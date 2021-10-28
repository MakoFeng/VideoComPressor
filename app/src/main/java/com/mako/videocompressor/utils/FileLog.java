package com.mako.videocompressor.utils;

import android.util.Log;


public class FileLog {

    public static void d(String msg) {
        Log.d("VideoCompressor", msg);
    }

    public static void e(String msg) {
        Log.e("VideoCompressor", msg);
    }

    public static void e(Throwable e) {
        Log.e("VideoCompressor", "" + e);
    }

}
