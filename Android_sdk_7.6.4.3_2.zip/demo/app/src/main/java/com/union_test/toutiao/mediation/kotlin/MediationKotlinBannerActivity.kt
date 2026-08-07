package com.union_test.toutiao.mediation.kotlin

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdDislike
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTNativeExpressAd
import com.union_test.toutiao.R
import com.union_test.toutiao.mediation.java.utils.Const
import com.union_test.toutiao.utils.UIUtils

class MediationKotlinBannerActivity : AppCompatActivity() {

    private var mediaId: String? = null
    private var bannerContainer: FrameLayout? = null
    //@[classname]
    private var bannerAd: TTNativeExpressAd? = null
    //@[classname]
    private var adNativeLoader: TTAdNative? = null
    //@[classname]
    private var adSlot: AdSlot? = null
    //@[classname]
    private var nativeExpressAdListener: TTAdNative.NativeExpressAdListener? = null
    //@[classname]
    private var expressAdInteractionListener: TTNativeExpressAd.ExpressAdInteractionListener? = null
    //@[classname]
    private var dislikeInteractionCallback: TTAdDislike.DislikeInteractionCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_banner)
        mediaId = resources.getString(R.string.banner_media_id)
        val tvMediationId = findViewById<TextView>(R.id.tv_media_id)
        tvMediationId.text = String.format(
            resources.getString(R.string.ad_mediation_id),
            mediaId
        )

        bannerContainer = findViewById(R.id.banner_container)
        findViewById<Button>(R.id.bt_load).setOnClickListener {
            loadAd()
        }
        findViewById<Button>(R.id.bt_show).setOnClickListener {
            showAd()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerAd?.destroy()
    }

    private fun loadAd() {
        bannerContainer?.removeAllViews()

        /** 1、创建AdSlot对象 */
        //@[classname]
        adSlot = AdSlot.Builder()
            .setCodeId(mediaId)
            .setImageAcceptedSize(UIUtils.dp2px(this, 320f), UIUtils.dp2px(this, 150f)) // 单位px
            .build()

        /** 2、创建TTAdNative对象 */
        //@[classname]//@[methodname]
        adNativeLoader = TTAdSdk.getAdManager().createAdNative(this)

        /** 3、创建加载、展示监听器 */
        initListeners()

        /** 4、加载广告 */
        adNativeLoader?.loadBannerExpressAd(adSlot, nativeExpressAdListener)
    }

    private fun showAd() {
        if (bannerAd == null) {
            Log.i(Const.TAG, "请先加载广告或等待广告加载完毕后再调用show方法")
        }
        bannerAd?.setExpressInteractionListener(expressAdInteractionListener)
        bannerAd?.setDislikeCallback(this@MediationKotlinBannerActivity, dislikeInteractionCallback)

        /** 注意：使用融合功能时，load成功后可直接调用getExpressAdView获取广告view展示，而无需调用render等onRenderSuccess后 */
        val bannerView: View? = bannerAd?.expressAdView
        if (bannerView != null) {
            bannerContainer?.removeAllViews()
            bannerContainer?.addView(bannerView)
        }
    }

    private fun initListeners() {
        // 广告加载监听器
        //@[classname]
        nativeExpressAdListener = object : TTAdNative.NativeExpressAdListener {
            //@[classname]
            override fun onNativeExpressAdLoad(ads: MutableList<TTNativeExpressAd>?) {
                if (ads != null) {
                    Log.d(Const.TAG, "banner load success: " + ads.size)
                }
                ads?.let {
                    if (it.size > 0) {
                        //@[classname]
                        val ad: TTNativeExpressAd = it[0]
                        bannerAd = ad
                    }
                }
            }

            override fun onError(code: Int, message: String?) {
                Log.d(Const.TAG, "banner load fail: $code, $message")
            }
        }
        // 广告展示监听器
        expressAdInteractionListener = object :
        //@[classname]
            TTNativeExpressAd.ExpressAdInteractionListener {
            override fun onAdClicked(view: View?, type: Int) {
                Log.d(Const.TAG, "banner clicked")
            }

            override fun onAdShow(view: View?, type: Int) {
                Log.d(Const.TAG, "banner show")
            }

            override fun onRenderFail(view: View?, msg: String?, code: Int) {
                // 注意：使用融合功能时，无需调用render，load成功后可调用mBannerAd.getExpressAdView()进行展示。
            }

            override fun onRenderSuccess(view: View?, width: Float, height: Float) {
                // 注意：使用融合功能时，无需调用render，load成功后可调用mBannerAd.getExpressAdView()获取view进行展示。
                // 如果调用了render，则会直接回调onRenderSuccess，***** 参数view为null，请勿使用。*****
            }
        }

        // dislike监听器，广告关闭时会回调onSelected
        //@[classname]
        dislikeInteractionCallback = object : TTAdDislike.DislikeInteractionCallback {
            override fun onShow() {
                Log.d(Const.TAG, "banner dislike show")
            }

            override fun onSelected(
                position: Int,
                value: String?,
                enforce: Boolean
            ) {
                Log.d(Const.TAG, "banner dislike closed")
                bannerContainer?.removeAllViews()
            }

            override fun onCancel() {
                Log.d(Const.TAG, "banner dislike cancel")
            }
        }
    }

}