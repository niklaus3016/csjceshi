package com.gaoqianleme.app;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String APP_ID = "5793939";

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "Application onCreate");
        
        String deviceId = getMyDeviceId();
        Log.d(TAG, "========================================");
        Log.d(TAG, "设备 ID: " + deviceId);
        Log.d(TAG, "请将此设备 ID 添加到穿山甲联盟后台的测试设备列表中");
        Log.d(TAG, "========================================");
        
        initCsjAdSDK();
    }

    private String getMyDeviceId() {
        try {
            return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            Log.e(TAG, "获取设备ID失败: " + e.getMessage());
            return "unknown";
        }
    }

    private void initCsjAdSDK() {
        try {
            Log.d(TAG, "开始初始化穿山甲广告SDK，App ID: " + APP_ID);
            
            TTAdConfig ttAdConfig = new TTAdConfig.Builder()
                    .appId(APP_ID)
                    .appName("搞钱乐么")
                    .debug(true)
                    .useMediation(false)
                    .build();
            
            TTAdSdk.init(this, ttAdConfig);
            
            TTAdSdk.start(new TTAdSdk.Callback() {
                @Override
                public void success() {
                    Log.d(TAG, "✅ 穿山甲广告SDK启动成功");
                }

                @Override
                public void fail(int code, String msg) {
                    Log.e(TAG, "❌ 穿山甲广告SDK启动失败: code=" + code + ", msg=" + msg);
                }
            });
            
            Log.d(TAG, "穿山甲广告SDK初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "穿山甲广告SDK初始化异常: " + e.getMessage(), e);
        }
    }
}