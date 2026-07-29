package com.mijingxingzuo.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String APP_ID = "5860455";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initTTAdSdk();
    }

    private void initTTAdSdk() {
        Log.d(TAG, "初始化穿山甲SDK: appId=" + APP_ID);
        
        TTAdSdk.init(this, new TTAdConfig.Builder()
                .appId(APP_ID)
                .appName("秘境星座")
                .allowShowNotify(true)
                .debug(false)
                .supportMultiProcess(false)
                .useMediation(true)
                .build());
        
        TTAdSdk.start(new TTAdSdk.Callback() {
            @Override
            public void success() {
                Log.d(TAG, "穿山甲SDK初始化成功");
            }
            
            @Override
            public void fail(int code, String msg) {
                Log.e(TAG, "穿山甲SDK初始化失败: code=" + code + ", msg=" + msg);
            }
        });
    }
}
