#include <jni.h>
#include <vulkan/vulkan.h>
#include <sstream>
#include <string>
#include <vector>

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_status(JNIEnv* env, jobject) {
    const std::string status =
        "Native library loaded. Wine, x86 translator, and Direct3D 9 runtime are not integrated.";
    return env->NewStringUTF(status.c_str());
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
        out << "Vulkan: instance creation failed (VkResult " << result << ").";
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
        return env->NewStringUTF("Vulkan: physical-device enumeration failed.");
    }

    std::ostringstream out;
    out << "Vulkan instance: OK; physical devices: " << count;
    for (uint32_t i = 0; i < count; ++i) {
        VkPhysicalDeviceProperties props{};
        vkGetPhysicalDeviceProperties(devices[i], &props);
        out << "\\nGPU " << (i + 1) << ": " << props.deviceName
            << " | API " << VK_VERSION_MAJOR(props.apiVersion) << "."
            << VK_VERSION_MINOR(props.apiVersion) << "."
            << VK_VERSION_PATCH(props.apiVersion);
    }

    vkDestroyInstance(instance, nullptr);
    out << "\\nNote: Vulkan availability alone does not prove DXVK or D3D9 compatibility.";
    return env->NewStringUTF(out.str().c_str());
}
