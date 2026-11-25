#include "runtime_host.h"
#include "../common/runtime_log.h"

#include <condition_variable>
#include <filesystem>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>

namespace storm::runtime {
namespace {
enum class HostState { Stopped, Starting, Ready, Stopping, Failed };
std::mutex g_mutex;
std::condition_variable g_cv;
std::thread g_worker;
HostState g_state = HostState::Stopped;
bool g_stop_requested = false;
std::string g_status = "NOT INITIALIZED";
std::string g_last_error;

void set_error_locked(const std::string& message) {
    g_last_error = message;
    g_status = "HOST FAILED: " + message;
    g_state = HostState::Failed;
}

void worker_main() {
    log_info("Native host worker started.");
    std::unique_lock<std::mutex> lock(g_mutex);
    g_cv.wait(lock, [] { return g_stop_requested; });
    lock.unlock();
    log_info("Native host worker stopped.");
}
} // namespace

bool runtime_initialize(const char* app_data_directory) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_state == HostState::Ready) return true;
    if (g_state == HostState::Starting || g_state == HostState::Stopping) {
        g_last_error = "Invalid concurrent host lifecycle transition.";
        return false;
    }
    if (app_data_directory == nullptr || app_data_directory[0] == '\0') {
        set_error_locked("Android app data directory is empty.");
        return false;
    }

    g_state = HostState::Starting;
    g_status = "INITIALIZING ANDROID NATIVE HOST";
    g_last_error.clear();
    try {
        const std::filesystem::path root(app_data_directory);
        const auto runtime_dir = root / "storm-runtime";
        std::filesystem::create_directories(runtime_dir);
        const auto probe = runtime_dir / "host-filesystem-probe.txt";
        {
            std::ofstream output(probe, std::ios::binary | std::ios::trunc);
            if (!output) throw std::runtime_error("Cannot create host filesystem probe.");
            output << "storm-runtime-host-ok\n";
        }
        {
            std::ifstream input(probe, std::ios::binary);
            std::string value;
            std::getline(input, value);
            if (!input || value != "storm-runtime-host-ok") {
                throw std::runtime_error("Host filesystem read-back validation failed.");
            }
        }
        std::filesystem::remove(probe);
        g_stop_requested = false;
        g_worker = std::thread(worker_main);
        g_state = HostState::Ready;
        g_status = "ANDROID NATIVE HOST READY (Wine/Hangover unavailable)";
        log_info("Android native host initialized; Wine/Hangover is not present.");
        return true;
    } catch (const std::exception& error) {
        set_error_locked(error.what());
        log_error(g_last_error.c_str());
        return false;
    }
}

void runtime_shutdown() {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_state == HostState::Stopped) return;
        g_state = HostState::Stopping;
        g_status = "STOPPING ANDROID NATIVE HOST";
        g_stop_requested = true;
    }
    g_cv.notify_all();
    if (g_worker.joinable()) g_worker.join();
    std::lock_guard<std::mutex> lock(g_mutex);
    g_state = HostState::Stopped;
    g_status = "ANDROID NATIVE HOST STOPPED (Wine/Hangover unavailable)";
    log_info("Android native host shutdown complete.");
}

const char* runtime_get_status() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_status.c_str();
}

const char* runtime_get_last_error() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_last_error.c_str();
}
}
