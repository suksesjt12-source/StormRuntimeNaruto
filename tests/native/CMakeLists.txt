cmake_minimum_required(VERSION 3.22)
project(storm_native_host_test LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_executable(storm_native_host_test
    host_test.cpp
    ../../runtime/host/runtime_host.cpp
    ../../runtime/common/runtime_log.cpp)
target_include_directories(storm_native_host_test PRIVATE
    ../../runtime/host
    ../../runtime/common)
find_package(Threads REQUIRED)
target_link_libraries(storm_native_host_test PRIVATE Threads::Threads)
enable_testing()
add_test(NAME storm_native_host_test COMMAND storm_native_host_test)
