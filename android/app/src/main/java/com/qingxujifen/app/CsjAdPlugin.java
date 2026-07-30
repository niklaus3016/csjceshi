package com.qingxujifen.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdInteractionListener;
import com.bytedance.sdk.openadsdk.TTAdLoadType;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.bytedance.sdk.openadsdk.TTAppDownloadListener;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;

@CapacitorPlugin(name = "CsjAd")
public class CsjAdPlugin extends Plugin {
    
    private static final String TAG = "CsjAdPlugin";
    private TTAdNative mTTAdNative;
    private TTRewardVideoAd mRewardVideoAd;
    private PluginCall pendingShowCall;
    private double mLastAdRealEcpm = 0.0;
    private long mLoadStartTime = 0;
    private String mCurrentAdId = "";
    private String mHitSlotId = "";
    private String mSdkName = "";
    
    private void sendDebugLog(String tag, String message) {
        Log.d(TAG, "[" + tag + "] " + message);
        JSObject logData = new JSObject();
        logData.put("tag", tag);
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        notifyListeners("onCsjDebugLog", logData);
    }
    
    @PluginMethod
    public void loadRewardVideoAd(PluginCall call) {
        String adId = call.getString("adId");
        mCurrentAdId = adId;
        mLoadStartTime = System.currentTimeMillis();
        mLastAdRealEcpm = 0.0;
        mHitSlotId = "";
        mSdkName = "";
        
        sendDebugLog("LOAD", "开始加载广告: " + adId);
        
        if (adId == null || adId.isEmpty()) {
            call.reject("广告ID不能为空");
            return;
        }
        
        if (!TTAdSdk.isSdkReady()) {
            call.reject("穿山甲SDK未就绪");
            return;
        }
        
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity 为空");
            return;
        }
        
        activity.runOnUiThread(() -> {
            try {
                TTAdManager ttAdManager = TTAdSdk.getAdManager();
                mTTAdNative = ttAdManager.createAdNative(activity.getApplicationContext());
                
                String userId = call.getString("userId");
                String extraData = call.getString("extraData");
                
                AdSlot.Builder adSlotBuilder = new AdSlot.Builder()
                        .setCodeId(adId)
                        .setAdLoadType(TTAdLoadType.LOAD)
                        .setRewardAmount(1)
                        .setRewardName("金币")
                        .setOrientation(TTAdConstant.VERTICAL);
                
                if (userId != null && !userId.isEmpty()) {
                    adSlotBuilder.setUserID(userId);
                }
                
                if (extraData != null && !extraData.isEmpty()) {
                    try {
                        java.lang.reflect.Method setExtraMethod = AdSlot.Builder.class.getMethod("setExtra", String.class);
                        setExtraMethod.invoke(adSlotBuilder, extraData);
                        sendDebugLog("LOAD", "已设置extra透传参数");
                    } catch (NoSuchMethodException e1) {
                        try {
                            java.lang.reflect.Method setCustomDataMethod = AdSlot.Builder.class.getMethod("setCustomData", String.class);
                            setCustomDataMethod.invoke(adSlotBuilder, extraData);
                            sendDebugLog("LOAD", "已设置customData透传参数");
                        } catch (NoSuchMethodException e2) {
                            try {
                                java.lang.reflect.Method setExtraObjectMethod = AdSlot.Builder.class.getMethod("setExtraObject", String.class, Object.class);
                                setExtraObjectMethod.invoke(adSlotBuilder, "GroMore_EXTRA", extraData);
                                sendDebugLog("LOAD", "已设置extraObject透传参数");
                            } catch (NoSuchMethodException e3) {
                                try {
                                    Class<?> mediationAdSlotClass = Class.forName("com.bytedance.sdk.openadsdk.mediation.MediationAdSlot");
                                    Object mediationAdSlot = mediationAdSlotClass.getDeclaredConstructor().newInstance();
                                    
                                    try {
                                        Class<?> mediationConstantClass = Class.forName("com.bytedance.sdk.openadsdk.mediation.MediationConstant");
                                        Object keyGroMoreExtra = mediationConstantClass.getField("KEY_GroMore_EXTRA").get(null);
                                        java.lang.reflect.Method setExtraObjMethod = mediationAdSlotClass.getMethod("setExtraObject", String.class, Object.class);
                                        setExtraObjMethod.invoke(mediationAdSlot, keyGroMoreExtra, extraData);
                                        sendDebugLog("LOAD", "已设置GroMore服务端奖励透传参数");
                                    } catch (Exception e4) {
                                        sendDebugLog("LOAD", "SDK版本不支持MediationConstant.KEY_GroMore_EXTRA，跳过");
                                    }
                                    
                                    java.lang.reflect.Method setMediationAdSlotMethod = AdSlot.Builder.class.getMethod("setMediationAdSlot", mediationAdSlotClass);
                                    adSlotBuilder = (AdSlot.Builder) setMediationAdSlotMethod.invoke(adSlotBuilder, mediationAdSlot);
                                    
                                } catch (Exception e4) {
                                    sendDebugLog("LOAD", "SDK版本不支持透传参数，所有方法均失败");
                                }
                            }
                        }
                    }
                }
                
                AdSlot adSlot = adSlotBuilder.build();
                
                mTTAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
                    @Override
                    public void onError(int code, String message) {
                        sendDebugLog("ERROR", "广告加载失败: code=" + code + ", msg=" + message);
                        JSObject errorResult = new JSObject();
                        errorResult.put("error", message);
                        errorResult.put("code", code);
                        notifyListeners("onAdFailed", errorResult);
                    }
                    
                    @Override
                    public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                        sendDebugLog("SUCCESS", "广告加载成功: " + mCurrentAdId);
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
                        notifyListeners("onAdLoaded", new JSObject());
                    }
                    
                    @Override
                    public void onRewardVideoCached() {
                    }
                    
                    @Override
                    public void onRewardVideoCached(TTRewardVideoAd ad) {
                        sendDebugLog("SUCCESS", "广告缓存成功: " + mCurrentAdId);
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
                        notifyListeners("onVideoDownloadSuccess", new JSObject());
                    }
                });
                
