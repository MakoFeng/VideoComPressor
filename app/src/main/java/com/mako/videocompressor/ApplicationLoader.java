package com.mako.videocompressor;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

public class ApplicationLoader extends Application {

    public static volatile Context applicationContext;

    public static volatile Handler applicationHandler;

    @Override
    public void onCreate() {
        try {
            applicationContext = getApplicationContext();
        } catch (Throwable ignore) {

        }

        super.onCreate();

        if (applicationContext == null) {
            applicationContext = getApplicationContext();
        }

        applicationHandler = new Handler(applicationContext.getMainLooper());

    }
}