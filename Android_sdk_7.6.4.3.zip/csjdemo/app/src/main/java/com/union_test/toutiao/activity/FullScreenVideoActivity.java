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
import com.bytedance.sdk.openadsdk.TTAdLoadType;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAppDownloadListener;
import com.bytedance.sdk.openadsdk.TTFullScreenVideoAd;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.utils.TToast;

import java.lang.ref.WeakReference;

/**
 * Created by bytedance on 2018/2/1.
 * 全屏/新插屏接入类
 */
public class FullScreenVideoActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "FullScreenVideoActivity";


    private TTAdNative mTTAdNative;
    private AdLoadListener mAdLoadListener;

    private String mHorizontalCodeId;
    private String mVerticalCodeId;
    private boolean isInteraction;

    @SuppressWarnings("RedundantCast")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_video);
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
            case R.id.btn_fsv_back:
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
                .build();
        if (mTTAdNative == null) {
            return;
        }
        //step6:注册广告加载生命周期监听，请求广告

        mAdLoadListener = new AdLoadListener(this);

        mTTAdNative.loadFullScreenVideoAd(adSlot, mAdLoadListener);
    }

    /**
     * 展示广告
     */
    private void showAd() {
        if (mAdLoadListener == null) {
            return;
        }

        mAdLoadListener.showAd(TTAdConstant.RitScenes.CUSTOMIZE_SCENES, "scenes_test");
    }

    /**
     * 【必须】广告加载期间生命周期监听
     */

    private static class AdLoadListener implements TTAdNative.FullScreenVideoAdListener {

        private final Activity mActivity;

        private TTFullScreenVideoAd mAd;

        public AdLoadListener(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void onError(int code, String message) {
            Log.e(TAG, "Callback --> onError: " + code + ", " + message);
            TToast.show(mActivity, message);
        }

        @Override

        public void onFullScreenVideoAdLoad(TTFullScreenVideoAd ad) {
            Log.e(TAG, "Callback --> onFullScreenVideoAdLoad");
            TToast.show(mActivity, "FullVideoAd loaded  广告类型：" + getAdType(ad.getFullVideoAdType()));
            handleAd(ad);
        }

        @Override

        public void onFullScreenVideoCached() {
            // 已废弃 请使用 onRewardVideoCached(TTRewardVideoAd ad) 方法
        }

        @Override

        public void onFullScreenVideoCached(TTFullScreenVideoAd ad) {
            Log.e(TAG, "Callback --> onFullScreenVideoCached");
            TToast.show(mActivity, "FullVideoAd video cached");
            handleAd(ad);
        }

        /**
         * 处理广告对象
         */

        public void handleAd(TTFullScreenVideoAd ad) {
            if (mAd != null) {
                return;
            }
            mAd = ad;
            //【必须】广告展示时的生命周期监听

            mAd.setFullScreenVideoAdInteractionListener(new AdLifeListener(mActivity));
            //【可选】监听下载状态
            mAd.setDownloadListener(new DownloadStatusListener());
        }

        /**
         * 触发展示广告
         */

        public void showAd(TTAdConstant.RitScenes ritScenes, String scenes) {
            if (mAd == null) {
                TToast.show(mActivity, "当前广告未加载好，请先点击加载广告");
                return;
            }

            mAd.showFullScreenVideoAd(mActivity, ritScenes, scenes);
            // 广告使用后应废弃
            mAd = null;
        }
    }

    /**
     * 【必须】广告生命状态监听器
     */

    private static class AdLifeListener implements TTFullScreenVideoAd.FullScreenVideoAdInteractionListener {

        private final WeakReference<Context> mContextRef;

        public AdLifeListener(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override

        public void onAdShow() {
            Log.d(TAG, "Callback --> FullVideoAd show");
            TToast.show(mContextRef.get(), "FullVideoAd show");
        }

        @Override

        public void onAdVideoBarClick() {
            Log.d(TAG, "Callback --> FullVideoAd bar click");
            TToast.show(mContextRef.get(), "FullVideoAd bar click");
        }

        @Override

        public void onAdClose() {
            Log.d(TAG, "Callback --> FullVideoAd close");
            TToast.show(mContextRef.get(), "FullVideoAd close");
        }

        @Override
        public void onVideoComplete() {
            Log.d(TAG, "Callback --> FullVideoAd complete");
            TToast.show(mContextRef.get(), "FullVideoAd complete");
        }

        @Override
        public void onSkippedVideo() {
            Log.d(TAG, "Callback --> FullVideoAd skipped");
            TToast.show(mContextRef.get(), "FullVideoAd skipped");
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
                return "普通全屏视频，type=" + type;

            case TTAdConstant.AD_TYPE_PLAYABLE_VIDEO:
                return "Playable全屏视频，type=" + type;

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
        isInteraction = intent.getBooleanExtra("is_interaction", false);

    }

    private void initViews() {
        Button loadAd = findViewById(R.id.btn_reward_load);
        Button loadAdVertical = findViewById(R.id.btn_reward_load_vertical);
        Button showAd = findViewById(R.id.btn_reward_show);
        Button backBtn = findViewById(R.id.btn_fsv_back);
        if (isInteraction) {
            loadAd.setText(getResources().getText(R.string.load_full_interaction_horizontal));
            loadAdVertical.setText(getResources().getText(R.string.load_full_interaction_vertical));
            showAd.setText(getResources().getText(R.string.show_full_interaction));
        }

        loadAd.setOnClickListener(this);
        loadAdVertical.setOnClickListener(this);
        showAd.setOnClickListener(this);
        backBtn.setOnClickListener(this);
    }
}
