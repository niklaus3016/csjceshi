package com.gaoqianleme.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.pangle.cn.pangleadsdk.PangleAdManager;
import com.pangle.cn.pangleadsdk.PangleConfig;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String APP_ID = "5793939";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initPangleSdk();
    }

    private void initPangleSdk() {
        Log.d(TAG, "初始化GroMore融合SDK(纯穿山甲模式): appId=" + APP_ID);
        
        PangleAdManager.getInstance().init(this, new PangleConfig.Builder()
                .appId(APP_ID)
                .appName("荔枝记账")
                .useMediation(false)
                .debug(false)
                .build());
        
        Log.d(TAG, "GroMore融合SDK初始化完成");
    }
}
