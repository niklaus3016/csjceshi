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
        
        sendDebugLog("LOAD", "========== 开始加载广告 ==========");
        sendDebugLog("LOAD", "广告ID: " + adId);
        
        if (adId == null || adId.isEmpty()) {
            sendDebugLog("ERROR", "广告ID不能为空");
            call.reject("广告ID不能为空");
            return;
        }
        
        if (!TTAdSdk.isSdkReady()) {
            sendDebugLog("ERROR", "穿山甲SDK未就绪");
            call.reject("穿山甲SDK未就绪");
            return;
        }
        
        sendDebugLog("LOAD", "穿山甲SDK已就绪");
        
        Activity activity = getActivity();
        if (activity == null) {
            sendDebugLog("ERROR", "Activity 为空");
            call.reject("Activity 为空");
            return;
        }
        
        sendDebugLog("LOAD", "Activity 获取成功");
        
        activity.runOnUiThread(() -> {
            try {
                TTAdManager ttAdManager = TTAdSdk.getAdManager();
                sendDebugLog("LOAD", "获取TTAdManager成功");
                
                mTTAdNative = ttAdManager.createAdNative(activity.getApplicationContext());
                sendDebugLog("LOAD", "创建TTAdNative成功");
                
                AdSlot adSlot = new AdSlot.Builder()
                        .setCodeId(adId)
                        .setAdLoadType(TTAdLoadType.LOAD)
                        .setRewardAmount(1)
                        .setRewardName("金币")
                        .setOrientation(TTAdConstant.VERTICAL)
                        .build();
                
                sendDebugLog("LOAD", "构建AdSlot成功，codeId=" + adId);
                
                mTTAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
                    @Override
                    public void onError(int code, String message) {
                        long duration = System.currentTimeMillis() - mLoadStartTime;
                        sendDebugLog("ERROR", "========== 广告加载失败 ==========");
                        sendDebugLog("ERROR", "广告ID: " + mCurrentAdId);
                        sendDebugLog("ERROR", "错误码: " + code);
                        sendDebugLog("ERROR", "错误信息: " + message);
                        sendDebugLog("ERROR", "耗时: " + duration + "ms");
                        
                        JSObject errorResult = new JSObject();
                        errorResult.put("error", message);
                        errorResult.put("code", code);
                        notifyListeners("onAdFailed", errorResult);
                    }
                    
                    @Override
                    public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                        long duration = System.currentTimeMillis() - mLoadStartTime;
                        sendDebugLog("SUCCESS", "========== 广告加载成功 ==========");
                        sendDebugLog("SUCCESS", "广告ID: " + mCurrentAdId);
                        sendDebugLog("SUCCESS", "耗时: " + duration + "ms");
                        
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
                        
                        JSObject loadedResult = new JSObject();
                        notifyListeners("onAdLoaded", loadedResult);
                    }
                    
                    @Override
                    public void onRewardVideoCached() {
                    }
                    
                    @Override
                    public void onRewardVideoCached(TTRewardVideoAd ad) {
                        sendDebugLog("SUCCESS", "========== 广告缓存成功 ==========");
                        sendDebugLog("SUCCESS", "广告ID: " + mCurrentAdId);
                        
                        mRewardVideoAd = ad;
                        setupAdListener(ad);
                        
                        JSObject cachedResult = new JSObject();
                        notifyListeners("onVideoDownloadSuccess", cachedResult);
                    }
                });
                
                call.resolve();
                
            } catch (Exception e) {
                sendDebugLog("ERROR", "加载广告异常: " + e.getMessage());
                call.reject("加载广告异常: " + e.getMessage());
            }
        });
    }
    
    private void setupAdListener(TTRewardVideoAd ad) {
        if (ad == null) return;
        
        ad.setRewardAdInteractionListener(new TTRewardVideoAd.RewardAdInteractionListener() {
            @Override
            public void onAdShow() {
                sendDebugLog("AD", "========== 广告开始展示 ==========");
                sendDebugLog("AD", "广告ID: " + mCurrentAdId);
                
                extractShowInfo(ad);
                
                notifyListeners("onAdShow", new JSObject());
            }
            
            @Override
            public void onAdVideoBarClick() {
                sendDebugLog("AD", "广告被点击");
                notifyListeners("onAdClick", new JSObject());
            }
            
            @Override
            public void onAdClose() {
                sendDebugLog("AD", "========== 广告关闭 ==========");
                sendDebugLog("AD", "广告ID: " + mCurrentAdId);
                sendDebugLog("AD", "ECPM: " + mLastAdRealEcpm);
                sendDebugLog("AD", "命中子代码位ID: " + mHitSlotId);
                sendDebugLog("AD", "ADN: " + mSdkName);
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
                sendDebugLog("AD", "视频播放完成");
            }
            
            @Override
            public void onVideoError() {
                sendDebugLog("ERROR", "视频播放错误");
                notifyListeners("onVideoDownloadFailed", new JSObject());
            }
            
            @Override
            public void onRewardVerify(boolean rewardVerify, int rewardAmount, String rewardName, int errorCode, String errorMsg) {
                sendDebugLog("REWARD", "onRewardVerify: rewardVerify=" + rewardVerify + ", amount=" + rewardAmount + ", name=" + rewardName);
            }
            
            @Override
            public void onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo) {
                sendDebugLog("REWARD", "========== 获得奖励 ==========");
                sendDebugLog("REWARD", "广告ID: " + mCurrentAdId);
                sendDebugLog("REWARD", "isRewardValid: " + isRewardValid);
                sendDebugLog("REWARD", "rewardType: " + rewardType);
                sendDebugLog("REWARD", "ECPM: " + mLastAdRealEcpm);
                sendDebugLog("REWARD", "命中子代码位ID: " + mHitSlotId);
                sendDebugLog("REWARD", "ADN: " + mSdkName);
                
                if (extraInfo != null) {
                    sendDebugLog("REWARD", "extraInfo: rewardAmount=" + extraInfo.getInt("reward_amount", 0) + ", rewardName=" + extraInfo.getString("reward_name", ""));
                }
                
                JSObject result = new JSObject();
                result.put("rewardVerify", isRewardValid);
                
                if (extraInfo != null) {
                    result.put("rewardAmount", extraInfo.getInt("reward_amount", 1));
                    result.put("rewardName", extraInfo.getString("reward_name", "金币"));
                }
                
                result.put("ecpm", mLastAdRealEcpm);
                result.put("slotId", mHitSlotId);
                result.put("adn", mSdkName);
                sendDebugLog("REWARD", "下发给JS的ECPM: " + mLastAdRealEcpm);
                
                notifyListeners("onRewardVerify", result);
                
                if (pendingShowCall != null) {
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mLastAdRealEcpm = 0.0;
                mHitSlotId = "";
                mSdkName = "";
                sendDebugLog("REWARD", "ECPM缓存已清空");
            }
            
            @Override
            public void onSkippedVideo() {
                sendDebugLog("AD", "广告被跳过");
            }
        });
        
        ad.setDownloadListener(new TTAppDownloadListener() {
            @Override
            public void onIdle() {
            }
            
            @Override
            public void onDownloadActive(long totalBytes, long currBytes, String fileName, String appName) {
                sendDebugLog("DOWNLOAD", "下载中: " + currBytes + "/" + totalBytes + " (" + fileName + ")");
            }
            
            @Override
            public void onDownloadPaused(long totalBytes, long currBytes, String fileName, String appName) {
                sendDebugLog("DOWNLOAD", "下载暂停: " + currBytes + "/" + totalBytes);
            }
            
            @Override
            public void onDownloadFailed(long totalBytes, long currBytes, String fileName, String appName) {
                sendDebugLog("DOWNLOAD", "下载失败: " + currBytes + "/" + totalBytes);
            }
            
            @Override
            public void onDownloadFinished(long totalBytes, String fileName, String appName) {
                sendDebugLog("DOWNLOAD", "下载完成: " + totalBytes + " bytes");
            }
            
            @Override
            public void onInstalled(String fileName, String appName) {
                sendDebugLog("DOWNLOAD", "安装完成: " + fileName);
            }
        });
    }
    
    private void extractShowInfo(TTRewardVideoAd ad) {
        sendDebugLog("ECPM", "=== 开始获取展示信息（show后）===");
        
        try {
            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
            String mmClass = mediationManager != null ? mediationManager.getClass().getName() : "null";
            sendDebugLog("ECPM", "1. mediationManager 获取结果: " + mmClass);
            
            if (mediationManager != null) {
                sendDebugLog("ECPM", "2. 遍历mediationManager的所有方法:");
                java.lang.reflect.Method[] methods = mediationManager.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    String methodName = method.getName();
                    if (methodName.contains("ecpm") || methodName.contains("Ecpm") || 
                        methodName.contains("cpm") || methodName.contains("Cpm") ||
                        methodName.contains("price") || methodName.contains("Price") ||
                        methodName.contains("load") || methodName.contains("Load") ||
                        methodName.contains("show") || methodName.contains("Show") ||
                        methodName.contains("adLoad") || methodName.contains("AdLoad")) {
                        sendDebugLog("ECPM", "   - " + methodName);
                    }
                }
                
                Object showEcpmObj = null;
                try {
                    showEcpmObj = mediationManager.getClass().getMethod("getShowEcpm").invoke(mediationManager);
                    sendDebugLog("ECPM", "3. getShowEcpm() 调用成功");
                } catch (NoSuchMethodException e) {
                    sendDebugLog("ECPM", "3. getShowEcpm() 不存在，尝试其他方法...");
                    try {
                        showEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                        sendDebugLog("ECPM", "3. getLoadEcpm() 调用成功");
                    } catch (NoSuchMethodException e2) {
                        try {
                            showEcpmObj = mediationManager.getClass().getMethod("getEcpm").invoke(mediationManager);
                            sendDebugLog("ECPM", "3. getEcpm() 调用成功");
                        } catch (NoSuchMethodException e3) {
                            sendDebugLog("ECPM_ERROR", "3. 所有ECPM相关方法都不存在");
                        }
                    }
                }
                
                if (showEcpmObj != null) {
                    String ecpmClass = showEcpmObj.getClass().getName();
                    sendDebugLog("ECPM", "4. ECPM对象类型: " + ecpmClass);
                    
                    sendDebugLog("ECPM", "5. 遍历ECPM对象的所有方法:");
                    java.lang.reflect.Method[] ecpmMethods = showEcpmObj.getClass().getMethods();
                    for (java.lang.reflect.Method method : ecpmMethods) {
                        String methodName = method.getName();
                        if (methodName.contains("get") && !methodName.equals("getClass")) {
                            sendDebugLog("ECPM", "   - " + methodName);
                        }
                    }
                    
                    try {
                        Object ecpmVal = showEcpmObj.getClass().getMethod("getEcpm").invoke(showEcpmObj);
                        String ecpmStr = ecpmVal != null ? ecpmVal.toString() : "null";
                        String ecpmType = ecpmVal != null ? ecpmVal.getClass().getName() : "null";
                        sendDebugLog("ECPM", "6. getEcpm() 结果: " + ecpmStr + ", 类型: " + ecpmType);
                        
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
                            // 文档说明：单位是分，转换成元
                            mLastAdRealEcpm = mLastAdRealEcpm / 100.0;
                            sendDebugLog("ECPM", "7. ECPM值(分→元): " + mLastAdRealEcpm);
                        }
                    } catch (Exception e) {
                        sendDebugLog("ECPM_ERROR", "6. 获取Ecpm值失败: " + e.getMessage());
                    }
                    
                    try {
                        Object sdkName = showEcpmObj.getClass().getMethod("getSdkName").invoke(showEcpmObj);
                        mSdkName = sdkName != null ? sdkName.toString() : "";
                        sendDebugLog("ECPM", "8. SDK名称: " + mSdkName);
                    } catch (Exception e) {
                        sendDebugLog("ECPM_ERROR", "8. 获取SDK名称失败: " + e.getMessage());
                    }
                    
                    try {
                        Object slotId = showEcpmObj.getClass().getMethod("getSlotId").invoke(showEcpmObj);
                        mHitSlotId = slotId != null ? slotId.toString() : "";
                        sendDebugLog("ECPM", "9. 子代码位ID: " + mHitSlotId);
                    } catch (Exception e) {
                        sendDebugLog("ECPM_ERROR", "9. 获取子代码位ID失败: " + e.getMessage());
                    }
                    
                    try {
                        Object levelTag = showEcpmObj.getClass().getMethod("getLevelTag").invoke(showEcpmObj);
                        sendDebugLog("ECPM", "10. 阶梯价标签: " + (levelTag != null ? levelTag.toString() : "null"));
                    } catch (Exception e) {
                        sendDebugLog("ECPM", "10. 获取阶梯价标签失败: " + e.getMessage());
                    }
                }
                
                sendDebugLog("ECPM", "=== 尝试getAdLoadInfo ===");
                try {
                    Object adLoadInfoList = mediationManager.getClass().getMethod("getAdLoadInfo").invoke(mediationManager);
                    if (adLoadInfoList != null) {
                        String listClass = adLoadInfoList.getClass().getName();
                        sendDebugLog("ECPM", "   adLoadInfoList 获取成功: " + listClass);
                        
                        java.lang.reflect.Method sizeMethod = adLoadInfoList.getClass().getMethod("size");
                        int size = (int) sizeMethod.invoke(adLoadInfoList);
                        sendDebugLog("ECPM", "   adLoadInfoList 大小: " + size);
                        
                        java.lang.reflect.Method getMethod = adLoadInfoList.getClass().getMethod("get", int.class);
                        for (int i = 0; i < size; i++) {
                            Object infoItem = getMethod.invoke(adLoadInfoList, i);
                            if (infoItem != null) {
                                boolean success = false;
                                try {
                                    success = (boolean) infoItem.getClass().getMethod("isSuccess").invoke(infoItem);
                                } catch (Exception e) {
                                    sendDebugLog("ECPM", "   [" + i + "] isSuccess方法不存在");
                                }
                                sendDebugLog("ECPM", "   [" + i + "] isSuccess: " + success);
                                
                                if (success) {
                                    try {
                                        Object hitId = infoItem.getClass().getMethod("getAdnRitId").invoke(infoItem);
                                        sendDebugLog("ECPM", "   [" + i + "] 命中子代码位ID(getAdnRitId): " + hitId);
                                    } catch (Exception e) {
                                        try {
                                            Object hitId = infoItem.getClass().getMethod("getAdnSlotId").invoke(infoItem);
                                            sendDebugLog("ECPM", "   [" + i + "] 命中子代码位ID(getAdnSlotId): " + hitId);
                                        } catch (Exception e2) {
                                            try {
                                                Object hitId = infoItem.getClass().getMethod("getSlotId").invoke(infoItem);
                                                sendDebugLog("ECPM", "   [" + i + "] 命中子代码位ID(getSlotId): " + hitId);
                                            } catch (Exception e3) {
                                                sendDebugLog("ECPM_ERROR", "   [" + i + "] 获取子代码位ID失败");
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    sendDebugLog("ECPM_ERROR", "getAdLoadInfo调用失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            sendDebugLog("ECPM_ERROR", "获取展示信息失败: " + e.getMessage());
        }
        
        sendDebugLog("ECPM", "=== 获取展示信息结束 ===");
        sendDebugLog("ECPM", "最终ECPM值: " + mLastAdRealEcpm);
        sendDebugLog("ECPM", "命中子代码位ID: " + mHitSlotId);
        sendDebugLog("ECPM", "ADN: " + mSdkName);
    }
    
    @PluginMethod
    public void showRewardVideoAd(PluginCall call) {
        sendDebugLog("SHOW", "========== 开始显示广告 ==========");
        sendDebugLog("SHOW", "广告ID: " + mCurrentAdId);
        
        Activity activity = getActivity();
        if (activity == null) {
            sendDebugLog("ERROR", "Activity 为空");
            call.reject("Activity 为空");
            return;
        }
        
        if (mRewardVideoAd == null) {
            sendDebugLog("ERROR", "广告未加载");
            call.reject("广告未加载");
            return;
        }
        
        sendDebugLog("SHOW", "广告已就绪，准备显示");
        
        activity.runOnUiThread(() -> {
            try {
                pendingShowCall = call;
                mRewardVideoAd.showRewardVideoAd(activity);
                sendDebugLog("SHOW", "广告显示命令已发送");
            } catch (Exception e) {
                sendDebugLog("ERROR", "展示广告异常: " + e.getMessage());
                call.reject("展示广告异常: " + e.getMessage());
                pendingShowCall = null;
                mRewardVideoAd = null;
            }
        });
    }
    
    @PluginMethod
    public void isReady(PluginCall call) {
        boolean ready = mRewardVideoAd != null;
        sendDebugLog("STATUS", "isReady: " + ready);
        JSObject result = new JSObject();
        result.put("ready", ready);
        call.resolve(result);
    }
    
    @PluginMethod
    public void isSdkReady(PluginCall call) {
        boolean ready = TTAdSdk.isSdkReady();
        sendDebugLog("STATUS", "isSdkReady: " + ready);
        JSObject result = new JSObject();
        result.put("ready", ready);
        call.resolve(result);
    }
}