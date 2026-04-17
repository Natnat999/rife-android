cmake_minimum_required(VERSION 3.22.1)

project("rife_android")

set(CMAKE_CXX_STANDARD 17)

set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

find_package(Vulkan REQUIRED)
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

add_library(${CMAKE_PROJECT_NAME} SHARED
    rife_jni.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ncnn
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    Vulkan::Vulkan
)
