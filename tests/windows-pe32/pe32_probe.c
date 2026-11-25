#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>

int main(void) {
    char path[MAX_PATH];
    char message[256];
    DWORD written = 0;

    DWORD temp_length = GetTempPathA((DWORD)sizeof(path), path);
    if (temp_length == 0 || temp_length >= sizeof(path)) {
        OutputDebugStringA("STORM_PE32_PROBE: GetTempPathA failed\n");
        return 20;
    }

    const char suffix[] = "storm-pe32-probe.log";
    size_t path_length = strlen(path);
    if (path_length + sizeof(suffix) > sizeof(path)) {
        OutputDebugStringA("STORM_PE32_PROBE: path too long\n");
        return 21;
    }
    memcpy(path + path_length, suffix, sizeof(suffix));

    HANDLE file = CreateFileA(path, GENERIC_WRITE, FILE_SHARE_READ, NULL,
                              CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        OutputDebugStringA("STORM_PE32_PROBE: CreateFileA failed\n");
        return 22;
    }

    int length = snprintf(message, sizeof(message),
                          "STORM_PE32_PROBE PASS\r\n"
                          "process_id=%lu\r\n"
                          "tick_count=%lu\r\n"
                          "expected_exit_code=37\r\n",
                          (unsigned long)GetCurrentProcessId(),
                          (unsigned long)GetTickCount());

    BOOL ok = length > 0 && (size_t)length < sizeof(message) &&
              WriteFile(file, message, (DWORD)length, &written, NULL) &&
              written == (DWORD)length;
    CloseHandle(file);

    if (!ok) {
        OutputDebugStringA("STORM_PE32_PROBE: WriteFile failed\n");
        return 23;
    }

    OutputDebugStringA("STORM_PE32_PROBE PASS: Win32 API calls completed\n");
    return 37;
}
