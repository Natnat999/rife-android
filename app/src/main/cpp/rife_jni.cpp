#include <jni.h>
#include <string>
#include <android/log.h>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "stb_image_write.h"

#include "net.h"
#include "gpu.h"

#define TAG "RIFE_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static ncnn::Net* g_rife_net = nullptr;
static ncnn::VulkanDevice* g_vkdev = nullptr;

extern "C" JNIEXPORT jint JNICALL
Java_com_rife_android_RifeEngine_initEngine(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath) {
    
    ncnn::create_gpu_instance();
    g_vkdev = ncnn::get_gpu_device(0);
    
    g_rife_net = new ncnn::Net();
    g_rife_net->opt.use_vulkan_compute = true;
    g_rife_net->set_vulkan_device(g_vkdev);
    
    const char* model_path_str = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(model_path_str);
    
    std::string param_path = path + "/rife-v4.6.param";
    std::string bin_path = path + "/rife-v4.6.bin";
    
    int ret1 = g_rife_net->load_param(param_path.c_str());
    int ret2 = g_rife_net->load_model(bin_path.c_str());
    
    env->ReleaseStringUTFChars(modelPath, model_path_str);
    
    if (ret1 != 0 || ret2 != 0) {
        LOGE("Failed to load RIFE model: %d %d", ret1, ret2);
        return -1;
    }
    
    LOGD("RIFE engine initialized via direct NCNN.");
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rife_android_RifeEngine_destroyEngine(
        JNIEnv* env,
        jobject /* this */) {
    if (g_rife_net) {
        delete g_rife_net;
        g_rife_net = nullptr;
    }
    ncnn::destroy_gpu_instance();
    LOGD("RIFE engine destroyed.");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rife_android_RifeEngine_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jstring frame0_path,
        jstring frame1_path,
        jstring out_path) {
    
    if (!g_rife_net) return -1;

    const char* f0_path = env->GetStringUTFChars(frame0_path, nullptr);
    const char* f1_path = env->GetStringUTFChars(frame1_path, nullptr);
    const char* o_path = env->GetStringUTFChars(out_path, nullptr);

    int w, h, c;
    unsigned char* p0 = stbi_load(f0_path, &w, &h, &c, 3);
    unsigned char* p1 = stbi_load(f1_path, &w, &h, &c, 3);

    if (!p0 || !p1) {
        if (p0) stbi_image_free(p0);
        if (p1) stbi_image_free(p1);
        return -1;
    }

    ncnn::Mat in0 = ncnn::Mat::from_pixels(p0, ncnn::Mat::PIXEL_RGB, w, h);
    ncnn::Mat in1 = ncnn::Mat::from_pixels(p1, ncnn::Mat::PIXEL_RGB, w, h);
    
    // Simple RIFE v4 inference logic
    ncnn::Extractor ex = g_rife_net->create_extractor();
    ex.input("input0", in0);
    ex.input("input1", in1);
    
    ncnn::Mat out;
    ex.extract("output", out);

    unsigned char* out_pixels = new unsigned char[w * h * 3];
    out.to_pixels(out_pixels, ncnn::Mat::PIXEL_RGB);
    stbi_write_png(o_path, w, h, 3, out_pixels, w * 3);

    delete[] out_pixels;
    stbi_image_free(p0);
    stbi_image_free(p1);

    env->ReleaseStringUTFChars(frame0_path, f0_path);
    env->ReleaseStringUTFChars(frame1_path, f1_path);
    env->ReleaseStringUTFChars(out_path, o_path);

    return 0;
}
