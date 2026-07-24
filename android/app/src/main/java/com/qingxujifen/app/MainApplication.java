package com.qingxujifen.app;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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

        JSONObject configJsonObj = loadSiteConfig();

        TTAdConfig.Builder adConfigBuilder = new TTAdConfig.Builder()
                .appId(APP_ID)
                .appName("轻序计分")
                .allowShowNotify(true)
                .debug(false)
                .supportMultiProcess(false)
                .useMediation(true);

        if (configJsonObj != null) {
            Log.d(TAG, "配置文件加载成功，设置自定义本地配置");
            try {
                Class<?> mediationConfigClass = Class.forName("com.bytedance.sdk.openadsdk.MediationConfig");
                Class<?> mediationConfigBuilderClass = Class.forName("com.bytedance.sdk.openadsdk.MediationConfig$Builder");
                Object builder = mediationConfigBuilderClass.getDeclaredConstructor().newInstance();
                Object mediationConfig = mediationConfigBuilderClass.getMethod("setCustomLocalConfig", JSONObject.class)
                        .invoke(builder, configJsonObj);
                mediationConfig = mediationConfigBuilderClass.getMethod("build").invoke(mediationConfig);
                adConfigBuilder.getClass().getMethod("setMediationConfig", mediationConfigClass)
                        .invoke(adConfigBuilder, mediationConfig);
                Log.d(TAG, "setMediationConfig 反射调用成功");
            } catch (Exception e) {
                Log.e(TAG, "反射设置MediationConfig失败，继续初始化", e);
            }
        } else {
            Log.w(TAG, "配置文件加载失败，跳过自定义配置");
        }

        TTAdSdk.init(this, adConfigBuilder.build());

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

    private JSONObject loadSiteConfig() {
        String configFilePath = "site_config_5858423";
        try (InputStream is = getAssets().open(configFilePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String configJsonStr = sb.toString();
            Log.d(TAG, "配置文件读取成功，长度: " + configJsonStr.length());
            return new JSONObject(configJsonStr);
        } catch (IOException e) {
            Log.e(TAG, "读取配置文件失败: " + configFilePath, e);
        } catch (JSONException e) {
            Log.e(TAG, "解析配置文件JSON失败", e);
        }
        return null;
    }
}
