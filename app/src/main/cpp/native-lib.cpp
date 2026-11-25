#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_id_stormruntime_naruto_NativeBridge_status(JNIEnv* env, jobject) {
    const std::string status =
        "Native core aktif; Wine/CPU translator/DXVK belum terintegrasi.";
    return env->NewStringUTF(status.c_str());
}
