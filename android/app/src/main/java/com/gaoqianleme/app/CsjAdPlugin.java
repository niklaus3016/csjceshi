package com.gaoqianleme.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdLoadType;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;

import java.util.Map;

@CapacitorPlugin(name = "CsjAd")
public class CsjAdPlugin extends Plugin {
    
    private static final String TAG = "CsjAdPlugin";
    private TTAdNative mTTAdNative;
    private TTRewardVideoAd mTTRewardVideoAd;
    private PluginCall pendingShowCall;
    private boolean isAdCached = false;
    
    @PluginMethod
    public void loadRewardVideoAd(PluginCall call) {
        String adId = call.getString("adId");
        if (adId == null || adId.isEmpty()) {
            call.reject("广告ID不能为空");
            return;
        }
        
        Log.d(TAG, "加载广告ID: " + adId);
        
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity 为空");
            return;
        }
        
        if (!TTAdSdk.isSdkReady()) {
            Log.e(TAG, "SDK未就绪，请等待初始化完成");
            call.reject("SDK未就绪");
            return;
        }
        
        if (mTTAdNative == null) {
            TTAdManager ttAdManager = TTAdSdk.getAdManager();
            if (ttAdManager == null) {
                call.reject("SDK未初始化");
                return;
            }
            mTTAdNative = ttAdManager.createAdNative(activity.getApplicationContext());
        }
        
        isAdCached = false;
        mTTRewardVideoAd = null;
        
        activity.runOnUiThread(() -> {
            try {
                AdSlot adSlot = new AdSlot.Builder()
                        .setCodeId(adId)
                        .setAdLoadType(TTAdLoadType.LOAD)
                        .setRewardAmount(1)
                        .setRewardName("金币")
                        .build();
                
                mTTAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
                    @Override
                    public void onError(int code, String message) {
                        Log.e(TAG, "广告加载失败: code=" + code + ", message=" + message);
                        notifyListeners("onAdFailed", new JSObject().put("error", message));
                    }
                    
                    @Override
                    public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                        Log.d(TAG, "广告加载成功");
                        handleAd(ad);
                        notifyListeners("onAdLoaded", new JSObject());
                    }
                    
                    @Override
                    public void onRewardVideoCached() {
                    }
                    
                    @Override
                    public void onRewardVideoCached(TTRewardVideoAd ad) {
                        Log.d(TAG, "广告缓存成功");
                        isAdCached = true;
                        handleAd(ad);
                        notifyListeners("onVideoDownloadSuccess", new JSObject());
                    }
                });
                
                call.resolve();
                
            } catch (Exception e) {
                Log.e(TAG, "加载广告异常: " + e.getMessage(), e);
                call.reject("加载广告异常: " + e.getMessage());
            }
        });
    }
    
    private void handleAd(TTRewardVideoAd ad) {
        if (mTTRewardVideoAd != null) {
            return;
        }
        mTTRewardVideoAd = ad;
        mTTRewardVideoAd.setRewardAdInteractionListener(new TTRewardVideoAd.RewardAdInteractionListener() {
            @Override
            public void onAdShow() {
                Log.d(TAG, "广告展示");
                notifyListeners("onAdShow", new JSObject());
            }
            
            @Override
            public void onAdVideoBarClick() {
                Log.d(TAG, "广告点击");
                notifyListeners("onAdClick", new JSObject());
            }
            
            @Override
            public void onAdClose() {
                Log.d(TAG, "广告关闭");
                notifyListeners("onAdClose", new JSObject());
                
                if (pendingShowCall != null) {
                    JSObject result = new JSObject();
                    result.put("rewardVerify", false);
                    result.put("ecpm", 0);
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mTTRewardVideoAd = null;
                isAdCached = false;
            }
            
            @Override
            public void onVideoComplete() {
                Log.d(TAG, "播放完成");
            }
            
            @Override
            public void onVideoError() {
                Log.e(TAG, "视频播放错误");
                notifyListeners("onVideoDownloadFailed", new JSObject());
            }
            
            @Override
            public void onRewardVerify(boolean rewardVerify, int rewardAmount, String rewardName, int errorCode, String errorMsg) {
            }
            
            @Override
            public void onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo) {
                Log.d(TAG, "获得奖励: " + isRewardValid);
                
                JSObject result = new JSObject();
                result.put("rewardVerify", isRewardValid);
                
                if (extraInfo != null) {
                    result.put("rewardName", extraInfo.getString(TTRewardVideoAd.REWARD_EXTRA_KEY_REWARD_NAME));
                    result.put("rewardAmount", extraInfo.getInt(TTRewardVideoAd.REWARD_EXTRA_KEY_REWARD_AMOUNT));
                    result.put("rewardPropose", extraInfo.getFloat(TTRewardVideoAd.REWARD_EXTRA_KEY_REWARD_PROPOSE));
                    result.put("errorCode", extraInfo.getInt(TTRewardVideoAd.REWARD_EXTRA_KEY_ERROR_CODE));
                    result.put("errorMsg", extraInfo.getString(TTRewardVideoAd.REWARD_EXTRA_KEY_ERROR_MSG));
                }
                
                result.put("ecpm", 0);
                
                notifyListeners("onRewardVerify", result);
                
                if (pendingShowCall != null) {
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
            }
            
            @Override
            public void onSkippedVideo() {
                Log.d(TAG, "广告跳过");
            }
        });
    }
    
    @PluginMethod
    public void showRewardVideoAd(PluginCall call) {
        Log.d(TAG, "显示广告");
        
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity 为空");
            return;
        }
        
        if (mTTRewardVideoAd == null) {
            call.reject("广告未加载");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                pendingShowCall = call;
                mTTRewardVideoAd.showRewardVideoAd(activity);
            } catch (Exception e) {
                Log.e(TAG, "展示广告异常: " + e.getMessage(), e);
                call.reject("展示广告异常: " + e.getMessage());
                pendingShowCall = null;
            }
        });
    }
    
    @PluginMethod
    public void isReady(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", mTTRewardVideoAd != null);
        call.resolve(result);
    }
}