package com.txtify.app.utils;

public class NativeBridge {

    static {
        System.loadLibrary("txtify_native");
    }

    public static native String getBannerAdId();

    public static native String getAppOpenAdId();

    public static native String getContactEndpoint();
}