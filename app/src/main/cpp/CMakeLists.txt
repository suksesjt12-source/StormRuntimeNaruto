cmake_minimum_required(VERSION 3.22.1)
project(stormcore)

add_library(stormcore SHARED
    native-lib.cpp
    ../../../../runtime/host/runtime_host.cpp
    ../../../../runtime/common/runtime_log.cpp
)

target_include_directories(stormcore PRIVATE
    ../../../../runtime/host
    ../../../../runtime/common
)

find_library(ANDROID_LIB android)
find_library(LOG_LIB log)
find_library(VULKAN_LIB vulkan)
if(NOT ANDROID_LIB)
    message(FATAL_ERROR "Android native library not found in NDK toolchain")
endif()
if(NOT LOG_LIB)
    message(FATAL_ERROR "Android log library not found in NDK toolchain")
endif()
if(NOT VULKAN_LIB)
    message(FATAL_ERROR "Android Vulkan loader library not found in NDK toolchain")
endif()

target_link_libraries(stormcore PRIVATE ${ANDROID_LIB} ${LOG_LIB} ${VULKAN_LIB})
