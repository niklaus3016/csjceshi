package com.gaoqianleme.app;

import android.app.Application;
import android.util.Log;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String APP_ID = "5793939";

    @Override
    public void onCreate() {
        super.onCreate();
        initTTAdSdk();
    }

    private void initTTAdSdk() {
        Log.d(TAG, "初始化穿山甲SDK: appId=" + APP_ID);
        
        TTAdSdk.init(this, new TTAdConfig.Builder()
                .appId(APP_ID)
                .useTextureView(true)
                .allowShowNotify(true)
                .allowShowPageWhenScreenLock(false)
                .debug(false)
                .supportMultiProcess(true)
                .titleBarTheme(TTAdConfig.TITLE_BAR_THEME_DARK)
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
