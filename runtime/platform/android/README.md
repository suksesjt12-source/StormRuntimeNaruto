# Android Platform Host

The Android-specific layer owns JNI entry points, Activity/application lifecycle coordination, ANativeWindow references, Android logging, and SAF integration.

The Kotlin Activity passes its app-private files directory to the native host. SAF tree/document URIs remain ContentResolver resources; they must not be passed to native code as ordinary paths. Future Wine process integration must choose an explicit file-descriptor, content-provider, or staged-copy bridge.

The current JNI library is still built from `app/src/main/cpp/native-lib.cpp`; this directory records the platform boundary without a risky wholesale source move.
