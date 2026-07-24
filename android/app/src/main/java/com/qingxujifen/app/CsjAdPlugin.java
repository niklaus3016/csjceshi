package com.qingxujifen.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.pangle.cn.pangleadsdk.AdSlot;
import com.pangle.cn.pangleadsdk.PangleAdConstant;
import com.pangle.cn.pangleadsdk.PangleAdInteractionListener;
import com.pangle.cn.pangleadsdk.PangleAdLoadType;
import com.pangle.cn.pangleadsdk.PangleAdManager;
import com.pangle.cn.pangleadsdk.PangleAdNative;
import com.pangle.cn.pangleadsdk.PangleAppDownloadListener;
import com.pangle.cn.pangleadsdk.PangleRewardVideoAd;

import java.util.Map;

@CapacitorPlugin(name = "CsjAd")
public class CsjAdPlugin extends Plugin {
    
    private static final String TAG = "CsjAdPlugin";
    private PangleAdNative mAdNative;
    private PangleRewardVideoAd mRewardVideoAd;
    private PluginCall pendingShowCall;
    
    private PangleAdInteractionListener mInteractionListener = new PangleAdInteractionListener() {
        @Override
        public void onAdEvent(int code, Map map) {
            if (map == null) {
                return;
            }
            switch (code) {
                case PangleAdConstant.AD_EVENT_AUTH_DOUYIN:
                    String uid = (String) map.get("open_uid");
                    Log.d(TAG, "授权成功 --> uid：" + uid);
                    break;
                case PangleAdConstant.AD_EVENT_EXCHANGE_COUPON_FINISH:
                    String isSuccess = String.valueOf(map.get("isSuccess"));
                    Log.d(TAG, "兑换结果：" + isSuccess);
                    break;
            }
        }
    };
    
    @PluginMethod
    public void loadRewardVideoAd(PluginCall call) {
        String adId = call.getString("adId");
        if (adId == null || adId.isEmpty()) {
            call.reject("广告ID不能为空");
            return;
        }
        
        Log.d(TAG, "加载广告ID: " + adId);
        
        if (!PangleAdManager.getInstance().isSdkReady()) {
            Log.e(TAG, "GroMore融合SDK未就绪");
            call.reject("GroMore融合SDK未就绪");
            return;
        }
        
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity 为空");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                mAdNative = PangleAdManager.getInstance().createAdNative(activity.getApplicationContext());
                
                AdSlot adSlot = new AdSlot.Builder()
                        .setCodeId(adId)
                        .setAdLoadType(PangleAdLoadType.LOAD)
                        .setRewardAmount(1)
                        .setRewardName("金币")
                        .setOrientation(PangleAdConstant.VERTICAL)
                        .build();
                
                mAdNative.loadRewardVideoAd(adSlot, new PangleAdNative.RewardVideoAdListener() {
                    @Override
                    public void onError(int code, String message) {
                        Log.e(TAG, "广告加载失败: code=" + code + ", message=" + message);
                        JSObject errorResult = new JSObject();
                        errorResult.put("error", message);
                        errorResult.put("code", code);
                        notifyListeners("onAdFailed", errorResult);
                    }
                    
                    @Override
                    public void onRewardVideoAdLoad(PangleRewardVideoAd ad) {
                        Log.d(TAG, "广告加载成功");
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
                        notifyListeners("onAdLoaded", new JSObject());
                    }
                    
                    @Override
                    public void onRewardVideoCached() {
                    }
                    
                    @Override
                    public void onRewardVideoCached(PangleRewardVideoAd ad) {
                        Log.d(TAG, "广告缓存成功");
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
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
    
    private void setupAdListener(PangleRewardVideoAd ad) {
        if (ad == null) return;
        
        ad.setRewardAdInteractionListener(new PangleRewardVideoAd.RewardAdInteractionListener() {
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
                Log.d(TAG, "获得奖励: isRewardValid=" + isRewardValid + ", rewardType=" + rewardType);
                
                JSObject result = new JSObject();
                result.put("rewardVerify", isRewardValid);
                
                if (extraInfo != null) {
                    result.put("rewardAmount", extraInfo.getInt("reward_amount", 1));
                    result.put("rewardName", extraInfo.getString("reward_name", "金币"));
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
        
        ad.setDownloadListener(new PangleAppDownloadListener() {
            @Override
            public void onIdle() {
            }
            
            @Override
            public void onDownloadActive(long totalBytes, long currBytes, String fileName, String appName) {
                Log.d(TAG, "onDownloadActive: totalBytes=" + totalBytes + ", currBytes=" + currBytes);
            }
            
            @Override
            public void onDownloadPaused(long totalBytes, long currBytes, String fileName, String appName) {
                Log.d(TAG, "onDownloadPaused: totalBytes=" + totalBytes + ", currBytes=" + currBytes);
            }
            
            @Override
            public void onDownloadFailed(long totalBytes, long currBytes, String fileName, String appName) {
                Log.d(TAG, "onDownloadFailed: totalBytes=" + totalBytes + ", currBytes=" + currBytes);
            }
            
            @Override
            public void onDownloadFinished(long totalBytes, String fileName, String appName) {
                Log.d(TAG, "onDownloadFinished: totalBytes=" + totalBytes);
            }
            
            @Override
            public void onInstalled(String fileName, String appName) {
                Log.d(TAG, "onInstalled: fileName=" + fileName + ", appName=" + appName);
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
        
        if (mRewardVideoAd == null) {
            call.reject("广告未加载");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                pendingShowCall = call;
                mRewardVideoAd.showRewardVideoAd(activity);
                mRewardVideoAd.setAdInteractionListener(mInteractionListener);
                mRewardVideoAd = null;
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
        result.put("ready", mRewardVideoAd != null);
        call.resolve(result);
    }
    
    @PluginMethod
    public void isSdkReady(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", PangleAdManager.getInstance().isSdkReady());
        call.resolve(result);
    }
}
