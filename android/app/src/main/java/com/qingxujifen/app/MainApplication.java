package com.qingxujifen.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String APP_ID = "5858423";

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
                .appName("轻序计分")
                .allowShowNotify(true)
                .debug(false)
                .supportMultiProcess(false)
                .useMediation(true)
                .build());
        
        TTAdSdk.start(new TTAdSdk.Callback() {
            @Override
            public void success() {
                Log.d(TAG, "穿山甲SDK初始化成功");
                startPreload();
            }
            
            @Override
            public void fail(int code, String msg) {
                Log.e(TAG, "穿山甲SDK初始化失败: code=" + code + ", msg=" + msg);
            }
        });
    }
    
    private void startPreload() {
        try {
            Class<?> mediationManagerClass = Class.forName("com.bytedance.sdk.openadsdk.mediation.IMediationManager");
            Object mediationManager = TTAdSdk.getMediationManager();
            
            if (mediationManager == null) {
                Log.d(TAG, "预缓存: mediationManager为空，跳过");
                return;
            }
            
            Class<?> adSlotClass = Class.forName("com.bytedance.sdk.openadsdk.AdSlot");
            Object rewardAdSlot = adSlotClass.getMethod("build").invoke(
                    adSlotClass.getMethod("newBuilder").invoke(null)
            );
            
            java.util.List<String> rewardPrimeRitList = new java.util.ArrayList<>();
            rewardPrimeRitList.add("104282400");
            
            Class<?> mediationPreloadRequestInfoClass = Class.forName("com.bytedance.sdk.openadsdk.mediation.MediationPreloadRequestInfo");
            Object rewardPreloadInfo = mediationPreloadRequestInfoClass.getConstructor(
                    int.class, adSlotClass, java.util.List.class
            ).newInstance(adSlotClass.getField("TYPE_REWARD_VIDEO").getInt(null), rewardAdSlot, rewardPrimeRitList);
            
            java.util.List<Object> requestInfoList = new java.util.ArrayList<>();
            requestInfoList.add(rewardPreloadInfo);
            
            java.lang.reflect.Method preloadMethod = mediationManagerClass.getMethod(
                    "preload", Context.class, java.util.List.class, int.class, int.class
            );
            preloadMethod.invoke(mediationManager, MainApplication.this, requestInfoList, 2, 2);
            
            Log.d(TAG, "预缓存: 已发起激励视频预请求，流量位ID=104282400");
            
        } catch (Exception e) {
            Log.e(TAG, "预缓存: 调用preload接口失败: " + e.getMessage());
        }
    }
}
