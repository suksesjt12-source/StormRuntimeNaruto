cmake_minimum_required(VERSION 3.22.1)
project(stormcore)

add_library(stormcore SHARED native-lib.cpp)

find_library(android-vulkan-lib vulkan)
if(NOT android-vulkan-lib)
    message(FATAL_ERROR "Android Vulkan loader library not found in NDK toolchain")
endif()

target_link_libraries(stormcore ${android-vulkan-lib})
