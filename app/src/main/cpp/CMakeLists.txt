cmake_minimum_required(VERSION 3.22.1)
project(stormcore)

add_library(stormcore SHARED
    native-lib.cpp
    dxvk-sarek-probe.cpp
)

find_library(ANDROID_LIB android)
find_library(VULKAN_LIB vulkan)
if(NOT ANDROID_LIB)
    message(FATAL_ERROR "Android native library not found in NDK toolchain")
endif()
if(NOT VULKAN_LIB)
    message(FATAL_ERROR "Android Vulkan loader library not found in NDK toolchain")
endif()

target_link_libraries(stormcore PRIVATE ${ANDROID_LIB} ${VULKAN_LIB})
