#include "../../runtime/host/runtime_host.h"
#include <filesystem>
#include <iostream>
#include <string>

int main() {
    const auto root = std::filesystem::temp_directory_path() / "storm-runtime-host-test";
    std::filesystem::remove_all(root);
    std::filesystem::create_directories(root);

    if (storm::runtime::runtime_initialize(nullptr)) {
        std::cerr << "Expected null app data path to fail.\n";
        return 1;
    }
    if (std::string(storm::runtime::runtime_get_last_error()).find("empty") == std::string::npos) {
        std::cerr << "Missing expected initialization error.\n";
        return 2;
    }

    const auto root_string = root.string();
    if (!storm::runtime::runtime_initialize(root_string.c_str())) {
        std::cerr << "Host initialization failed: " << storm::runtime::runtime_get_last_error() << "\n";
        return 3;
    }
    if (std::string(storm::runtime::runtime_get_status()).find("HOST READY") == std::string::npos) {
        std::cerr << "Host did not report READY.\n";
        return 4;
    }
    if (!std::filesystem::is_directory(root / "storm-runtime")) {
        std::cerr << "Runtime data directory was not created.\n";
        return 5;
    }

    storm::runtime::runtime_shutdown();
    if (std::string(storm::runtime::runtime_get_status()).find("STOPPED") == std::string::npos) {
        std::cerr << "Host did not report STOPPED.\n";
        return 6;
    }
    std::filesystem::remove_all(root);
    std::cout << "PASS: initialization errors, filesystem setup, worker lifecycle, and shutdown.\n";
    return 0;
}
