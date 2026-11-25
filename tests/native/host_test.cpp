#include "../../runtime/host/runtime_host.h"
#include <cassert>
#include <filesystem>
#include <string>

int main() {
    const auto root = std::filesystem::temp_directory_path() / "storm-runtime-host-test";
    std::filesystem::remove_all(root);
    std::filesystem::create_directories(root);

    assert(!storm::runtime::runtime_initialize(nullptr));
    assert(std::string(storm::runtime::runtime_get_last_error()).find("empty") != std::string::npos);

    const auto root_string = root.string();
    assert(storm::runtime::runtime_initialize(root_string.c_str()));
    assert(std::string(storm::runtime::runtime_get_status()).find("HOST READY") != std::string::npos);
    assert(std::filesystem::is_directory(root / "storm-runtime"));

    storm::runtime::runtime_shutdown();
    assert(std::string(storm::runtime::runtime_get_status()).find("STOPPED") != std::string::npos);
    std::filesystem::remove_all(root);
    return 0;
}
