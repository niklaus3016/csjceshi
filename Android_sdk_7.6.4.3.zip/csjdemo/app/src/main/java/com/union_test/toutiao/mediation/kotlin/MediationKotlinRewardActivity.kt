package com.union_test.toutiao.mediation.kotlin

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTRewardVideoAd
import com.union_test.toutiao.R
import com.union_test.toutiao.mediation.java.utils.Const

class MediationKotlinRewardActivity : AppCompatActivity() {

    private var mediaId: String? = null
    //@[classname]
    private var rewardVideoAd: TTRewardVideoAd? = null
    //@[classname]
    private var adNativeLoader: TTAdNative? = null
    //@[classname]
    private var rewardVideoAdListener: TTAdNative.RewardVideoAdListener? = null
    //@[classname]
    private var rewardAdInteractionListener: TTRewardVideoAd.RewardAdInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_reward)
        mediaId = resources.getString(R.string.reward_media_id)
        val tvMediationId = findViewById<TextView>(R.id.tv_media_id)
        tvMediationId.text = String.format(
            resources.getString(R.string.ad_mediation_id),
            mediaId
        )
        findViewById<Button>(R.id.bt_load).setOnClickListener {
            loadAd()
        }
        findViewById<Button>(R.id.bt_show).setOnClickListener {
            showAd()
        }
    }

    private fun loadAd() {

        /** 1、创建AdSlot对象 */
        //@[classname]
        val adslot = AdSlot.Builder()
            .setCodeId(mediaId)
            //@[classname]
            .setOrientation(TTAdConstant.VERTICAL)
            .build()

        /** 2、创建TTAdNative对象 */
        //@[classname]//@[methodname]
        adNativeLoader = TTAdSdk.getAdManager().createAdNative(this@MediationKotlinRewardActivity)

        /** 3、创建加载、展示监听器 */
        initListeners()

        /** 4、加载广告 */
        adNativeLoader?.loadRewardVideoAd(adslot, rewardVideoAdListener)
    }

    private fun showAd() {
        if (rewardVideoAd == null) {
            Log.i(Const.TAG, "请先加载广告或等待广告加载完毕后再调用show方法")
        }
        rewardVideoAd?.let {
            if (it.mediationManager.isReady) {
                /** 5、设置展示监听器，展示广告 */
                it.setRewardAdInteractionListener(rewardAdInteractionListener)
                it.showRewardVideoAd(this@MediationKotlinRewardActivity)
            }
        }
    }

    private fun initListeners() {
        // 广告加载监听器
        //@[classname]
        rewardVideoAdListener = object : TTAdNative.RewardVideoAdListener {
            override fun onError(code: Int, message: String?) {
                Log.i(Const.TAG, "onError code = ${code} msg = ${message}")
            }
            //@[classname]
            override fun onRewardVideoAdLoad(ad: TTRewardVideoAd?) {
                Log.i(Const.TAG, "onRewardVideoAdLoad")
                rewardVideoAd = ad
            }

            override fun onRewardVideoCached() {
                Log.i(Const.TAG, "onRewardVideoCached")
            }
            //@[classname]
            override fun onRewardVideoCached(ad: TTRewardVideoAd?) {
                Log.i(Const.TAG, "onRewardVideoCached")
                rewardVideoAd = ad
            }
        }
        // 广告展示监听器
        //@[classname]
        rewardAdInteractionListener = object : TTRewardVideoAd.RewardAdInteractionListener {
            override fun onAdShow() {
                Log.i(Const.TAG, "onAdShow")
            }

            override fun onAdVideoBarClick() {
                Log.i(Const.TAG, "onAdVideoBarClick")
            }

            override fun onAdClose() {
                Log.i(Const.TAG, "onAdClose")
            }

            override fun onVideoComplete() {
                Log.i(Const.TAG, "onVideoComplete")
            }

            override fun onVideoError() {
                Log.i(Const.TAG, "onVideoError")
            }

            override fun onRewardVerify(
                rewardVerify: Boolean,
                rewardAmount: Int,
                rewardName: String?,
                errorCode: Int,
                errorMsg: String?
            ) {
                //此方法不生效
            }

            override fun onRewardArrived(
                isRewardValid: Boolean,
                rewardType: Int,
                extraInfo: Bundle?
            ) {
                Log.i(Const.TAG, "onRewardArrived, extra: " + extraInfo?.toString())
            }

            override fun onSkippedVideo() {
                Log.i(Const.TAG, "onSkippedVideo")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /** 6、在onDestroy中销毁广告  */
        rewardVideoAd?.mediationManager?.destroy()
    }
}