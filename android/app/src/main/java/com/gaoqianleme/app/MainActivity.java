package com.gaoqianleme.app;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 注册插件
        registerPlugin(CsjAdPlugin.class);
        registerPlugin(TTSPlugin.class);
        
        super.onCreate(savedInstanceState);
        
        // 配置 WebView 以支持现代 CSS 特性
        configureWebView();
    }
    
    private void configureWebView() {
        WebView webView = getBridge().getWebView();
        WebSettings settings = webView.getSettings();
        
        // 启用 JavaScript
        settings.setJavaScriptEnabled(true);
        
        // 启用 DOM storage
        settings.setDomStorageEnabled(true);
        
        // 启用数据库
        settings.setDatabaseEnabled(true);
        
        // 启用混合内容模式
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // 启用宽视口
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        // 启用硬件加速
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        
        // 设置用户代理，避免某些网站对 WebView 的限制
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " MobileApp/1.0");
    }
}
