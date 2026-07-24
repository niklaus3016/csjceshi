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
import com.bytedance.sdk.openadsdk.TTFullScreenVideoAd
import com.union_test.toutiao.R
import com.union_test.toutiao.mediation.java.utils.Const

class MediationKotlinInterstitialFullActivity : AppCompatActivity() {

    private var mediaId: String? = null
    //@[classname]
    private var adSlot: AdSlot? = null
    //@[classname]
    private var adNativeLoader: TTAdNative? = null
    //@[classname]
    private var fullScreenVideoAd: TTFullScreenVideoAd? = null
    //@[classname]
    private var fullScreenVideoAdListener:TTAdNative.FullScreenVideoAdListener? = null
    //@[classname]
    private var fullScreenVideoAdInteractionListener:TTFullScreenVideoAd.FullScreenVideoAdInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_interstitial_full)
        mediaId = resources.getString(R.string.full_media_id)
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


    fun loadAd() {
        /** 1、创建AdSlot对象 */
        //@[classname]
        adSlot = AdSlot.Builder()
            .setCodeId(mediaId)
            //@[classname]
            .setOrientation(TTAdConstant.VERTICAL)
            .build()

        /** 2、创建TTAdNative对象 */
        //@[classname]//@[methodname]
        adNativeLoader = TTAdSdk.getAdManager().createAdNative(this@MediationKotlinInterstitialFullActivity)


        /** 3、创建加载、展示监听器 */
        initListeners()

        /** 4、加载广告 */
        adNativeLoader?.loadFullScreenVideoAd(adSlot, fullScreenVideoAdListener)
    }

    fun showAd() {
        if (fullScreenVideoAd == null) {
            Log.i(Const.TAG, "请先加载广告或等待广告加载完毕后再调用show方法")
        }
        fullScreenVideoAd?.let {
            if (it.mediationManager.isReady) {
                /** 5、设置展示监听器，展示广告 */
                it.setFullScreenVideoAdInteractionListener(fullScreenVideoAdInteractionListener)
                it.showFullScreenVideoAd(this@MediationKotlinInterstitialFullActivity)
            } else {
                Log.i(Const.TAG, "video is not ready")
            }
        }
    }

    private fun initListeners() {
        // 广告加载监听器
        //@[classname]
        fullScreenVideoAdListener = object : TTAdNative.FullScreenVideoAdListener {
            override fun onError(code: Int, message: String?) {
                Log.i(Const.TAG, "onError code = ${code} msg = ${message}")
            }
            //@[classname]
            override fun onFullScreenVideoAdLoad(ad: TTFullScreenVideoAd?) {
                Log.i(Const.TAG, "onFullScreenVideoAdLoad")
                fullScreenVideoAd = ad
            }

            override fun onFullScreenVideoCached() {
                Log.i(Const.TAG, "onFullScreenVideoCached")
            }
            //@[classname]
            override fun onFullScreenVideoCached(ad: TTFullScreenVideoAd?) {
                Log.i(Const.TAG, "onFullScreenVideoCached")
                fullScreenVideoAd = ad
            }
        }
        // 广告展示监听器
        //@[classname]
        fullScreenVideoAdInteractionListener = object : TTFullScreenVideoAd.FullScreenVideoAdInteractionListener {
            override fun onAdShow() {
                Log.e(Const.TAG, "onAdShow");
            }

            override fun onAdVideoBarClick() {
                Log.e(Const.TAG, "onAdVideoBarClick");
            }

            override fun onAdClose() {
                Log.e(Const.TAG, "onAdClose");
            }

            override fun onVideoComplete() {
                Log.e(Const.TAG, "onVideoComplete");
            }

            override fun onSkippedVideo() {
                Log.e(Const.TAG, "onSkippedVideo");
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /** 6、在onDestroy中销毁广告  */
        fullScreenVideoAd?.mediationManager?.destroy()
    }
}