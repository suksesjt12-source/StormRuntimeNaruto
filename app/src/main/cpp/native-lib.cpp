#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <mutex>
#include <algorithm>
#include <cstdint>
#include <sstream>
#include <string>
#include <vector>

namespace {
std::mutex g_surfaceMutex;
ANativeWindow* g_window = nullptr;

bool hasInstanceExtension(const std::vector<VkExtensionProperties>& extensions,
                          const char* name) {
    for (const auto& extension : extensions) {
        if (std::string(extension.extensionName) == name) return true;
    }
    return false;
}


bool runSwapchainPresentProbe(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface,
                              ANativeWindow* window, std::string& detail) {
    uint32_t familyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &familyCount, nullptr);
    std::vector<VkQueueFamilyProperties> families(familyCount);
    if (familyCount) vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &familyCount, families.data());
    uint32_t queueFamily = UINT32_MAX;
    for (uint32_t i = 0; i < familyCount; ++i) {
        if (!families[i].queueCount || !(families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) continue;
        VkBool32 present = VK_FALSE;
        if (vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, i, surface, &present) == VK_SUCCESS && present) {
            queueFamily = i;
            break;
        }
    }
    if (queueFamily == UINT32_MAX) { detail = "No unified graphics/present queue family."; return false; }

    uint32_t extensionCount = 0;
    if (vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extensionCount, nullptr) != VK_SUCCESS) {
        detail = "Device-extension enumeration failed."; return false;
    }
    std::vector<VkExtensionProperties> extensions(extensionCount);
    if (vkEnumerateDeviceExtensionProperties(physicalDevice, nullptr, &extensionCount, extensions.data()) != VK_SUCCESS ||
        !hasInstanceExtension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
        detail = "VK_KHR_swapchain unavailable."; return false;
    }

    VkSurfaceCapabilitiesKHR caps{};
    if (vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, &caps) != VK_SUCCESS ||
        !(caps.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)) {
        detail = "Surface color-attachment usage unsupported."; return false;
    }
    uint32_t formatCount = 0;
    if (vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, nullptr) != VK_SUCCESS || !formatCount) {
        detail = "No surface formats."; return false;
    }
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    if (vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, &formatCount, formats.data()) != VK_SUCCESS) {
        detail = "Surface-format query failed."; return false;
    }
    VkSurfaceFormatKHR chosen = formats.front();
    if (formatCount == 1 && chosen.format == VK_FORMAT_UNDEFINED) {
        chosen.format = VK_FORMAT_B8G8R8A8_UNORM;
        chosen.colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    }

    VkExtent2D extent = caps.currentExtent;
    if (extent.width == UINT32_MAX) {
        const uint32_t width = static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(window)));
        const uint32_t height = static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(window)));
        extent.width = std::clamp(width, caps.minImageExtent.width, caps.maxImageExtent.width);
        extent.height = std::clamp(height, caps.minImageExtent.height, caps.maxImageExtent.height);
    }
    if (!extent.width || !extent.height) { detail = "Surface extent is zero; wait for a non-zero surface."; return false; }

    uint32_t imageCount = caps.minImageCount + 1;
    if (caps.maxImageCount && imageCount > caps.maxImageCount) imageCount = caps.maxImageCount;

    const float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;
    const char* deviceExtensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1;
    deviceInfo.ppEnabledExtensionNames = deviceExtensions;

    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkSemaphore acquiredSemaphore = VK_NULL_HANDLE;
    VkSemaphore renderCompleteSemaphore = VK_NULL_HANDLE;
    VkFence renderFence = VK_NULL_HANDLE;
    std::vector<VkImageView> imageViews;
    std::vector<VkFramebuffer> framebuffers;
    std::vector<VkCommandBuffer> commandBuffers;

    auto cleanup = [&]() {
        if (device) vkDeviceWaitIdle(device);
        for (VkFramebuffer framebuffer : framebuffers) vkDestroyFramebuffer(device, framebuffer, nullptr);
        for (VkImageView view : imageViews) vkDestroyImageView(device, view, nullptr);
        if (!commandBuffers.empty() && commandPool) {
            vkFreeCommandBuffers(device, commandPool, static_cast<uint32_t>(commandBuffers.size()), commandBuffers.data());
        }
        if (commandPool) vkDestroyCommandPool(device, commandPool, nullptr);
        if (renderPass) vkDestroyRenderPass(device, renderPass, nullptr);
        if (renderFence) vkDestroyFence(device, renderFence, nullptr);
        if (renderCompleteSemaphore) vkDestroySemaphore(device, renderCompleteSemaphore, nullptr);
        if (acquiredSemaphore) vkDestroySemaphore(device, acquiredSemaphore, nullptr);
        if (swapchain) {
            auto destroySwapchain = reinterpret_cast<PFN_vkDestroySwapchainKHR>(vkGetDeviceProcAddr(device, "vkDestroySwapchainKHR"));
            if (destroySwapchain) destroySwapchain(device, swapchain, nullptr);
        }
        if (device) vkDestroyDevice(device, nullptr);
    };

    VkResult result = vkCreateDevice(physicalDevice, &deviceInfo, nullptr, &device);
    if (result != VK_SUCCESS) { detail = "vkCreateDevice failed: " + std::to_string(result); return false; }
    auto createSwapchain = reinterpret_cast<PFN_vkCreateSwapchainKHR>(vkGetDeviceProcAddr(device, "vkCreateSwapchainKHR"));
    auto acquireImage = reinterpret_cast<PFN_vkAcquireNextImageKHR>(vkGetDeviceProcAddr(device, "vkAcquireNextImageKHR"));
    auto presentQueue = reinterpret_cast<PFN_vkQueuePresentKHR>(vkGetDeviceProcAddr(device, "vkQueuePresentKHR"));
    if (!createSwapchain || !acquireImage || !presentQueue) {
        detail = "Swapchain entry points unavailable."; cleanup(); return false;
    }

    const VkCompositeAlphaFlagBitsKHR alphaOptions[] = {
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR, VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
        VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR, VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
    };
    VkCompositeAlphaFlagBitsKHR alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    bool alphaFound = false;
    for (auto option : alphaOptions) if (caps.supportedCompositeAlpha & option) { alpha = option; alphaFound = true; break; }
    if (!alphaFound) { detail = "No supported composite-alpha mode."; cleanup(); return false; }

    VkSwapchainCreateInfoKHR swapInfo{};
    swapInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapInfo.surface = surface;
    swapInfo.minImageCount = imageCount;
    swapInfo.imageFormat = chosen.format;
    swapInfo.imageColorSpace = chosen.colorSpace;
    swapInfo.imageExtent = extent;
    swapInfo.imageArrayLayers = 1;
    swapInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swapInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapInfo.preTransform = caps.currentTransform;
    swapInfo.compositeAlpha = alpha;
    swapInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    swapInfo.clipped = VK_TRUE;
    result = createSwapchain(device, &swapInfo, nullptr, &swapchain);
    if (result != VK_SUCCESS) { detail = "vkCreateSwapchainKHR failed: " + std::to_string(result); cleanup(); return false; }

    auto getImages = reinterpret_cast<PFN_vkGetSwapchainImagesKHR>(vkGetDeviceProcAddr(device, "vkGetSwapchainImagesKHR"));
    if (!getImages) { detail = "vkGetSwapchainImagesKHR unavailable."; cleanup(); return false; }
    uint32_t swapImageCount = 0;
    result = getImages(device, swapchain, &swapImageCount, nullptr);
    if (result != VK_SUCCESS || !swapImageCount) { detail = "Swapchain image enumeration failed."; cleanup(); return false; }
    std::vector<VkImage> images(swapImageCount);
    result = getImages(device, swapchain, &swapImageCount, images.data());
    if (result != VK_SUCCESS) { detail = "Swapchain image retrieval failed."; cleanup(); return false; }

    VkAttachmentDescription attachment{};
    attachment.format = chosen.format;
    attachment.samples = VK_SAMPLE_COUNT_1_BIT;
    attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    VkAttachmentReference colorReference{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;
    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &attachment;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;
    result = vkCreateRenderPass(device, &renderPassInfo, nullptr, &renderPass);
    if (result != VK_SUCCESS) { detail = "vkCreateRenderPass failed: " + std::to_string(result); cleanup(); return false; }

    imageViews.resize(swapImageCount, VK_NULL_HANDLE);
    framebuffers.resize(swapImageCount, VK_NULL_HANDLE);
    for (uint32_t i = 0; i < swapImageCount; ++i) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = images[i];
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = chosen.format;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;
        result = vkCreateImageView(device, &viewInfo, nullptr, &imageViews[i]);
        if (result != VK_SUCCESS) { detail = "vkCreateImageView failed: " + std::to_string(result); cleanup(); return false; }

        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = renderPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = &imageViews[i];
        framebufferInfo.width = extent.width;
        framebufferInfo.height = extent.height;
        framebufferInfo.layers = 1;
        result = vkCreateFramebuffer(device, &framebufferInfo, nullptr, &framebuffers[i]);
        if (result != VK_SUCCESS) { detail = "vkCreateFramebuffer failed: " + std::to_string(result); cleanup(); return false; }
    }

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = queueFamily;
    result = vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool);
    if (result != VK_SUCCESS) { detail = "vkCreateCommandPool failed: " + std::to_string(result); cleanup(); return false; }

    commandBuffers.resize(swapImageCount, VK_NULL_HANDLE);
    VkCommandBufferAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocateInfo.commandPool = commandPool;
    allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocateInfo.commandBufferCount = swapImageCount;
    result = vkAllocateCommandBuffers(device, &allocateInfo, commandBuffers.data());
    if (result != VK_SUCCESS) { detail = "vkAllocateCommandBuffers failed: " + std::to_string(result); cleanup(); return false; }

    for (uint32_t i = 0; i < swapImageCount; ++i) {
        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        result = vkBeginCommandBuffer(commandBuffers[i], &beginInfo);
        if (result != VK_SUCCESS) { detail = "vkBeginCommandBuffer failed: " + std::to_string(result); cleanup(); return false; }
        VkClearValue clearValue{};
        clearValue.color.float32[0] = 0.035f;
        clearValue.color.float32[1] = 0.075f;
        clearValue.color.float32[2] = 0.16f;
        clearValue.color.float32[3] = 1.0f;
        VkRenderPassBeginInfo beginRender{};
        beginRender.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        beginRender.renderPass = renderPass;
        beginRender.framebuffer = framebuffers[i];
        beginRender.renderArea.extent = extent;
        beginRender.clearValueCount = 1;
        beginRender.pClearValues = &clearValue;
        vkCmdBeginRenderPass(commandBuffers[i], &beginRender, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdEndRenderPass(commandBuffers[i]);
        result = vkEndCommandBuffer(commandBuffers[i]);
        if (result != VK_SUCCESS) { detail = "vkEndCommandBuffer failed: " + std::to_string(result); cleanup(); return false; }
    }

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    result = vkCreateSemaphore(device, &semaphoreInfo, nullptr, &acquiredSemaphore);
    if (result == VK_SUCCESS) result = vkCreateSemaphore(device, &semaphoreInfo, nullptr, &renderCompleteSemaphore);
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    if (result == VK_SUCCESS) result = vkCreateFence(device, &fenceInfo, nullptr, &renderFence);
    if (result != VK_SUCCESS) { detail = "Synchronization object creation failed: " + std::to_string(result); cleanup(); return false; }

    uint32_t imageIndex = 0;
    result = acquireImage(device, swapchain, 5000000000ULL, acquiredSemaphore, VK_NULL_HANDLE, &imageIndex);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        detail = "vkAcquireNextImageKHR failed: " + std::to_string(result);
        cleanup();
        return false;
    }

    VkQueue queue = VK_NULL_HANDLE;
    vkGetDeviceQueue(device, queueFamily, 0, &queue);
    const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submit{};
    submit.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit.waitSemaphoreCount = 1;
    submit.pWaitSemaphores = &acquiredSemaphore;
    submit.pWaitDstStageMask = &waitStage;
    submit.commandBufferCount = 1;
    submit.pCommandBuffers = &commandBuffers[imageIndex];
    submit.signalSemaphoreCount = 1;
    submit.pSignalSemaphores = &renderCompleteSemaphore;
    result = vkQueueSubmit(queue, 1, &submit, renderFence);
    if (result != VK_SUCCESS) { detail = "vkQueueSubmit failed: " + std::to_string(result); cleanup(); return false; }

    VkPresentInfoKHR present{};
    present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present.waitSemaphoreCount = 1;
    present.pWaitSemaphores = &renderCompleteSemaphore;
    present.swapchainCount = 1;
    present.pSwapchains = &swapchain;
    present.pImageIndices = &imageIndex;
    VkResult presentResult = presentQueue(queue, &present);
    VkResult fenceResult = vkWaitForFences(device, 1, &renderFence, VK_TRUE, 5000000000ULL);
    vkQueueWaitIdle(queue);
    const bool presented = (presentResult == VK_SUCCESS || presentResult == VK_SUBOPTIMAL_KHR) && fenceResult == VK_SUCCESS;
    if (!presented) {
        detail = "Present/fence failed: present=" + std::to_string(presentResult) +
                 ", fence=" + std::to_string(fenceResult);
    } else {
        detail = "PASS: one solid-color frame rendered by a Vulkan render pass and presented (" +
                 std::to_string(extent.width) + "x" + std::to_string(extent.height) + ").";
    }
    cleanup();
    return presented;
}

