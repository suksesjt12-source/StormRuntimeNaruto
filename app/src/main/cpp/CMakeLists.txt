cmake_minimum_required(VERSION 3.22.1)
project(stormcore)

add_library(stormcore SHARED native-lib.cpp)

find_library(android-lib android)
find_library(vulkan-lib vulkan)
if(NOT android-lib)
    message(FATAL_ERROR "Android native library not found in NDK toolchain")
endif()
if(NOT vulkan-lib)
    message(FATAL_ERROR "Android Vulkan loader library not found in NDK toolchain")
endif()

target_link_libraries(stormcore ${android-lib} ${vulkan-lib})
