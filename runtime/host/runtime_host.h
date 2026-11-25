#pragma once

namespace storm::runtime {

// Initializes the Android/native host only. It does not initialize Wine or
// claim that Windows executables can be launched.
bool runtime_initialize(const char* app_data_directory);
void runtime_shutdown();
const char* runtime_get_status();
const char* runtime_get_last_error();

}