                call.resolve();
                
            } catch (Exception e) {
                call.reject("加载广告异常: " + e.getMessage());
            }
        });
    }
    
    private void setupAdListener(TTRewardVideoAd ad) {
        if (ad == null) return;
        
        ad.setRewardAdInteractionListener(new TTRewardVideoAd.RewardAdInteractionListener() {
            @Override
            public void onAdShow() {
                sendDebugLog("AD", "广告开始展示");
                extractShowInfo(ad);
                notifyListeners("onAdShow", new JSObject());
            }
            
            @Override
            public void onAdVideoBarClick() {
                notifyListeners("onAdClick", new JSObject());
            }
            
            @Override
            public void onAdClose() {
                sendDebugLog("AD", "广告关闭");
                notifyListeners("onAdClose", new JSObject());
                
                if (pendingShowCall != null) {
                    JSObject result = new JSObject();
                    result.put("rewardVerify", false);
                    result.put("ecpm", mLastAdRealEcpm);
                    result.put("slotId", mHitSlotId);
                    result.put("adn", mSdkName);
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mRewardVideoAd = null;
            }
            
            @Override
            public void onVideoComplete() {
            }
            
            @Override
            public void onVideoError() {
                notifyListeners("onVideoDownloadFailed", new JSObject());
            }
            
            @Override
            public void onRewardVerify(boolean rewardVerify, int rewardAmount, String rewardName, int errorCode, String errorMsg) {
            }
            
            @Override
            public void onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo) {
                sendDebugLog("REWARD", "获得奖励: isRewardValid=" + isRewardValid + ", ECPM=" + mLastAdRealEcpm);
                
                JSObject result = new JSObject();
                result.put("rewardVerify", isRewardValid);
                
                if (extraInfo != null) {
                    result.put("rewardAmount", extraInfo.getInt("reward_amount", 1));
                    result.put("rewardName", extraInfo.getString("reward_name", "金币"));
                }
                
                result.put("ecpm", mLastAdRealEcpm);
                result.put("slotId", mHitSlotId);
                result.put("adn", mSdkName);
                
                notifyListeners("onRewardVerify", result);
                
                if (pendingShowCall != null) {
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mLastAdRealEcpm = 0.0;
                mHitSlotId = "";
                mSdkName = "";
            }
            
            @Override
            public void onSkippedVideo() {
            }
        });
        
        ad.setDownloadListener(new TTAppDownloadListener() {
            @Override
            public void onIdle() {}
            @Override
            public void onDownloadActive(long totalBytes, long currBytes, String fileName, String appName) {}
            @Override
            public void onDownloadPaused(long totalBytes, long currBytes, String fileName, String appName) {}
            @Override
            public void onDownloadFailed(long totalBytes, long currBytes, String fileName, String appName) {}
            @Override
            public void onDownloadFinished(long totalBytes, String fileName, String appName) {}
            @Override
            public void onInstalled(String fileName, String appName) {}
        });
    }
    
    private void extractShowInfo(TTRewardVideoAd ad) {
        try {
            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
            if (mediationManager != null) {
                Object showEcpmObj = null;
                
                try {
                    showEcpmObj = mediationManager.getClass().getMethod("getShowEcpm").invoke(mediationManager);
                } catch (NoSuchMethodException e) {
                    try {
                        showEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                    } catch (NoSuchMethodException e2) {
                        try {
                            showEcpmObj = mediationManager.getClass().getMethod("getEcpm").invoke(mediationManager);
                        } catch (NoSuchMethodException e3) {
                            sendDebugLog("ECPM", "未找到ECPM相关方法");
                        }
                    }
                }
                
                if (showEcpmObj != null) {
                    try {
                        Object ecpmVal = showEcpmObj.getClass().getMethod("getEcpm").invoke(showEcpmObj);
                        if (ecpmVal != null) {
                            if (ecpmVal instanceof Double) {
                                mLastAdRealEcpm = (Double) ecpmVal;
                            } else if (ecpmVal instanceof Integer) {
                                mLastAdRealEcpm = ((Integer) ecpmVal).doubleValue();
                            } else if (ecpmVal instanceof Long) {
                                mLastAdRealEcpm = ((Long) ecpmVal).doubleValue();
                            } else if (ecpmVal instanceof Float) {
                                mLastAdRealEcpm = ((Float) ecpmVal).doubleValue();
                            } else {
                                mLastAdRealEcpm = Double.parseDouble(ecpmVal.toString());
                            }
                            mLastAdRealEcpm = mLastAdRealEcpm / 100.0;
                        }
                    } catch (Exception e) {
                        sendDebugLog("ECPM", "获取Ecpm值失败: " + e.getMessage());
                    }
                    
                    try {
                        Object sdkName = showEcpmObj.getClass().getMethod("getSdkName").invoke(showEcpmObj);
                        mSdkName = sdkName != null ? sdkName.toString() : "";
                    } catch (Exception e) {
                    }
                    
                    try {
                        Object slotId = showEcpmObj.getClass().getMethod("getSlotId").invoke(showEcpmObj);
                        mHitSlotId = slotId != null ? slotId.toString() : "";
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            sendDebugLog("ECPM", "获取展示信息失败: " + e.getMessage());
        }
        
        sendDebugLog("ECPM", "ECPM=" + mLastAdRealEcpm + ", slotId=" + mHitSlotId + ", adn=" + mSdkName);
    }
    
    @PluginMethod
    public void showRewardVideoAd(PluginCall call) {
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
            } catch (Exception e) {
                call.reject("展示广告异常: " + e.getMessage());
                pendingShowCall = null;
                mRewardVideoAd = null;
            }
        });
    }
    
    @PluginMethod
    public void isReady(PluginCall call) {
        boolean ready = mRewardVideoAd != null;
        JSObject result = new JSObject();
        result.put("ready", ready);
        call.resolve(result);
    }
    
    @PluginMethod
    public void isSdkReady(PluginCall call) {
        boolean ready = TTAdSdk.isSdkReady();
        JSObject result = new JSObject();
        result.put("ready", ready);
        call.resolve(result);
    }
}
