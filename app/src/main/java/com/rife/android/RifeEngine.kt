package com.rife.android

class RifeEngine {
    companion object {
        init {
            System.loadLibrary("rife_android")
        }
    }

    external fun initEngine(modelPath: String): Int
    external fun destroyEngine()
    external fun processFrame(frame0Path: String, frame1Path: String, outPath: String): Int
}
