package com.union_test.toutiao.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdInteractionListener;
import com.bytedance.sdk.openadsdk.TTAdLoadType;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAppDownloadListener;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.utils.RewardBundleModel;
import com.union_test.toutiao.utils.TToast;

import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Created by bytedance on 2018/2/1.
 * 激励视频接入类
 */

public class RewardVideoActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "RewardVideoActivity";
    private String mHorizontalCodeId;
    private String mVerticalCodeId;


    private TTAdNative mTTAdNative;
    private AdLoadListener mAdLoadListener;

    @SuppressWarnings("RedundantCast")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reward_video);
        getExtraInfo();
        initViews();

        //step1:初始化sdk

        TTAdManager ttAdManager = TTAdManagerHolder.get();
        //step2:(可选，强烈建议在合适的时机调用):申请部分权限
        TTAdManagerHolder.get().requestPermissionIfNecessary(this);
        //step3:创建TTAdNative对象,用于调用广告请求接口

        mTTAdNative = ttAdManager.createAdNative(getApplicationContext());
    }


    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_arv_back:
                finish();
                break;
            case R.id.btn_reward_load:
                // step4: 加载横屏广告
                loadAd(mHorizontalCodeId);
                break;
            case R.id.btn_reward_load_vertical:
                // step4: 加载竖屏广告
                loadAd(mVerticalCodeId);
                break;
            case R.id.btn_reward_show:
                // step7: 触发展示广告
                // 强烈建议在等待onRewardVideoCached回调后，再触发展示广告，提升播放体验
                showAd();
                break;
        }
    }

    /**
     * 加载广告
     */
    private void loadAd(final String codeId) {
        //step5:创建广告请求参数AdSlot

        AdSlot adSlot = new AdSlot.Builder()
                .setCodeId(codeId) // 广告代码位Id
                .setAdLoadType(TTAdLoadType.LOAD) // 本次广告用途：TTAdLoadType.LOAD实时；TTAdLoadType.PRELOAD预请求
                .setRewardAmount(123)
                .setRewardName("金币")
                .build();
        if (mTTAdNative == null) {
            return;
        }
        //step6:注册广告加载生命周期监听，请求广告

        mAdLoadListener = new AdLoadListener(this);

        mTTAdNative.loadRewardVideoAd(adSlot, mAdLoadListener);
    }

    /**
     * 展示广告
     */
    private void showAd() {
        if (mAdLoadListener == null) {
            return;
        }
        mAdLoadListener.showAd();
    }

    /**
     * 【必须】广告加载期间生命周期监听
     */

    private static class AdLoadListener implements TTAdNative.RewardVideoAdListener {

        private final Activity mActivity;

        private TTRewardVideoAd mAd;

        private TTAdInteractionListener mInteractionListener = new TTAdInteractionListener() {
            @Override
            public void onAdEvent(int code, Map map) {
                if (map == null) {
                    return;
                }
                switch (code) {

                    case TTAdConstant.AD_EVENT_AUTH_DOUYIN:
                        // 抖音授权成功状态回调, 媒体可以通过map获取抖音openuid用以判断是否下发奖励
                        String uid = (String) map.get("open_uid");
                        Log.i(TAG, "授权成功 --> uid：" + uid);
                        break;

                    case TTAdConstant.AD_EVENT_EXCHANGE_COUPON_FINISH:
                        // 优惠券兑换回调
                        String isSuccess = String.valueOf(map.get("isSuccess"));
                        Log.i(TAG, "兑换结果：" + isSuccess);
                        break;

                }
            }
        };

        public AdLoadListener(Activity activity) {
            mActivity = activity;
        }

        /**
         * 广告加载过程中出错
         */
        @Override
        public void onError(int code, String message) {
            Log.e(TAG, "Callback --> onError: " + code + ", " + message);
            TToast.show(mActivity, message);
        }

        /**
         * 广告基础信息加载完成，此方法是回调后是广告可调用展示的最早时机
         *
         * @param ad 广告对象 在一次广告生命周期中onRewardVideoAdLoad与onRewardVideoCached回调中的ad是同一个对象
         */
        @Override

        public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
            Log.e(TAG, "Callback --> onRewardVideoAdLoad");

            TToast.show(mActivity, "rewardVideoAd loaded 广告类型：" + getAdType(ad.getRewardVideoAdType()));
            handleAd(ad);
        }

        @Override

        public void onRewardVideoCached() {
            // 已废弃 请使用 onRewardVideoCached(TTRewardVideoAd ad) 方法
        }

        /**
         * 广告基础信息与素材缓存完成，此时调用广告展示流畅，是展示广告的最理想时机
         *
         * @param ad 广告对象 在一次广告生命周期中onRewardVideoAdLoad与onRewardVideoCached回调中的ad是同一个对象
         */
        @Override

        public void onRewardVideoCached(TTRewardVideoAd ad) {
            Log.e(TAG, "Callback --> onRewardVideoCached");

            TToast.show(mActivity, "rewardVideoAd cached 广告类型：" + getAdType(ad.getRewardVideoAdType()));
            handleAd(ad);
        }

        /**
         * 处理广告对象
         */

        public void handleAd(TTRewardVideoAd ad) {
            if (mAd != null) {
                return;
            }
            mAd = ad;
            //【必须】广告展示时的生命周期监听

            mAd.setRewardAdInteractionListener(new AdLifeListener(mActivity));
            //【可选】监听下载状态
            mAd.setDownloadListener(new DownloadStatusListener());
        }

        /**
         * 触发展示广告
         */
        public void showAd() {
            if (mAd == null) {
                TToast.show(mActivity, "当前广告未加载好，请先点击加载广告");
                return;
            }

            mAd.showRewardVideoAd(mActivity);

            /**
             * 注册广告事件监听， 目前只有授权事件定义，后续会扩展
             */
            mAd.setAdInteractionListener(mInteractionListener);
            // 广告使用后应废弃
            mAd = null;
        }
    }

    /**
     * 【必须】广告生命状态监听器
     */

    private static class AdLifeListener implements TTRewardVideoAd.RewardAdInteractionListener {

        private final WeakReference<Context> mContextRef;

        public AdLifeListener(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override

        public void onAdShow() {
            // 广告展示
            Log.d(TAG, "Callback --> rewardVideoAd show");
            TToast.show(mContextRef.get(), "rewardVideoAd show");
        }

        @Override

        public void onAdVideoBarClick() {
            // 广告中产生了点击行为
            Log.d(TAG, "Callback --> rewardVideoAd bar click");
            TToast.show(mContextRef.get(), "rewardVideoAd bar click");
        }

        @Override

        public void onAdClose() {
            // 广告整体关闭
            Log.d(TAG, "Callback --> rewardVideoAd close");
            TToast.show(mContextRef.get(), "rewardVideoAd close");
        }

        //视频播放完成回调
        @Override
        public void onVideoComplete() {
            // 广告素材播放完成，例如视频未跳过，完整的播放了
            Log.d(TAG, "Callback --> rewardVideoAd complete");
            TToast.show(mContextRef.get(), "rewardVideoAd complete");
        }

        @Override
        public void onVideoError() {
            // 广告素材展示时出错
            Log.e(TAG, "Callback --> rewardVideoAd error");
            TToast.show(mContextRef.get(), "rewardVideoAd error");
        }

        @Override

        public void onRewardVerify(boolean rewardVerify, int rewardAmount, String rewardName, int errorCode, String errorMsg) {
            // 已废弃 请使用 onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo)
        }

        @Override

        public void onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo) {
            // 用户的观看行为满足了奖励条件
            RewardBundleModel rewardBundleModel = new RewardBundleModel(extraInfo);
            Log.e(TAG, "Callback --> rewardVideoAd has onRewardArrived " +
                    "\n奖励是否有效：" + isRewardValid +
                    "\n奖励类型：" + rewardType +
                    "\n奖励名称：" + rewardBundleModel.getRewardName() +
                    "\n奖励数量：" + rewardBundleModel.getRewardAmount() +
                    "\n建议奖励百分比：" + rewardBundleModel.getRewardPropose());
            TToast.show(mContextRef.get(), "ad onRewardArrived valid:" + isRewardValid +
                    " type:" + rewardType + " errorCode:" + rewardBundleModel.getServerErrorCode());
            if (!isRewardValid) {
                Log.d(TAG, "发送奖励失败 code：" + rewardBundleModel.getServerErrorCode() +
                        "\n msg：" + rewardBundleModel.getServerErrorMsg());
                return;
            }


            if (rewardType == TTRewardVideoAd.REWARD_TYPE_DEFAULT) {
                Log.d(TAG, "普通奖励发放，name:" + rewardBundleModel.getRewardName() +
                        "\namount:" + rewardBundleModel.getRewardAmount());
            }
        }

        @Override
        public void onSkippedVideo() {
            // 用户在观看素材时点击了跳过
            Log.e(TAG, "Callback --> rewardVideoAd has onSkippedVideo");
            TToast.show(mContextRef.get(), "rewardVideoAd has onSkippedVideo");
        }
    }

    /**
     * 【可选】下载状态监听器
     */
    private static class DownloadStatusListener implements TTAppDownloadListener {

        @Override
        public void onIdle() {
        }

        @Override
        public void onDownloadActive(long totalBytes, long currBytes, String fileName, String appName) {
            Log.d("DML", "onDownloadActive==totalBytes=" + totalBytes + ",currBytes=" + currBytes + ",fileName=" + fileName + ",appName=" + appName);
        }

        @Override
        public void onDownloadPaused(long totalBytes, long currBytes, String fileName, String appName) {
            Log.d("DML", "onDownloadPaused===totalBytes=" + totalBytes + ",currBytes=" + currBytes + ",fileName=" + fileName + ",appName=" + appName);
        }

        @Override
        public void onDownloadFailed(long totalBytes, long currBytes, String fileName, String appName) {
            Log.d("DML", "onDownloadFailed==totalBytes=" + totalBytes + ",currBytes=" + currBytes + ",fileName=" + fileName + ",appName=" + appName);
        }

        @Override
        public void onDownloadFinished(long totalBytes, String fileName, String appName) {
            Log.d("DML", "onDownloadFinished==totalBytes=" + totalBytes + ",fileName=" + fileName + ",appName=" + appName);
        }

        @Override
        public void onInstalled(String fileName, String appName) {
            Log.d("DML", "onInstalled==" + ",fileName=" + fileName + ",appName=" + appName);
        }
    }

    private static String getAdType(int type) {
        switch (type) {

            case TTAdConstant.AD_TYPE_COMMON_VIDEO:
                return "普通激励视频，type=" + type;

            case TTAdConstant.AD_TYPE_PLAYABLE_VIDEO:
                return "Playable激励视频，type=" + type;

            case TTAdConstant.AD_TYPE_PLAYABLE:
                return "纯Playable，type=" + type;

            case TTAdConstant.AD_TYPE_LIVE:
                return "直播流，type=" + type;
        }

        return "未知类型+type=" + type;
    }

    private void getExtraInfo() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        mHorizontalCodeId = intent.getStringExtra("horizontal_rit");
        mVerticalCodeId = intent.getStringExtra("vertical_rit");
    }

    private void initViews() {
        Button loadAd = findViewById(R.id.btn_reward_load);
        Button mLoadAdVertical = findViewById(R.id.btn_reward_load_vertical);
        Button mShowAd = findViewById(R.id.btn_reward_show);
        Button mBackBtn = findViewById(R.id.btn_arv_back);

        loadAd.setOnClickListener(this);
        mLoadAdVertical.setOnClickListener(this);
        mShowAd.setOnClickListener(this);
        mBackBtn.setOnClickListener(this);
    }
}
