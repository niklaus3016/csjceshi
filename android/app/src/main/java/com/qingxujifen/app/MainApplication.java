package com.mijingxingzuo.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.bytedance.sdk.openadsdk.mediation.MediationConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

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
        
        JSONObject localConfig = loadLocalConfig();
        
        TTAdConfig.Builder configBuilder = new TTAdConfig.Builder()
                .appId(APP_ID)
                .appName("秘境星座")
                .allowShowNotify(true)
                .debug(false)
                .supportMultiProcess(false)
                .useMediation(true);
        
        if (localConfig != null) {
            configBuilder.setMediationConfig(
                new MediationConfig.Builder()
                    .setCustomLocalConfig(localConfig)
                    .build()
            );
            Log.d(TAG, "已加载本地配置文件");
        } else {
            Log.w(TAG, "未加载到本地配置文件");
        }
        
        TTAdSdk.init(this, configBuilder.build());
        
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
    
    private JSONObject loadLocalConfig() {
        try {
            InputStream is = getAssets().open("site_config_5860455.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            is.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "加载本地配置失败: " + e.getMessage());
            return null;
        }
    }
}
