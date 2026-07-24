package com.gaoqianleme.app;

import android.app.Activity;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.baidu.mobads.sdk.api.RewardVideoAd;

@CapacitorPlugin(name = "BaiduAd")
public class BaiduAdPlugin extends Plugin {
    
    private static final String TAG = "BaiduAdPlugin";
    private RewardVideoAd mRewardVideoAd;
    private PluginCall pendingShowCall;
    
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
        
        activity.runOnUiThread(() -> {
            try {
                mRewardVideoAd = new RewardVideoAd(activity, adId, new RewardVideoAd.RewardVideoAdListener() {
                    @Override
                    public void onAdLoaded() {
                        Log.d(TAG, "广告加载成功");
                        if (mRewardVideoAd != null) {
                            String ecpmLevel = mRewardVideoAd.getECPMLevel();
                            Log.d(TAG, "ECPM Level: " + ecpmLevel);
                            Log.d(TAG, "ECPM Level is null: " + (ecpmLevel == null));
                            if (ecpmLevel != null) {
                                Log.d(TAG, "ECPM Level length: " + ecpmLevel.length());
                                Log.d(TAG, "ECPM Level isEmpty: " + ecpmLevel.isEmpty());
                                Log.d(TAG, "ECPM Level trim: '" + ecpmLevel.trim() + "'");
                                try {
                                    double ecpmNum = Double.parseDouble(ecpmLevel);
                                    Log.d(TAG, "ECPM Level 可转换为数字: " + ecpmNum);
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "ECPM Level 不可转换为数字: " + ecpmLevel);
                                }
                            }
                            Log.d(TAG, "Is Ready: " + mRewardVideoAd.isReady());
                        }
                        notifyListeners("onAdLoaded", new JSObject());
                    }
                    
                    @Override
                    public void onAdShow() {
                        Log.d(TAG, "广告展示");
                        notifyListeners("onAdShow", new JSObject());
                    }
                    
                    @Override
                    public void onAdClick() {
                        Log.d(TAG, "广告点击");
                        notifyListeners("onAdClick", new JSObject());
                    }
                    
                    @Override
                    public void onAdClose(float playScale) {
                        Log.d(TAG, "广告关闭，播放比例: " + playScale);
                        notifyListeners("onAdClose", new JSObject());
                        
                        // 如果onRewardVerify没有被触发，广告关闭时resolve pendingShowCall
                        if (pendingShowCall != null) {
                            Log.d(TAG, "广告关闭时resolve pendingShowCall");
                            JSObject result = new JSObject();
                            result.put("rewardVerify", true);
                            result.put("ecpm", 0);
                            pendingShowCall.resolve(result);
                            pendingShowCall = null;
                        }
                    }
                    
                    @Override
                    public void onAdFailed(String error) {
                        Log.e(TAG, "广告加载失败: " + error);
                        notifyListeners("onAdFailed", new JSObject().put("error", error));
                    }
                    
                    @Override
                    public void onVideoDownloadSuccess() {
                        Log.d(TAG, "视频下载成功");
                        notifyListeners("onVideoDownloadSuccess", new JSObject());
                    }
                    
                    @Override
                    public void onVideoDownloadFailed() {
                        Log.e(TAG, "视频下载失败");
                        notifyListeners("onVideoDownloadFailed", new JSObject());
                    }
                    
                    @Override
                    public void playCompletion() {
                        Log.d(TAG, "播放完成");
                    }
                    
                    @Override
                    public void onRewardVerify(boolean rewardVerify, java.util.Map<String, Object> rewardInfo) {
                        Log.d(TAG, "获得奖励: " + rewardVerify);
                        Log.d(TAG, "奖励信息: " + rewardInfo);
                        
                        JSObject result = new JSObject();
                        result.put("rewardVerify", rewardVerify);
                        
                        // 添加rewardInfo的所有字段到结果中
                        if (rewardInfo != null) {
                            for (String key : rewardInfo.keySet()) {
                                result.put(key, rewardInfo.get(key));
                            }
                        }
                        
                        // 获取ECPM
                        double ecpmValue = 0;
                        if (mRewardVideoAd != null) {
                            String ecpmLevel = mRewardVideoAd.getECPMLevel();
                            Log.d(TAG, "ECPM Level: " + ecpmLevel);
                            Log.d(TAG, "ECPM Level is null: " + (ecpmLevel == null));
                            if (ecpmLevel != null) {
                                Log.d(TAG, "ECPM Level length: " + ecpmLevel.length());
                                Log.d(TAG, "ECPM Level isEmpty: " + ecpmLevel.isEmpty());
                                Log.d(TAG, "ECPM Level trim: '" + ecpmLevel.trim() + "'");
                            }
                            
                            try {
                                if (ecpmLevel != null && !ecpmLevel.isEmpty()) {
                                    ecpmValue = Double.parseDouble(ecpmLevel);
                                    Log.d(TAG, "ECPM Level 转换成功: " + ecpmValue);
                                } else {
                                    Log.d(TAG, "ECPM Level 为空或null，跳过转换");
                                }
                            } catch (NumberFormatException e) {
                                Log.w(TAG, "ECPM Level 转换失败: " + ecpmLevel);
                                Log.w(TAG, "转换异常信息: " + e.getMessage());
                                // 如果ecpmLevel不是数字，尝试从rewardInfo中获取
                                if (rewardInfo != null) {
                                    Log.d(TAG, "rewardInfo keys: " + rewardInfo.keySet());
                                    if (rewardInfo.containsKey("ecpm")) {
                                        Object ecpmObj = rewardInfo.get("ecpm");
                                        Log.d(TAG, "rewardInfo.ecpm value: " + ecpmObj);
                                        Log.d(TAG, "rewardInfo.ecpm type: " + (ecpmObj != null ? ecpmObj.getClass().getName() : "null"));
                                        if (ecpmObj instanceof Number) {
                                            ecpmValue = ((Number) ecpmObj).doubleValue();
                                            Log.d(TAG, "从rewardInfo获取ecpm(Number): " + ecpmValue);
                                        } else if (ecpmObj instanceof String) {
                                            try {
                                                ecpmValue = Double.parseDouble((String) ecpmObj);
                                                Log.d(TAG, "从rewardInfo获取ecpm(String): " + ecpmValue);
                                            } catch (NumberFormatException e2) {
                                                Log.w(TAG, "从rewardInfo获取ecpm失败: " + ecpmObj);
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "rewardInfo中不包含ecpm字段");
                                    }
                                } else {
                                    Log.d(TAG, "rewardInfo为空");
                                }
                            }
                        } else {
                            Log.d(TAG, "mRewardVideoAd为空，无法获取ECPM Level");
                        }
                        
                        result.put("ecpm", ecpmValue);
                        Log.d(TAG, "最终返回的ECPM: " + ecpmValue);
                        
                        notifyListeners("onRewardVerify", result);
                        
                        if (pendingShowCall != null) {
                            pendingShowCall.resolve(result);
                            pendingShowCall = null;
                        }
                    }
                    
                    @Override
                    public void onAdSkip(float playScale) {
                        Log.d(TAG, "广告跳过，播放比例: " + playScale);
                    }
                });
                
                // 加载广告
                mRewardVideoAd.load();
                call.resolve();
                
            } catch (Exception e) {
                Log.e(TAG, "加载广告异常: " + e.getMessage(), e);
                call.reject("加载广告异常: " + e.getMessage());
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
                if (mRewardVideoAd.isReady()) {
                    pendingShowCall = call;
                    mRewardVideoAd.show();
                } else {
                    call.reject("广告未准备好");
                }
            } catch (Exception e) {
                Log.e(TAG, "展示广告异常: " + e.getMessage(), e);
                call.reject("展示广告异常: " + e.getMessage());
            }
        });
    }
    
    @PluginMethod
    public void isReady(PluginCall call) {
        JSObject result = new JSObject();
        result.put("ready", mRewardVideoAd != null && mRewardVideoAd.isReady());
        call.resolve(result);
    }
}
