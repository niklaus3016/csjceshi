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

import java.util.Map;

@CapacitorPlugin(name = "CsjAd")
public class CsjAdPlugin extends Plugin {
    
    private static final String TAG = "CsjAdPlugin";
    private TTAdNative mTTAdNative;
    private TTRewardVideoAd mRewardVideoAd;
    private PluginCall pendingShowCall;
    private double mLastAdRealEcpm = 0.0;
    
    private TTAdInteractionListener mInteractionListener = new TTAdInteractionListener() {
        @Override
        public void onAdEvent(int code, Map map) {
            if (map == null) {
                return;
            }
            switch (code) {
                case TTAdConstant.AD_EVENT_AUTH_DOUYIN:
                    String uid = (String) map.get("open_uid");
                    Log.d(TAG, "授权成功 --> uid：" + uid);
                    break;
                case TTAdConstant.AD_EVENT_EXCHANGE_COUPON_FINISH:
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
        
        if (!TTAdSdk.isSdkReady()) {
            Log.e(TAG, "穿山甲SDK未就绪");
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
                
                AdSlot adSlot = new AdSlot.Builder()
                        .setCodeId(adId)
                        .setAdLoadType(TTAdLoadType.LOAD)
                        .setRewardAmount(1)
                        .setRewardName("金币")
                        .setOrientation(TTAdConstant.VERTICAL)
                        .build();
                
                mTTAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
                    @Override
                    public void onError(int code, String message) {
                        Log.e(TAG, "广告加载失败: code=" + code + ", message=" + message);
                        JSObject errorResult = new JSObject();
                        errorResult.put("error", message);
                        errorResult.put("code", code);
                        notifyListeners("onAdFailed", errorResult);
                    }
                    
                    @Override
                    public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                        Log.d(TAG, "广告加载成功");
                        mRewardVideoAd = ad;
                        mLastAdRealEcpm = 0.0;
                        
                        try {
                            Log.d(TAG, "=== 开始反射获取ECPM ===");
                            
                            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
                            Log.d(TAG, "mediationManager 获取成功: " + (mediationManager != null ? mediationManager.getClass().getName() : "null"));
                            
                            if (mediationManager != null) {
                                Object loadEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                                Log.d(TAG, "loadEcpmObj 获取成功: " + (loadEcpmObj != null ? loadEcpmObj.getClass().getName() : "null"));
                                
                                if (loadEcpmObj != null) {
                                    Object ecpmObj = loadEcpmObj.getClass().getMethod("getEcpm").invoke(loadEcpmObj);
                                    Log.d(TAG, "ecpmObj 获取成功: " + ecpmObj + ", 类型: " + (ecpmObj != null ? ecpmObj.getClass().getName() : "null"));
                                    
                                    if (ecpmObj != null) {
                                        if (ecpmObj instanceof Double) {
                                            mLastAdRealEcpm = (Double) ecpmObj;
                                        } else if (ecpmObj instanceof Integer) {
                                            mLastAdRealEcpm = ((Integer) ecpmObj).doubleValue();
                                        } else if (ecpmObj instanceof Long) {
                                            mLastAdRealEcpm = ((Long) ecpmObj).doubleValue();
                                        } else if (ecpmObj instanceof Float) {
                                            mLastAdRealEcpm = ((Float) ecpmObj).doubleValue();
                                        } else {
                                            mLastAdRealEcpm = Double.parseDouble(ecpmObj.toString());
                                        }
                                    }
                                    Log.d(TAG, "【保价广告加载成功】档位实际ECPM：" + mLastAdRealEcpm);
                                }
                            }
                        } catch (NoSuchMethodException e) {
                            Log.e(TAG, "反射获取ECPM失败 - 方法不存在: " + e.getMessage());
                            mLastAdRealEcpm = 0.0;
                        } catch (IllegalAccessException e) {
                            Log.e(TAG, "反射获取ECPM失败 - 访问权限: " + e.getMessage());
                            mLastAdRealEcpm = 0.0;
                        } catch (Exception e) {
                            Log.e(TAG, "反射获取ECPM失败 - 其他异常", e);
                            mLastAdRealEcpm = 0.0;
                        }
                        
                        setupAdListener(ad);
                        notifyListeners("onAdLoaded", new JSObject());
                    }
                    
                    @Override
                    public void onRewardVideoCached() {
                    }
                    
                    @Override
                    public void onRewardVideoCached(TTRewardVideoAd ad) {
                        Log.d(TAG, "广告缓存成功");
                        mRewardVideoAd = ad;
                        mLastAdRealEcpm = 0.0;
                        
                        try {
                            Log.d(TAG, "=== 开始反射获取ECPM (缓存) ===");
                            
                            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
                            Log.d(TAG, "mediationManager 获取成功: " + (mediationManager != null ? mediationManager.getClass().getName() : "null"));
                            
                            if (mediationManager != null) {
                                Object loadEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                                Log.d(TAG, "loadEcpmObj 获取成功: " + (loadEcpmObj != null ? loadEcpmObj.getClass().getName() : "null"));
                                
                                if (loadEcpmObj != null) {
                                    Object ecpmObj = loadEcpmObj.getClass().getMethod("getEcpm").invoke(loadEcpmObj);
                                    Log.d(TAG, "ecpmObj 获取成功: " + ecpmObj + ", 类型: " + (ecpmObj != null ? ecpmObj.getClass().getName() : "null"));
                                    
                                    if (ecpmObj != null) {
                                        if (ecpmObj instanceof Double) {
                                            mLastAdRealEcpm = (Double) ecpmObj;
                                        } else if (ecpmObj instanceof Integer) {
                                            mLastAdRealEcpm = ((Integer) ecpmObj).doubleValue();
                                        } else if (ecpmObj instanceof Long) {
                                            mLastAdRealEcpm = ((Long) ecpmObj).doubleValue();
                                        } else if (ecpmObj instanceof Float) {
                                            mLastAdRealEcpm = ((Float) ecpmObj).doubleValue();
                                        } else {
                                            mLastAdRealEcpm = Double.parseDouble(ecpmObj.toString());
                                        }
                                    }
                                    Log.d(TAG, "【保价广告缓存成功】档位实际ECPM：" + mLastAdRealEcpm);
                                }
                            }
                        } catch (NoSuchMethodException e) {
                            Log.e(TAG, "反射获取ECPM失败(缓存) - 方法不存在: " + e.getMessage());
                            mLastAdRealEcpm = 0.0;
                        } catch (IllegalAccessException e) {
                            Log.e(TAG, "反射获取ECPM失败(缓存) - 访问权限: " + e.getMessage());
                            mLastAdRealEcpm = 0.0;
                        } catch (Exception e) {
                            Log.e(TAG, "反射获取ECPM失败(缓存) - 其他异常", e);
                            mLastAdRealEcpm = 0.0;
                        }
                        
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
    
    private void setupAdListener(TTRewardVideoAd ad) {
        if (ad == null) return;
        
        ad.setRewardAdInteractionListener(new TTRewardVideoAd.RewardAdInteractionListener() {
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
                
                mRewardVideoAd = null;
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
                
                result.put("ecpm", mLastAdRealEcpm);
                Log.d(TAG, "下发给JS的ECPM：" + mLastAdRealEcpm);
                
                notifyListeners("onRewardVerify", result);
                
                if (pendingShowCall != null) {
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mLastAdRealEcpm = 0.0;
            }
            
            @Override
            public void onSkippedVideo() {
                Log.d(TAG, "广告跳过");
            }
        });
        
        ad.setDownloadListener(new TTAppDownloadListener() {
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
            } catch (Exception e) {
                Log.e(TAG, "展示广告异常: " + e.getMessage(), e);
                call.reject("展示广告异常: " + e.getMessage());
                pendingShowCall = null;
                mRewardVideoAd = null;
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
        result.put("ready", TTAdSdk.isSdkReady());
        call.resolve(result);
    }
}
