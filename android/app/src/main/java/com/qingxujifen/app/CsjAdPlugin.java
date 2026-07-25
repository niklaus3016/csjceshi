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
    
    private void sendDebugLog(String tag, String message) {
        Log.d(TAG, "[" + tag + "] " + message);
        JSObject logData = new JSObject();
        logData.put("tag", tag);
        logData.put("message", message);
        logData.put("timestamp", System.currentTimeMillis());
        notifyListeners("onCsjDebugLog", logData);
    }
    
    private TTAdInteractionListener mInteractionListener = new TTAdInteractionListener() {
        @Override
        public void onAdEvent(int code, Map map) {
            if (map == null) {
                return;
            }
            switch (code) {
                case TTAdConstant.AD_EVENT_AUTH_DOUYIN:
                    String uid = (String) map.get("open_uid");
                    sendDebugLog("AUTH", "授权成功 --> uid：" + uid);
                    break;
                case TTAdConstant.AD_EVENT_EXCHANGE_COUPON_FINISH:
                    String isSuccess = String.valueOf(map.get("isSuccess"));
                    sendDebugLog("COUPON", "兑换结果：" + isSuccess);
                    break;
            }
        }
    };
    
    @PluginMethod
    public void loadRewardVideoAd(PluginCall call) {
        String adId = call.getString("adId");
        mCurrentAdId = adId;
        mLoadStartTime = System.currentTimeMillis();
        
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
                        mLastAdRealEcpm = 0.0;
                        
                        JSObject debugInfo = new JSObject();
                        debugInfo.put("step", "load");
                        debugInfo.put("adId", mCurrentAdId);
                        debugInfo.put("duration", duration);
                        
                        try {
                            sendDebugLog("ECPM", "=== 开始反射获取ECPM ===");
                            
                            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
                            String mmClass = mediationManager != null ? mediationManager.getClass().getName() : "null";
                            sendDebugLog("ECPM", "1. mediationManager 获取结果: " + mmClass);
                            debugInfo.put("mediationManager", mmClass);
                            
                            if (mediationManager != null) {
                                sendDebugLog("ECPM", "2. 遍历mediationManager的所有方法:");
                                java.lang.reflect.Method[] methods = mediationManager.getClass().getMethods();
                                for (java.lang.reflect.Method method : methods) {
                                    String methodName = method.getName();
                                    if (methodName.contains("ecpm") || methodName.contains("Ecpm") || 
                                        methodName.contains("cpm") || methodName.contains("Cpm") ||
                                        methodName.contains("price") || methodName.contains("Price") ||
                                        methodName.contains("load") || methodName.contains("Load") ||
                                        methodName.contains("show") || methodName.contains("Show")) {
                                        sendDebugLog("ECPM", "   - " + methodName);
                                    }
                                }
                                
                                Object loadEcpmObj = null;
                                try {
                                    loadEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                                } catch (NoSuchMethodException e) {
                                    sendDebugLog("ECPM", "   getLoadEcpm() 不存在，尝试其他方法...");
                                    try {
                                        loadEcpmObj = mediationManager.getClass().getMethod("getEcpm").invoke(mediationManager);
                                        sendDebugLog("ECPM", "   getEcpm() 调用成功");
                                    } catch (NoSuchMethodException e2) {
                                        try {
                                            loadEcpmObj = mediationManager.getClass().getMethod("getAdEcpm").invoke(mediationManager);
                                            sendDebugLog("ECPM", "   getAdEcpm() 调用成功");
                                        } catch (NoSuchMethodException e3) {
                                            try {
                                                loadEcpmObj = mediationManager.getClass().getMethod("getShowEcpm").invoke(mediationManager);
                                                sendDebugLog("ECPM", "   getShowEcpm() 调用成功");
                                            } catch (NoSuchMethodException e4) {
                                                sendDebugLog("ECPM_ERROR", "所有ECPM相关方法都不存在");
                                            }
                                        }
                                    }
                                }
                                
                                String leClass = loadEcpmObj != null ? loadEcpmObj.getClass().getName() : "null";
                                sendDebugLog("ECPM", "3. ECPM对象获取结果: " + leClass);
                                debugInfo.put("loadEcpmObj", leClass);
                                
                                if (loadEcpmObj != null) {
                                    Object ecpmObj = loadEcpmObj.getClass().getMethod("getEcpm").invoke(loadEcpmObj);
                                    String ecpmStr = ecpmObj != null ? ecpmObj.toString() : "null";
                                    String ecpmType = ecpmObj != null ? ecpmObj.getClass().getName() : "null";
                                    sendDebugLog("ECPM", "3. ecpmObj 获取结果: " + ecpmStr + ", 类型: " + ecpmType);
                                    debugInfo.put("ecpmObj", ecpmStr);
                                    debugInfo.put("ecpmType", ecpmType);
                                    
                                    if (ecpmObj != null) {
                                        if (ecpmObj instanceof Double) {
                                            mLastAdRealEcpm = (Double) ecpmObj;
                                            sendDebugLog("ECPM", "4. 类型转换: Double -> " + mLastAdRealEcpm);
                                        } else if (ecpmObj instanceof Integer) {
                                            mLastAdRealEcpm = ((Integer) ecpmObj).doubleValue();
                                            sendDebugLog("ECPM", "4. 类型转换: Integer -> " + mLastAdRealEcpm);
                                        } else if (ecpmObj instanceof Long) {
                                            mLastAdRealEcpm = ((Long) ecpmObj).doubleValue();
                                            sendDebugLog("ECPM", "4. 类型转换: Long -> " + mLastAdRealEcpm);
                                        } else if (ecpmObj instanceof Float) {
                                            mLastAdRealEcpm = ((Float) ecpmObj).doubleValue();
                                            sendDebugLog("ECPM", "4. 类型转换: Float -> " + mLastAdRealEcpm);
                                        } else {
                                            mLastAdRealEcpm = Double.parseDouble(ecpmObj.toString());
                                            sendDebugLog("ECPM", "4. 类型转换: String -> " + mLastAdRealEcpm);
                                        }
                                    }
                                }
                                
                                sendDebugLog("ECPM", "5. 尝试获取子代码位信息 getAdLoadInfo()");
                                try {
                                    Object adLoadInfoList = mediationManager.getClass().getMethod("getAdLoadInfo").invoke(mediationManager);
                                    if (adLoadInfoList != null) {
                                        String listClass = adLoadInfoList.getClass().getName();
                                        sendDebugLog("ECPM", "   adLoadInfoList 获取成功: " + listClass);
                                        debugInfo.put("adLoadInfoList", listClass);
                                        
                                        java.lang.reflect.Method sizeMethod = adLoadInfoList.getClass().getMethod("size");
                                        int size = (int) sizeMethod.invoke(adLoadInfoList);
                                        sendDebugLog("ECPM", "   adLoadInfoList 大小: " + size);
                                        
                                        if (size > 0) {
                                            java.lang.reflect.Method getMethod = adLoadInfoList.getClass().getMethod("get", int.class);
                                            Object firstItem = getMethod.invoke(adLoadInfoList, 0);
                                            if (firstItem != null) {
                                                String itemClass = firstItem.getClass().getName();
                                                sendDebugLog("ECPM", "   第一个元素类型: " + itemClass);
                                                
                                                java.lang.reflect.Method[] itemMethods = firstItem.getClass().getMethods();
                                                sendDebugLog("ECPM", "   第一个元素的方法列表:");
                                                for (java.lang.reflect.Method itemMethod : itemMethods) {
                                                    String methodName = itemMethod.getName();
                                                    if (methodName.contains("code") || methodName.contains("Code") ||
                                                        methodName.contains("id") || methodName.contains("Id") ||
                                                        methodName.contains("ecpm") || methodName.contains("Ecpm") ||
                                                        methodName.contains("adn") || methodName.contains("Adn") ||
                                                        methodName.contains("price") || methodName.contains("Price")) {
                                                        sendDebugLog("ECPM", "     - " + methodName);
                                                    }
                                                }
                                                
                                                try {
                                                    Object subCodeId = firstItem.getClass().getMethod("getAdnSlotId").invoke(firstItem);
                                                    sendDebugLog("ECPM", "   子代码位ID: " + subCodeId);
                                                    debugInfo.put("subCodeId", subCodeId != null ? subCodeId.toString() : "null");
                                                } catch (NoSuchMethodException e) {
                                                    try {
                                                        Object subCodeId = firstItem.getClass().getMethod("getSlotId").invoke(firstItem);
                                                        sendDebugLog("ECPM", "   子代码位ID(getSlotId): " + subCodeId);
                                                        debugInfo.put("subCodeId", subCodeId != null ? subCodeId.toString() : "null");
                                                    } catch (NoSuchMethodException e2) {
                                                        sendDebugLog("ECPM_ERROR", "   获取子代码位ID失败，方法不存在");
                                                    }
                                                }
                                                
                                                try {
                                                    Object ecpmInfo = firstItem.getClass().getMethod("getMediationAdEcpmInfo").invoke(firstItem);
                                                    if (ecpmInfo != null) {
                                                        try {
                                                            Object ecpmVal = ecpmInfo.getClass().getMethod("getEcpm").invoke(ecpmInfo);
                                                            sendDebugLog("ECPM", "   通过AdLoadInfo获取ECPM: " + ecpmVal);
                                                            if (ecpmVal != null) {
                                                                if (ecpmVal instanceof Double) {
                                                                    mLastAdRealEcpm = (Double) ecpmVal;
                                                                } else if (ecpmVal instanceof Integer) {
                                                                    mLastAdRealEcpm = ((Integer) ecpmVal).doubleValue();
                                                                } else if (ecpmVal instanceof Long) {
                                                                    mLastAdRealEcpm = ((Long) ecpmVal).doubleValue();
                                                                } else {
                                                                    mLastAdRealEcpm = Double.parseDouble(ecpmVal.toString());
                                                                }
                                                                sendDebugLog("ECPM", "   通过AdLoadInfo更新ECPM值: " + mLastAdRealEcpm);
                                                            }
                                                        } catch (Exception e) {
                                                            sendDebugLog("ECPM_ERROR", "   获取Ecpm值失败: " + e.getMessage());
                                                        }
                                                    }
                                                } catch (NoSuchMethodException e) {
                                                    sendDebugLog("ECPM_ERROR", "   getMediationAdEcpmInfo方法不存在");
                                                }
                                            }
                                        }
                                    } else {
                                        sendDebugLog("ECPM", "   adLoadInfoList 为null");
                                    }
                                } catch (NoSuchMethodException e) {
                                    sendDebugLog("ECPM_ERROR", "   getAdLoadInfo方法不存在");
                                } catch (Exception e) {
                                    sendDebugLog("ECPM_ERROR", "   getAdLoadInfo调用失败: " + e.getMessage());
                                }
                            }
                        } catch (NoSuchMethodException e) {
                            String errorMsg = "反射失败 - 方法不存在: " + e.getMessage();
                            sendDebugLog("ECPM_ERROR", errorMsg);
                            debugInfo.put("error", errorMsg);
                            mLastAdRealEcpm = 0.0;
                        } catch (IllegalAccessException e) {
                            String errorMsg = "反射失败 - 访问权限: " + e.getMessage();
                            sendDebugLog("ECPM_ERROR", errorMsg);
                            debugInfo.put("error", errorMsg);
                            mLastAdRealEcpm = 0.0;
                        } catch (Exception e) {
                            String errorMsg = "反射失败 - " + e.getClass().getName() + ": " + e.getMessage();
                            sendDebugLog("ECPM_ERROR", errorMsg);
                            debugInfo.put("error", errorMsg);
                            mLastAdRealEcpm = 0.0;
                        }
                        
                        sendDebugLog("ECPM", "=== 反射结束 ===");
                        sendDebugLog("ECPM", "最终ECPM值: " + mLastAdRealEcpm);
                        debugInfo.put("ecpm", mLastAdRealEcpm);
                        
                        setupAdListener(ad);
                        
                        JSObject loadedResult = new JSObject();
                        loadedResult.put("debug", debugInfo);
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
                        mLastAdRealEcpm = 0.0;
                        
                        JSObject debugInfo = new JSObject();
                        debugInfo.put("step", "cache");
                        debugInfo.put("adId", mCurrentAdId);
                        
                        try {
                            sendDebugLog("ECPM", "=== 开始反射获取ECPM (缓存) ===");
                            
                            Object mediationManager = ad.getClass().getMethod("getMediationManager").invoke(ad);
                            String mmClass = mediationManager != null ? mediationManager.getClass().getName() : "null";
                            sendDebugLog("ECPM", "1. mediationManager 获取结果: " + mmClass);
                            debugInfo.put("mediationManager", mmClass);
                            
                            if (mediationManager != null) {
                                sendDebugLog("ECPM", "2. 遍历mediationManager的所有方法:");
                                java.lang.reflect.Method[] methods = mediationManager.getClass().getMethods();
                                for (java.lang.reflect.Method method : methods) {
                                    String methodName = method.getName();
                                    if (methodName.contains("ecpm") || methodName.contains("Ecpm") || 
                                        methodName.contains("cpm") || methodName.contains("Cpm") ||
                                        methodName.contains("price") || methodName.contains("Price") ||
                                        methodName.contains("load") || methodName.contains("Load") ||
                                        methodName.contains("show") || methodName.contains("Show")) {
                                        sendDebugLog("ECPM", "   - " + methodName);
                                    }
                                }
                                
                                Object loadEcpmObj = null;
                                try {
                                    loadEcpmObj = mediationManager.getClass().getMethod("getLoadEcpm").invoke(mediationManager);
                                } catch (NoSuchMethodException e) {
                                    sendDebugLog("ECPM", "   getLoadEcpm() 不存在，尝试其他方法...");
                                    try {
                                        loadEcpmObj = mediationManager.getClass().getMethod("getEcpm").invoke(mediationManager);
                                        sendDebugLog("ECPM", "   getEcpm() 调用成功");
                                    } catch (NoSuchMethodException e2) {
                                        try {
                                            loadEcpmObj = mediationManager.getClass().getMethod("getAdEcpm").invoke(mediationManager);
                                            sendDebugLog("ECPM", "   getAdEcpm() 调用成功");
                                        } catch (NoSuchMethodException e3) {
                                            try {
                                                loadEcpmObj = mediationManager.getClass().getMethod("getShowEcpm").invoke(mediationManager);
                                                sendDebugLog("ECPM", "   getShowEcpm() 调用成功");
                                            } catch (NoSuchMethodException e4) {
                                                sendDebugLog("ECPM_ERROR", "所有ECPM相关方法都不存在");
                                            }
                                        }
                                    }
                                }
                                
                                String leClass = loadEcpmObj != null ? loadEcpmObj.getClass().getName() : "null";
                                sendDebugLog("ECPM", "3. ECPM对象获取结果: " + leClass);
                                debugInfo.put("loadEcpmObj", leClass);
                                
                                if (loadEcpmObj != null) {
                                    Object ecpmObj = loadEcpmObj.getClass().getMethod("getEcpm").invoke(loadEcpmObj);
                                    String ecpmStr = ecpmObj != null ? ecpmObj.toString() : "null";
                                    String ecpmType = ecpmObj != null ? ecpmObj.getClass().getName() : "null";
                                    sendDebugLog("ECPM", "4. ecpmObj 获取结果: " + ecpmStr + ", 类型: " + ecpmType);
                                    debugInfo.put("ecpmObj", ecpmStr);
                                    debugInfo.put("ecpmType", ecpmType);
                                    
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
                                }
                                
                                sendDebugLog("ECPM", "5. 尝试获取子代码位信息 getAdLoadInfo()");
                                try {
                                    Object adLoadInfoList = mediationManager.getClass().getMethod("getAdLoadInfo").invoke(mediationManager);
                                    if (adLoadInfoList != null) {
                                        String listClass = adLoadInfoList.getClass().getName();
                                        sendDebugLog("ECPM", "   adLoadInfoList 获取成功: " + listClass);
                                        debugInfo.put("adLoadInfoList", listClass);
                                        
                                        java.lang.reflect.Method sizeMethod = adLoadInfoList.getClass().getMethod("size");
                                        int size = (int) sizeMethod.invoke(adLoadInfoList);
                                        sendDebugLog("ECPM", "   adLoadInfoList 大小: " + size);
                                        
                                        if (size > 0) {
                                            java.lang.reflect.Method getMethod = adLoadInfoList.getClass().getMethod("get", int.class);
                                            Object firstItem = getMethod.invoke(adLoadInfoList, 0);
                                            if (firstItem != null) {
                                                String itemClass = firstItem.getClass().getName();
                                                sendDebugLog("ECPM", "   第一个元素类型: " + itemClass);
                                                
                                                java.lang.reflect.Method[] itemMethods = firstItem.getClass().getMethods();
                                                sendDebugLog("ECPM", "   第一个元素的方法列表:");
                                                for (java.lang.reflect.Method itemMethod : itemMethods) {
                                                    String methodName = itemMethod.getName();
                                                    if (methodName.contains("code") || methodName.contains("Code") ||
                                                        methodName.contains("id") || methodName.contains("Id") ||
                                                        methodName.contains("ecpm") || methodName.contains("Ecpm") ||
                                                        methodName.contains("adn") || methodName.contains("Adn") ||
                                                        methodName.contains("price") || methodName.contains("Price")) {
                                                        sendDebugLog("ECPM", "     - " + methodName);
                                                    }
                                                }
                                                
                                                try {
                                                    Object subCodeId = firstItem.getClass().getMethod("getAdnSlotId").invoke(firstItem);
                                                    sendDebugLog("ECPM", "   子代码位ID: " + subCodeId);
                                                    debugInfo.put("subCodeId", subCodeId != null ? subCodeId.toString() : "null");
                                                } catch (NoSuchMethodException e) {
                                                    try {
                                                        Object subCodeId = firstItem.getClass().getMethod("getSlotId").invoke(firstItem);
                                                        sendDebugLog("ECPM", "   子代码位ID(getSlotId): " + subCodeId);
                                                        debugInfo.put("subCodeId", subCodeId != null ? subCodeId.toString() : "null");
                                                    } catch (NoSuchMethodException e2) {
                                                        sendDebugLog("ECPM_ERROR", "   获取子代码位ID失败，方法不存在");
                                                    }
                                                }
                                                
                                                try {
                                                    Object ecpmInfo = firstItem.getClass().getMethod("getMediationAdEcpmInfo").invoke(firstItem);
                                                    if (ecpmInfo != null) {
                                                        try {
                                                            Object ecpmVal = ecpmInfo.getClass().getMethod("getEcpm").invoke(ecpmInfo);
                                                            sendDebugLog("ECPM", "   通过AdLoadInfo获取ECPM: " + ecpmVal);
                                                            if (ecpmVal != null) {
                                                                if (ecpmVal instanceof Double) {
                                                                    mLastAdRealEcpm = (Double) ecpmVal;
                                                                } else if (ecpmVal instanceof Integer) {
                                                                    mLastAdRealEcpm = ((Integer) ecpmVal).doubleValue();
                                                                } else if (ecpmVal instanceof Long) {
                                                                    mLastAdRealEcpm = ((Long) ecpmVal).doubleValue();
                                                                } else {
                                                                    mLastAdRealEcpm = Double.parseDouble(ecpmVal.toString());
                                                                }
                                                                sendDebugLog("ECPM", "   通过AdLoadInfo更新ECPM值: " + mLastAdRealEcpm);
                                                            }
                                                        } catch (Exception e) {
                                                            sendDebugLog("ECPM_ERROR", "   获取Ecpm值失败: " + e.getMessage());
                                                        }
                                                    }
                                                } catch (NoSuchMethodException e) {
                                                    sendDebugLog("ECPM_ERROR", "   getMediationAdEcpmInfo方法不存在");
                                                }
                                            }
                                        }
                                    } else {
                                        sendDebugLog("ECPM", "   adLoadInfoList 为null");
                                    }
                                } catch (NoSuchMethodException e) {
                                    sendDebugLog("ECPM_ERROR", "   getAdLoadInfo方法不存在");
                                } catch (Exception e) {
                                    sendDebugLog("ECPM_ERROR", "   getAdLoadInfo调用失败: " + e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            sendDebugLog("ECPM_ERROR", "反射失败(缓存): " + e.getMessage());
                            debugInfo.put("error", e.getMessage());
                            mLastAdRealEcpm = 0.0;
                        }
                        
                        sendDebugLog("ECPM", "缓存后ECPM值: " + mLastAdRealEcpm);
                        debugInfo.put("ecpm", mLastAdRealEcpm);
                        
                        setupAdListener(ad);
                        
                        JSObject cachedResult = new JSObject();
                        cachedResult.put("debug", debugInfo);
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
                sendDebugLog("AD", "当前缓存的ECPM: " + mLastAdRealEcpm);
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
                sendDebugLog("REWARD", "当前缓存的ECPM: " + mLastAdRealEcpm);
                
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
                sendDebugLog("REWARD", "下发给JS的ECPM: " + mLastAdRealEcpm);
                
                notifyListeners("onRewardVerify", result);
                
                if (pendingShowCall != null) {
                    pendingShowCall.resolve(result);
                    pendingShowCall = null;
                }
                
                mLastAdRealEcpm = 0.0;
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
                mRewardVideoAd.setAdInteractionListener(mInteractionListener);
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