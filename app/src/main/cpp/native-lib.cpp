#include <jni.h>
#include <vulkan/vulkan.h>

#include <sstream>
#include <string>
#include <vector>

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_status(JNIEnv* env, jobject) {
    return env->NewStringUTF(
        "JNI native library loaded. Wine, x86-32 translation, D3D9 and game execution are not integrated."
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
        return env->NewStringUTF(
            "Vulkan loader is present, but no Vulkan physical device was enumerated."
        );
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
        if (queueCount > 0) {
            vkGetPhysicalDeviceQueueFamilyProperties(devices[i], &queueCount, queues.data());
        }

        bool graphicsQueue = false;
        for (const auto& queue : queues) {
            if (queue.queueCount > 0 &&
                (queue.queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
                graphicsQueue = true;
                break;
            }
        }

        uint32_t extensionCount = 0;
        VkResult extensionResult =
            vkEnumerateDeviceExtensionProperties(devices[i], nullptr, &extensionCount, nullptr);
        bool swapchain = false;
        if (extensionResult == VK_SUCCESS && extensionCount > 0) {
            std::vector<VkExtensionProperties> extensions(extensionCount);
            extensionResult = vkEnumerateDeviceExtensionProperties(
                devices[i], nullptr, &extensionCount, extensions.data());
            if (extensionResult == VK_SUCCESS) {
                for (const auto& extension : extensions) {
                    if (std::string(extension.extensionName) == "VK_KHR_swapchain") {
                        swapchain = true;
                        break;
                    }
                }
            }
        }

        out << "\n\nGPU " << (i + 1) << ": " << props.deviceName
            << "\nAPI: " << VK_VERSION_MAJOR(props.apiVersion) << "."
            << VK_VERSION_MINOR(props.apiVersion) << "."
            << VK_VERSION_PATCH(props.apiVersion)
            << "\nDriver version: " << props.driverVersion
            << "\nDevice type: " << static_cast<int>(props.deviceType)
            << "\nGraphics queue family: " << (graphicsQueue ? "available" : "not found")
            << "\nVK_KHR_swapchain: " << (swapchain ? "advertised" : "not advertised")
            << "\nGeometry shader: " << (features.geometryShader ? "yes" : "no")
            << "\nTessellation shader: " << (features.tessellationShader ? "yes" : "no")
            << "\nAnisotropic filtering: " << (features.samplerAnisotropy ? "yes" : "no");
    }

    vkDestroyInstance(instance, nullptr);
    out << "\n\nThis is capability discovery only. It does not prove DXVK, D3D9, "
           "Android surface presentation, or game compatibility.";
    return env->NewStringUTF(out.str().c_str());
}
