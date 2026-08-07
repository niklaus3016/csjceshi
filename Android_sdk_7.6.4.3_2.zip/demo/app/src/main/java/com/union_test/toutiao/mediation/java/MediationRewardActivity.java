package com.union_test.toutiao.mediation.java;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot;
import com.bytedance.sdk.openadsdk.mediation.manager.MediationAdLoadInfo;
import com.bytedance.sdk.openadsdk.mediation.manager.MediationRewardManager;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.mediation.java.utils.Const;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.function.Consumer;

/**
 * 融合demo，激励视频广告使用示例。更多功能参考接入文档。
 *
 * 注意：每次加载的广告，只能展示一次
 *
 * 接入步骤：
 * 1、创建AdSlot对象
 * 2、创建TTAdNative对象
 * 3、创建加载、展示监听器
 * 4、加载广告
 * 5、加载成功后，展示广告
 * 6、在onDestroy中销毁广告
 */
public class MediationRewardActivity extends Activity {

    public String mMediaId; // 融合广告位

    private TTRewardVideoAd mTTRewardVideoAd; // 插全屏广告对象

    private TTAdNative.RewardVideoAdListener mRewardVideoListener; // 广告加载监听器

    private TTRewardVideoAd.RewardAdInteractionListener mRewardVideoAdInteractionListener; // 广告展示监听器

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mediation_activity_reward);

        // 聚合广告位（在GroMore平台的广告位，注意不是adn的代码位）
        this.mMediaId = getResources().getString(R.string.reward_media_id);
        TextView tvMediationId = findViewById(R.id.tv_media_id);
        tvMediationId.setText(String.format(getResources().getString(R.string.ad_mediation_id), mMediaId));

        // 广告加载
        findViewById(R.id.bt_load).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadRewardVideoAd();
            }
        });

        // 广告展示
        findViewById(R.id.bt_show).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRewardVideoAd();
            }
        });
    }

    private void loadRewardVideoAd() {
        /** 1、创建AdSlot对象 */

        AdSlot adslot = new AdSlot.Builder()
                .setCodeId(mMediaId)

                .setOrientation(TTAdConstant.ORIENTATION_VERTICAL)
                .setMediationAdSlot(new MediationAdSlot.Builder().setExtraObject(
                        "show_adn_load_error_detail", true).build())
                .build();

        /** 2、创建TTAdNative对象 */

        TTAdNative adNativeLoader = TTAdManagerHolder.get().createAdNative(this);

        /** 3、创建加载、展示监听器 */
        initListeners();

        /** 4、加载广告 */
        if (adNativeLoader != null) {

            adNativeLoader.loadRewardVideoAd(adslot, mRewardVideoListener);
        }
    }

    // 广告加载成功后，开始展示广告
    private void showRewardVideoAd() {
        if (mTTRewardVideoAd == null) {
            Log.i(Const.TAG, "请先加载广告或等待广告加载完毕后再调用show方法");
            return;
        }
        /** 5、设置展示监听器，展示广告 */

        mTTRewardVideoAd.setRewardAdInteractionListener(mRewardVideoAdInteractionListener);

        mTTRewardVideoAd.showRewardVideoAd(this);
    }

    private void initListeners() {
        // 广告加载监听器

        this.mRewardVideoListener = new TTAdNative.RewardVideoAdListener() {
            @Override
            public void onError(int i, String s) {
                Log.i(Const.TAG, "reward load fail: errCode: " + i + ", errMsg: " + s);

                // v7300 新增过滤信息解析
                try {
                    if (s == null) {
                        return;
                    }
                    JSONArray jsonArray = new JSONArray(s);
                    // 遍历数组
                    for (int j = 0; j < jsonArray.length(); j++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(j);
                        // 获取属性值
                        String adnName = jsonObject.getString("adn_name");
                        String mediationRit = jsonObject.getString("mediation_rit");
                        String adType = jsonObject.getString("ad_type");
                        int errorCode = jsonObject.getInt("error_code");
                        if (errorCode == 81018) { // 被客户端过滤的配置
                            // 如果code为81018，则获取error_msg属性值error_msg 即为 被过滤规则。
                            String id = jsonObject.getString("error_msg");
                            Log.i(Const.TAG,
                                  "mediationRit = " + mediationRit + " filter by id = " + id);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }

            }

            @Override

            public void onRewardVideoAdLoad(TTRewardVideoAd ttRewardVideoAd) {
                Log.i(Const.TAG, "reward load success");
                mTTRewardVideoAd = ttRewardVideoAd;

                // v7300 新增过滤信息解析
                MediationRewardManager mediationManager = mTTRewardVideoAd.getMediationManager();
                if(mediationManager == null){
                    return;
                }
                List<MediationAdLoadInfo> adLoadInfo = mediationManager.getAdLoadInfo();
                if(adLoadInfo == null){
                    return;
                }
                adLoadInfo.forEach(new Consumer<MediationAdLoadInfo>() {
                    @Override
                    public void accept(MediationAdLoadInfo mediationAdLoadInfo) {
                        int errCode = mediationAdLoadInfo.getErrCode();//81018 被过滤
                        if (errCode == 81018) {
                            // 获取过滤规则id
                            String mediationRit = mediationAdLoadInfo.getMediationRit();
                            String id = mediationAdLoadInfo.getErrMsg();
                            Log.i(Const.TAG,
                                  "mediationRit = " + mediationRit + " filter by id = " + id);
                        }
                    }
                });
            }

            @Override

            public void onRewardVideoCached() {
                Log.i(Const.TAG, "reward cached success");
            }

            @Override

            public void onRewardVideoCached(TTRewardVideoAd ttRewardVideoAd) {
                Log.i(Const.TAG, "reward cached success 2");
                mTTRewardVideoAd = ttRewardVideoAd;
            }
        };
        // 广告展示监听器

        this.mRewardVideoAdInteractionListener = new TTRewardVideoAd.RewardAdInteractionListener() {
            @Override

            public void onAdShow() {
                Log.i(Const.TAG, "reward show");
            }

            @Override

            public void onAdVideoBarClick() {
                Log.i(Const.TAG, "reward click");
            }

            @Override

            public void onAdClose() {
                Log.i(Const.TAG, "reward close");
            }

            @Override
            public void onVideoComplete() {
                Log.i(Const.TAG, "reward onVideoComplete");
            }

            @Override
            public void onVideoError() {
                Log.i(Const.TAG, "reward onVideoError");
            }

            @Override

            public void onRewardVerify(boolean b, int i, String s, int i1, String s1) {
                Log.i(Const.TAG, "reward onRewardVerify");
            }

            @Override
            public void onRewardArrived(boolean isRewardValid, int rewardType, Bundle extraInfo) {
                Log.i(Const.TAG, "reward onRewardArrived");
            }

            @Override
            public void onSkippedVideo() {
                Log.i(Const.TAG, "reward onSkippedVideo");
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /** 6、在onDestroy中销毁广告 */
        if (mTTRewardVideoAd != null && mTTRewardVideoAd.getMediationManager() != null) {
            mTTRewardVideoAd.getMediationManager().destroy();
        }
    }
}
