// SPDX-License-Identifier: GPL-3.0-only
#include <android/log.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "abi.h"
#include "input_abi.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "VrApiVrxSmoke", __VA_ARGS__)
#define ERR(...) __android_log_print(ANDROID_LOG_ERROR, "VrApiVrxSmoke", __VA_ARGS__)

extern "C" {
int32_t vrapi_Initialize(const ovrInitParms* parms);
void vrapi_Shutdown();
int32_t vrapi_GetInstanceExtensionsVulkan(char* names, uint32_t* size);
int32_t vrapi_GetDeviceExtensionsVulkan(char* names, uint32_t* size);
int32_t vrapi_CreateSystemVulkan(ovrSystemCreateInfoVulkan* info);
void vrapi_DestroySystemVulkan();
ovrMobile* vrapi_EnterVrMode(const ovrModeParms* parms);
void vrapi_LeaveVrMode(ovrMobile* mobile);
double vrapi_GetPredictedDisplayTime(ovrMobile* mobile, int64_t index);
ovrTracking2 vrapi_GetPredictedTracking2(ovrMobile* mobile, double time);
int32_t vrapi_SubmitFrame2(ovrMobile* mobile,
                           const ovrSubmitFrameDescription2* description);
int32_t vrapi_EnumerateInputDevices(ovrMobile* mobile, uint32_t index,
                                    ovrInputCapabilityHeader* header);
int32_t vrapi_GetInputDeviceCapabilities(ovrMobile* mobile,
                                         ovrInputCapabilityHeader* header);
int32_t vrapi_GetCurrentInputState(ovrMobile* mobile, uint32_t deviceId,
                                   ovrInputStateHeader* state);
int32_t vrapi_GetInputTrackingState(ovrMobile* mobile, uint32_t deviceId,
                                    double time, ovrTracking* tracking);
}

namespace {
struct Vulkan {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;

