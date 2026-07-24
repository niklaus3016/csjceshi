package com.qingxujifen.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.pangle.cn.pangleadsdk.PangleAdManager;
import com.pangle.cn.pangleadsdk.PangleConfig;

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
        initPangleSdk();
    }

    private void initPangleSdk() {
        Log.d(TAG, "初始化GroMore融合SDK: appId=" + APP_ID);
        
        PangleAdManager.getInstance().init(this, new PangleConfig.Builder()
                .appId(APP_ID)
                .useMediation(true)
                .build());
        
        PangleAdManager.getInstance().start(new PangleAdManager.Callback() {
            @Override
            public void success() {
                Log.d(TAG, "GroMore融合SDK初始化成功");
            }
            
            @Override
            public void fail(int code, String msg) {
                Log.e(TAG, "GroMore融合SDK初始化失败: code=" + code + ", msg=" + msg);
            }
        });
    }
}
