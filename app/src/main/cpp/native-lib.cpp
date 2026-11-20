#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>

namespace {
    const uint8_t MASK[4] = {0x5A, 0x33, 0x91, 0xC7};

    std::string decodePayload(const uint8_t* data, size_t len) {
        std::string result;
        result.reserve(len);
        for (size_t i = 0; i < len; ++i) {
            result.push_back(static_cast<char>(data[i] ^ MASK[i % 4]));
        }
        return result;
    }

    // Encrypted: ca-app-pub-2223803405403727/7474403554
    const uint8_t ENC_BANNER_AD_ID[] = {
        0x39, 0x52, 0xBC, 0xA6, 0x2A, 0x43, 0xBC, 0xB7,
        0x2F, 0x51, 0xBC, 0xF5, 0x68, 0x01, 0xA2, 0xFF,
        0x6A, 0x00, 0xA5, 0xF7, 0x6F, 0x07, 0xA1, 0xF4,
        0x6D, 0x01, 0xA6, 0xE8, 0x6D, 0x07, 0xA6, 0xF3,
        0x6E, 0x03, 0xA2, 0xF2, 0x6F, 0x07
    };

    // Encrypted: ca-app-pub-2223803405403727/8315151430
    const uint8_t ENC_APP_OPEN_AD_ID[] = {
        0x39, 0x52, 0xBC, 0xA6, 0x2A, 0x43, 0xBC, 0xB7,
        0x2F, 0x51, 0xBC, 0xF5, 0x68, 0x01, 0xA2, 0xFF,
        0x6A, 0x00, 0xA5, 0xF7, 0x6F, 0x07, 0xA1, 0xF4,
        0x6D, 0x01, 0xA6, 0xE8, 0x62, 0x00, 0xA0, 0xF2,
        0x6B, 0x06, 0xA0, 0xF3, 0x69, 0x03
    };

    // Encrypted: https://formspree.io/f/xyzenlao
    const uint8_t ENC_CONTACT_ENDPOINT[] = {
        0x32, 0x47, 0xE5, 0xB7, 0x29, 0x09, 0xBE, 0xE8,
        0x3C, 0x5C, 0xE3, 0xAA, 0x29, 0x43, 0xE3, 0xA2,
        0x3F, 0x1D, 0xF8, 0xA8, 0x75, 0x55, 0xBE, 0xBF,
        0x23, 0x49, 0xF4, 0xA9, 0x36, 0x52, 0xFE
    };
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_txtify_app_utils_NativeBridge_getBannerAdId(JNIEnv* env, jclass clazz) {
    std::string val = decodePayload(ENC_BANNER_AD_ID, sizeof(ENC_BANNER_AD_ID));
    return env->NewStringUTF(val.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_txtify_app_utils_NativeBridge_getAppOpenAdId(JNIEnv* env, jclass clazz) {
    std::string val = decodePayload(ENC_APP_OPEN_AD_ID, sizeof(ENC_APP_OPEN_AD_ID));
    return env->NewStringUTF(val.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_txtify_app_utils_NativeBridge_getContactEndpoint(JNIEnv* env, jclass clazz) {
    std::string val = decodePayload(ENC_CONTACT_ENDPOINT, sizeof(ENC_CONTACT_ENDPOINT));
    return env->NewStringUTF(val.c_str());
}

}