    void destroy() {
        if (device)
            vkDestroyDevice(device, nullptr);
        if (instance)
            vkDestroyInstance(instance, nullptr);
        device = VK_NULL_HANDLE;
        instance = VK_NULL_HANDLE;
    }
};

std::vector<std::string> splitExtensions(const char* text) {
    std::vector<std::string> result;
    std::istringstream input(text ? text : "");
    std::string item;
    while (input >> item)
        result.push_back(item);
    return result;
}

bool queryExtensions(int32_t (*query)(char*, uint32_t*),
                     std::vector<std::string>& extensions) {
    uint32_t size = 0;
    int32_t result = query(nullptr, &size);
    if (result != 0 || size == 0) {
        ERR("VrApi extension query failed: result=%d size=%u", result, size);
        return false;
    }
    std::vector<char> buffer(size + 1);
    result = query(buffer.data(), &size);
    if (result != 0) {
        ERR("VrApi extension fetch failed: result=%d", result);
        return false;
    }
    extensions = splitExtensions(buffer.data());
    return true;
}

bool createVulkan(Vulkan& vk) {
    std::vector<std::string> instanceExtensionNames;
    if (!queryExtensions(vrapi_GetInstanceExtensionsVulkan, instanceExtensionNames))
        return false;
    std::vector<const char*> instanceExtensions;
    for (const auto& extension : instanceExtensionNames)
        instanceExtensions.push_back(extension.c_str());

    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "OVRPort VrApi VRX Smoke";
    app.applicationVersion = 1;
    app.pEngineName = "OVRPort smoke";
    app.engineVersion = 1;
    app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo instanceCreate{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    instanceCreate.pApplicationInfo = &app;
    instanceCreate.enabledExtensionCount =
        static_cast<uint32_t>(instanceExtensions.size());
    instanceCreate.ppEnabledExtensionNames = instanceExtensions.data();
    if (vkCreateInstance(&instanceCreate, nullptr, &vk.instance) != VK_SUCCESS) {
        ERR("vkCreateInstance failed");
        return false;
    }

    uint32_t count = 0;
    vkEnumeratePhysicalDevices(vk.instance, &count, nullptr);
    if (!count) {
        ERR("No Vulkan physical device");
        return false;
    }
    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(vk.instance, &count, devices.data());
    vk.physical = devices.front();

    uint32_t queueCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(vk.physical, &queueCount, nullptr);
    std::vector<VkQueueFamilyProperties> families(queueCount);
    vkGetPhysicalDeviceQueueFamilyProperties(vk.physical, &queueCount, families.data());
    uint32_t graphicsIndex = UINT32_MAX;
    for (uint32_t i = 0; i < queueCount; ++i) {
        if (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
            graphicsIndex = i;
            break;
        }
    }
    if (graphicsIndex == UINT32_MAX) {
        ERR("No Vulkan graphics queue family");
        return false;
    }

    std::vector<std::string> deviceExtensionNames;
    if (!queryExtensions(vrapi_GetDeviceExtensionsVulkan, deviceExtensionNames))
        return false;
    std::vector<const char*> deviceExtensions;
    for (const auto& extension : deviceExtensionNames)
        deviceExtensions.push_back(extension.c_str());
    const float priority = 1.0f;
    VkDeviceQueueCreateInfo queueCreate{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queueCreate.queueFamilyIndex = graphicsIndex;
    queueCreate.queueCount = 1;
    queueCreate.pQueuePriorities = &priority;
    VkDeviceCreateInfo deviceCreate{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    deviceCreate.queueCreateInfoCount = 1;
    deviceCreate.pQueueCreateInfos = &queueCreate;
    deviceCreate.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    deviceCreate.ppEnabledExtensionNames = deviceExtensions.data();
    if (vkCreateDevice(vk.physical, &deviceCreate, nullptr, &vk.device) != VK_SUCCESS) {
        ERR("vkCreateDevice failed");
        return false;
    }
    vkGetDeviceQueue(vk.device, graphicsIndex, 0, &vk.queue);
    return vk.queue != VK_NULL_HANDLE;
}
}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_moe_crx_overport_vrapismoke_MainActivity_runSmoke(JNIEnv* env, jobject activity) {
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK || !vm) {
        ERR("JavaVM unavailable");
        return 10;
    }
    ovrInitParms init{};
    init.Type = 1;
    init.ProductVersion = 1;
    init.MajorVersion = 1;
    init.GraphicsAPI = 0x40100;
    init.Java = {vm, env, activity};
    int32_t result = vrapi_Initialize(&init);
    LOG("vrapi_Initialize=%d", result);
    if (result != 0)
        return 11;

    Vulkan vk;
    if (!createVulkan(vk)) {
        vrapi_Shutdown();
        vk.destroy();
        return 12;
    }
    ovrSystemCreateInfoVulkan system{vk.instance, vk.physical, vk.device};
    result = vrapi_CreateSystemVulkan(&system);
    LOG("vrapi_CreateSystemVulkan=%d", result);
    if (result != 0) {
        vrapi_Shutdown();
        vk.destroy();
        return 13;
    }

    ovrModeParmsVulkan mode{};
    mode.ModeParms.Type = 1;
    mode.ModeParms.Java = {vm, env, activity};
    mode.SynchronizationQueue = reinterpret_cast<uint64_t>(vk.queue);
    ovrMobile* mobile = vrapi_EnterVrMode(reinterpret_cast<const ovrModeParms*>(&mode));
    LOG("vrapi_EnterVrMode=%p", mobile);
    if (!mobile) {
        vrapi_DestroySystemVulkan();
        vrapi_Shutdown();
        vk.destroy();
        return 14;
    }

    double displayTime = vrapi_GetPredictedDisplayTime(mobile, 0);
    ovrTracking2 head = vrapi_GetPredictedTracking2(mobile, displayTime);
    LOG("HEAD status=0x%x pos=(%.3f, %.3f, %.3f) quat=(%.3f, %.3f, %.3f, %.3f)",
        head.Status, head.HeadPose.Pose.Position.x, head.HeadPose.Pose.Position.y,
        head.HeadPose.Pose.Position.z, head.HeadPose.Pose.Orientation.x,
        head.HeadPose.Pose.Orientation.y, head.HeadPose.Pose.Orientation.z,
        head.HeadPose.Pose.Orientation.w);

    ovrSubmitFrameDescription2 frame{};
    frame.SwapInterval = 1;
    for (uint64_t index = 0; index < 300; ++index) {
        frame.FrameIndex = index;
        frame.DisplayTime = vrapi_GetPredictedDisplayTime(mobile, index);
        result = vrapi_SubmitFrame2(mobile, &frame);
        if (index < 8 || index % 60 == 0)
            LOG("EMPTY_FRAME index=%llu result=%d", (unsigned long long)index, result);
        std::this_thread::sleep_for(std::chrono::milliseconds(30));
    }

    int controllers = 0;
    for (uint32_t index = 0; index < 8; ++index) {
        ovrInputCapabilityHeader cap{};
        result = vrapi_EnumerateInputDevices(mobile, index, &cap);
        if (result != 0)
            break;
        ovrInputTrackedRemoteCapabilities remote{};
        remote.Header = cap;
        result = vrapi_GetInputDeviceCapabilities(mobile, &remote.Header);
        if (result != 0)
            continue;
        ovrTracking tracking{};
        result = vrapi_GetInputTrackingState(mobile, remote.Header.DeviceID,
                                             displayTime, &tracking);
        LOG("CONTROLLER index=%u id=%u caps=0x%x track=%d status=0x%x "
            "pos=(%.3f, %.3f, %.3f)",
            index, remote.Header.DeviceID, remote.ControllerCapabilities, result,
            tracking.Status, tracking.HeadPose.Pose.Position.x,
            tracking.HeadPose.Pose.Position.y, tracking.HeadPose.Pose.Position.z);
        ++controllers;
    }
    LOG("CONTROLLER_COUNT=%d", controllers);

    vrapi_LeaveVrMode(mobile);
    vrapi_DestroySystemVulkan();
    vrapi_Shutdown();
    vk.destroy();
    LOG("SMOKE_PASS controllers=%d", controllers);
    return 0;
}