std::string probeAndroidSurface(ANativeWindow* window) {
    if (window == nullptr) return "Android surface: NOT ATTACHED";

    uint32_t extensionCount = 0;
    VkResult result = vkEnumerateInstanceExtensionProperties(nullptr, &extensionCount, nullptr);
    if (result != VK_SUCCESS) return "Vulkan instance-extension enumeration failed.";

    std::vector<VkExtensionProperties> available(extensionCount);
    result = vkEnumerateInstanceExtensionProperties(nullptr, &extensionCount, available.data());
    if (result != VK_SUCCESS) return "Vulkan instance-extension enumeration failed.";

    if (!hasInstanceExtension(available, VK_KHR_SURFACE_EXTENSION_NAME) ||
        !hasInstanceExtension(available, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME)) {
        return "Android Vulkan surface extensions are unavailable; no VkSurfaceKHR was created.";
    }

    const char* enabledExtensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Storm Runtime Android Surface Probe";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "StormRuntime";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &appInfo;
    instanceInfo.enabledExtensionCount = 2;
    instanceInfo.ppEnabledExtensionNames = enabledExtensions;

    VkInstance instance = VK_NULL_HANDLE;
    result = vkCreateInstance(&instanceInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        std::ostringstream out;
        out << "Vulkan instance creation for Android surface failed: " << result;
        return out.str();
    }

    auto createSurface = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(
        vkGetInstanceProcAddr(instance, "vkCreateAndroidSurfaceKHR"));
    auto destroySurface = reinterpret_cast<PFN_vkDestroySurfaceKHR>(
        vkGetInstanceProcAddr(instance, "vkDestroySurfaceKHR"));
    auto getSurfaceSupport = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(
        vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceSurfaceSupportKHR"));
    auto getSurfaceCapabilities = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR>(
        vkGetInstanceProcAddr(instance, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR"));

    if (!createSurface || !destroySurface || !getSurfaceSupport || !getSurfaceCapabilities) {
        vkDestroyInstance(instance, nullptr);
        return "Required Vulkan Android-surface entry points are unavailable.";
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = window;

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    result = createSurface(instance, &surfaceInfo, nullptr, &surface);
    if (result != VK_SUCCESS) {
        std::ostringstream out;
        out << "vkCreateAndroidSurfaceKHR failed: " << result;
        vkDestroyInstance(instance, nullptr);
        return out.str();
    }

    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS || deviceCount == 0) {
        destroySurface(instance, surface, nullptr);
        vkDestroyInstance(instance, nullptr);
        return "VkSurfaceKHR created, but no Vulkan physical device is available.";
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
    if (result != VK_SUCCESS) {
        destroySurface(instance, surface, nullptr);
        vkDestroyInstance(instance, nullptr);
        return "Physical-device enumeration failed after surface creation.";
    }

    std::ostringstream out;
    out << "VkSurfaceKHR: CREATED; Android window: "
        << ANativeWindow_getWidth(window) << "x" << ANativeWindow_getHeight(window);

    bool presentQueueFound = false;
    VkPhysicalDevice presentDevice = VK_NULL_HANDLE;
    for (uint32_t d = 0; d < deviceCount; ++d) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[d], &properties);

        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[d], &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        if (familyCount) {
            vkGetPhysicalDeviceQueueFamilyProperties(devices[d], &familyCount, families.data());
        }

        for (uint32_t q = 0; q < familyCount; ++q) {
            if (families[q].queueCount == 0 ||
                (families[q].queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) continue;
            VkBool32 supported = VK_FALSE;
            result = getSurfaceSupport(devices[d], q, surface, &supported);
            if (result == VK_SUCCESS && supported) {
                VkSurfaceCapabilitiesKHR capabilities{};
                VkResult capResult = getSurfaceCapabilities(devices[d], surface, &capabilities);
                out << "\nPresent queue: " << properties.deviceName
                    << " (family " << q << ")";
                if (capResult == VK_SUCCESS) {
                    out << "\nSurface image count: " << capabilities.minImageCount
                        << "-" << capabilities.maxImageCount
                        << "\nSupported image usage flags: " << capabilities.supportedUsageFlags;
                } else {
                    out << "\nSurface capabilities query failed: " << capResult;
                }
                presentQueueFound = true;
                presentDevice = devices[d];
                break;
            }
        }
    }

    out << "\nPresentation support: " << (presentQueueFound ? "PASS" : "NO GRAPHICS PRESENT QUEUE");
    if (presentQueueFound && presentDevice != VK_NULL_HANDLE) {
        std::string swapchainDetail;
        bool swapchainPass = runSwapchainPresentProbe(presentDevice, surface, window, swapchainDetail);
        out << "\nSwapchain acquire/present: " << (swapchainPass ? "PASS" : "FAIL") << " — " << swapchainDetail;
    } else out << "\nSwapchain acquire/present: BLOCKED — no graphics/present queue.";
    destroySurface(instance, surface, nullptr);
    vkDestroyInstance(instance, nullptr);
    out << "\nNote: renderer test clears and presents one solid-color frame; no game rendering."
    return out.str();
}
}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_status(JNIEnv* env, jobject) {
    return env->NewStringUTF(
        "JNI native library loaded. Wine/Hangover and Windows PE execution are not integrated."
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_vulkanStatus(JNIEnv* env, jobject) {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Storm Runtime Naruto Diagnostics";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "StormRuntime";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        std::ostringstream out;
        out << "Vulkan instance creation failed (VkResult " << result << ").";
        return env->NewStringUTF(out.str().c_str());
    }

    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("Vulkan loader is present, but no Vulkan physical device was enumerated.");
    }

    std::vector<VkPhysicalDevice> devices(count);
    result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("Vulkan physical-device enumeration failed.");
    }

    std::ostringstream out;
    out << "Vulkan instance: PASS; physical devices: " << count;
    for (uint32_t i = 0; i < count; ++i) {
        VkPhysicalDeviceProperties props{};
        VkPhysicalDeviceFeatures features{};
        vkGetPhysicalDeviceProperties(devices[i], &props);
        vkGetPhysicalDeviceFeatures(devices[i], &features);
        uint32_t queueCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, nullptr);
        std::vector<VkQueueFamilyProperties> queues(queueCount);
        if (queueCount) vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, queues.data());

        bool graphicsQueue = false;
        for (const auto& queue : queues) {
            if (queue.queueCount && (queue.queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
                graphicsQueue = true;
                break;
            }
        }

        out << "\n\nGPU " << (i + 1) << ": " << props.deviceName
            << "\nAPI: " << VK_VERSION_MAJOR(props.apiVersion) << "."
            << VK_VERSION_MINOR(props.apiVersion) << "." << VK_VERSION_PATCH(props.apiVersion)
            << "\nGraphics queue family: " << (graphicsQueue ? "available" : "not found")
            << "\nAnisotropic filtering: " << (features.samplerAnisotropy ? "yes" : "no");
    }
    vkDestroyInstance(instance, nullptr);
    out << "\n\nDevice discovery only; this does not prove DXVK or D3D9 compatibility.";
    return env->NewStringUTF(out.str().c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_setAndroidSurface(JNIEnv* env, jobject, jobject surface) {
    ANativeWindow* next = ANativeWindow_fromSurface(env, surface);
    if (!next) return env->NewStringUTF("ANativeWindow_fromSurface failed.");

    // Keep a temporary reference before publishing the window. A concurrent
    // surfaceDestroyed callback may clear the global reference immediately.
    ANativeWindow_acquire(next);
    ANativeWindow* previous = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_surfaceMutex);
        previous = g_window;
        g_window = next;  // takes ownership of ANativeWindow_fromSurface reference
    }
    if (previous) ANativeWindow_release(previous);

    std::string status = probeAndroidSurface(next);
    ANativeWindow_release(next);  // release temporary probe reference
    return env->NewStringUTF(status.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_androidSurfaceStatus(JNIEnv* env, jobject) {
    ANativeWindow* window = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_surfaceMutex);
        window = g_window;
        if (window) ANativeWindow_acquire(window);
    }
    if (!window) return env->NewStringUTF("Android surface: NOT ATTACHED");
    std::string status = probeAndroidSurface(window);
    ANativeWindow_release(window);
    return env->NewStringUTF(status.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_id_stormruntime_naruto_NativeBridge_clearAndroidSurface(JNIEnv*, jobject) {
    ANativeWindow* previous = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_surfaceMutex);
        previous = g_window;
        g_window = nullptr;
    }
    if (previous) ANativeWindow_release(previous);
}
