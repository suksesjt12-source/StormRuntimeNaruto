#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <mutex>
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
                break;
            }
        }
    }

    destroySurface(instance, surface, nullptr);
    vkDestroyInstance(instance, nullptr);

    out << "\nPresentation support: " << (presentQueueFound ? "PASS" : "NO GRAPHICS PRESENT QUEUE");
    out << "\nSwapchain: NOT CREATED (this probe validates VkSurfaceKHR/present support only).";
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

    ANativeWindow* previous = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_surfaceMutex);
        previous = g_window;
        g_window = next;
    }
    if (previous) ANativeWindow_release(previous);

    std::string status = probeAndroidSurface(next);
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
