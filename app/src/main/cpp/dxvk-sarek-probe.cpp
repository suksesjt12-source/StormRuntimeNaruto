#include <jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <sstream>
#include <string>
#include <vector>

namespace {
bool hasName(const std::vector<VkExtensionProperties>& extensions, const char* name) {
    for (const auto& extension : extensions) {
        if (std::string(extension.extensionName) == name) return true;
    }
    return false;
}

std::vector<VkExtensionProperties> instanceExtensions() {
    uint32_t count = 0;
    if (vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr) != VK_SUCCESS) return {};
    std::vector<VkExtensionProperties> values(count);
    if (count && vkEnumerateInstanceExtensionProperties(nullptr, &count, values.data()) != VK_SUCCESS) return {};
    values.resize(count);
    return values;
}
}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_dxvkSarekStatus(JNIEnv* env, jobject) {
    // Create a Vulkan 1.0 instance so older loaders can still report why the
    // selected DXVK-Sarek Vulkan 1.1 baseline is unavailable.
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "LauncherPC DXVK-Sarek Capability Probe";
    appInfo.applicationVersion = 1;
    appInfo.pEngineName = "LauncherPC";
    appInfo.engineVersion = 1;
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        std::ostringstream out;
        out << "BLOCKED: Vulkan instance creation failed (" << result << ").";
        return env->NewStringUTF(out.str().c_str());
    }

    const auto availableInstanceExtensions = instanceExtensions();
    const bool surfaceExt = hasName(availableInstanceExtensions, VK_KHR_SURFACE_EXTENSION_NAME);
    const bool androidSurfaceExt = hasName(availableInstanceExtensions, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME);

    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("BLOCKED: no Vulkan physical device was enumerated.");
    }

    std::vector<VkPhysicalDevice> devices(count);
    result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return env->NewStringUTF("BLOCKED: physical-device enumeration failed.");
    }

    std::ostringstream out;
    out << "DXVK-Sarek v1.11.0 baseline probe (Vulkan 1.1)";
    bool anyCandidate = false;

    for (uint32_t i = 0; i < count; ++i) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[i], &properties);

        uint32_t extensionCount = 0;
        VkResult extResult = vkEnumerateDeviceExtensionProperties(devices[i], nullptr, &extensionCount, nullptr);
        std::vector<VkExtensionProperties> deviceExtensions;
        if (extResult == VK_SUCCESS && extensionCount) {
            deviceExtensions.resize(extensionCount);
            extResult = vkEnumerateDeviceExtensionProperties(
                devices[i], nullptr, &extensionCount, deviceExtensions.data());
            if (extResult == VK_SUCCESS) deviceExtensions.resize(extensionCount);
            else deviceExtensions.clear();
        }

        uint32_t queueCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, nullptr);
        std::vector<VkQueueFamilyProperties> queues(queueCount);
        if (queueCount) vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, queues.data());
        bool graphicsQueue = false;
        for (const auto& queue : queues) {
            if (queue.queueCount > 0 && (queue.queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
                graphicsQueue = true;
                break;
            }
        }

        const uint32_t major = VK_VERSION_MAJOR(properties.apiVersion);
        const uint32_t minor = VK_VERSION_MINOR(properties.apiVersion);
        const bool vulkan11 = major > 1 || (major == 1 && minor >= 1);
        const bool swapchain = extResult == VK_SUCCESS && hasName(deviceExtensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        const bool candidate = vulkan11 && swapchain && graphicsQueue && surfaceExt && androidSurfaceExt;
        anyCandidate = anyCandidate || candidate;

        out << "\n\nGPU " << (i + 1) << ": " << properties.deviceName
            << "\nVulkan API: " << major << "." << minor << "." << VK_VERSION_PATCH(properties.apiVersion)
            << "\nVulkan 1.1 baseline: " << (vulkan11 ? "PASS" : "FAIL")
            << "\nVK_KHR_swapchain: " << (swapchain ? "present" : "missing")
            << "\nGraphics queue: " << (graphicsQueue ? "present" : "missing")
            << "\nVK_KHR_surface: " << (surfaceExt ? "present" : "missing")
            << "\nVK_KHR_android_surface: " << (androidSurfaceExt ? "present" : "missing")
            << "\nBaseline candidate: " << (candidate ? "PASS" : "BLOCKED");
    }

    vkDestroyInstance(instance, nullptr);
    out << "\n\nOverall: " << (anyCandidate
        ? "baseline candidate found; Android surface presentation must still pass the separate surface test."
        : "no device passed the baseline checks; keep DXVK-Sarek disabled and use the Mali Wrapper/WineD3D route.");
    out << "\nThis probe does not prove DXVK DLL loading, D3D9 compatibility, or game rendering.";
    return env->NewStringUTF(out.str().c_str());
}
