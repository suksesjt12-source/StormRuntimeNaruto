#include "runtime_log.h"

#ifdef __ANDROID__
#include <android/log.h>
#else
#include <cstdio>
#endif

namespace storm::runtime {
void log_info(const char* message) {
#ifdef __ANDROID__
    __android_log_write(ANDROID_LOG_INFO, "StormRuntime", message ? message : "(null)");
#else
    std::fprintf(stderr, "[StormRuntime][INFO] %s\n", message ? message : "(null)");
#endif
}
void log_error(const char* message) {
#ifdef __ANDROID__
    __android_log_write(ANDROID_LOG_ERROR, "StormRuntime", message ? message : "(null)");
#else
    std::fprintf(stderr, "[StormRuntime][ERROR] %s\n", message ? message : "(null)");
#endif
}
}
