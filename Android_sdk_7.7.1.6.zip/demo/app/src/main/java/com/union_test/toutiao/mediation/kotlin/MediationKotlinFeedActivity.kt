package com.union_test.toutiao.mediation.kotlin

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTFeedAd
import com.bytedance.sdk.openadsdk.TTNativeAd
import com.bytedance.sdk.openadsdk.mediation.ad.MediationExpressRenderListener
import com.union_test.toutiao.R
import com.union_test.toutiao.mediation.java.utils.FeedAdUtils
import com.union_test.toutiao.mediation.java.utils.Const
import com.union_test.toutiao.utils.UIUtils

class MediationKotlinFeedActivity : AppCompatActivity() {

    private var mediaId: String? = null
    private var flContent: FrameLayout? = null
    //@[classname]
    private var feedAd: TTFeedAd? = null
    //@[classname]
    private var adNativeLoader: TTAdNative? = null
    //@[classname]
    private var feedAdListener:TTAdNative.FeedAdListener? = null
    private var mediationExpressRenderListener:MediationExpressRenderListener? = null
    //@[classname]
    private var adInteractionListener:TTNativeAd.AdInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_feed)
        flContent = findViewById<FrameLayout>(R.id.fl_content);
        mediaId = resources.getString(R.string.feed_express_media_id)
        val tvMediationId = findViewById<TextView>(R.id.tv_media_id)
        tvMediationId.text = String.format(
            resources.getString(R.string.ad_mediation_id),
            mediaId
        )
        findViewById<Button>(R.id.bt_load).setOnClickListener {
            loadAd()
        }
        findViewById<Button>(R.id.bt_show).setOnClickListener {
            show()
        }
    }

    private fun loadAd() {
        flContent?.removeAllViews()

        /** 1、创建AdSlot对象 */
        //@[classname]
        val adslot = AdSlot.Builder()
            .setCodeId(mediaId)
            .setImageAcceptedSize(UIUtils.getScreenWidthInPx(this), 720)
            .setAdCount(1)//请求广告数量为1到3条 （优先采用平台配置的数量）
            .build()

        /** 2、创建TTAdNative对象 */
        //@[classname]//@[methodname]
        adNativeLoader = TTAdSdk.getAdManager().createAdNative(this@MediationKotlinFeedActivity)

        /** 3、创建加载、展示监听器 */
        initListeners()

        /** 4、加载广告 */
        adNativeLoader?.loadFeedAd(adslot, feedAdListener)
    }

    private fun show() {
        if (feedAd == null) {
            Log.i(Const.TAG, "请先加载广告或等待广告加载完毕后再调用show方法")
            return
        }
        feedAd?.let { it ->
            /** 5、展示广告 */
            //--------------信息流模板广告渲染----------------
            if (it.mediationManager.isExpress) {
                it.setExpressRenderListener(mediationExpressRenderListener)
                it.render()//必须要调用render方法进行渲染，在onRenderSuccess中处理模板的view
            } else {
                //--------------信息流自渲染广告渲染----------------
                val feedView = FeedAdUtils.getFeedAdFromFeedInfo(feedAd, this, null, adInteractionListener)
                feedView?.let { view ->
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    UIUtils.removeFromParent(view)
                    flContent?.addView(view)
                }
            }
        }
    }

    private fun initListeners() {
        // 广告加载监听器
        //@[classname]
        feedAdListener = object : TTAdNative.FeedAdListener {
            override fun onError(code: Int, message: String?) {
                Log.i(Const.TAG, "onError code = ${code} msg = ${message}")
            }

            //@[classname]
            override fun onFeedAdLoad(ads: MutableList<TTFeedAd>?) {
                Log.i(Const.TAG, "onFeedAdLoad list.size = ${ads?.size}")
                ads?.let {
                    if (it.size > 0) {
                        feedAd = it[0]
                    }
                }
            }
        }
        // 模板广告展示监听器
        mediationExpressRenderListener = object : MediationExpressRenderListener {
            override fun onRenderSuccess(p0: View?, p1: Float, p2: Float, p3: Boolean) {
                Log.i(Const.TAG, "onRenderSuccess")
                feedAd?.adView?.let { view ->            // *** 注意不要使用onRenderSuccess参数中的view ***
                    UIUtils.removeFromParent(view)
                    flContent?.addView(view)
                }
            }

            override fun onRenderFail(p0: View?, p1: String?, p2: Int) {
                Log.i(Const.TAG, "onRenderFail")
            }

            override fun onAdClick() {
                Log.i(Const.TAG, "onAdClick")
            }

            override fun onAdShow() {
                Log.i(Const.TAG, "onAdShow")
            }
        }
        // 自渲染广告展示监听器
        //@[classname]
        adInteractionListener = object : TTNativeAd.AdInteractionListener {
            //@[classname]
            override fun onAdClicked(view: View?, ad: TTNativeAd?) {
                Log.i(Const.TAG, "onAdClicked")
            }
            //@[classname]
            override fun onAdCreativeClick(view: View?, ad: TTNativeAd?) {
                Log.i(Const.TAG, "onAdCreativeClick")
            }
            //@[classname]
            override fun onAdShow(ad: TTNativeAd?) {
                Log.i(Const.TAG, "onAdShow")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /** 6、在onDestroy中销毁广告  */
        feedAd?.destroy()
    }
}