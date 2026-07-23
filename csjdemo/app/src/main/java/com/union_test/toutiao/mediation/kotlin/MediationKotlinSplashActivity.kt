package com.union_test.toutiao.mediation.kotlin

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.CSJAdError
import com.bytedance.sdk.openadsdk.CSJSplashAd
import com.bytedance.sdk.openadsdk.CSJSplashCloseType
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.union_test.toutiao.R
import com.union_test.toutiao.mediation.java.utils.Const
import com.union_test.toutiao.utils.UIUtils


class MediationKotlinSplashActivity : AppCompatActivity() {

    private var flContent: FrameLayout? = null
    //@[classname]
    private var csjSplashAdListener:TTAdNative.CSJSplashAdListener? = null
    //@[classname]
    private var splashAdListener: CSJSplashAd.SplashAdListener? = null
    //@[classname]
    private var csjSplashAd: CSJSplashAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mediation_activity_splash)
        flContent = findViewById<FrameLayout>(R.id.fl_content);
        loadAd()
    }

    fun loadAd(){
        /** 1、创建AdSlot对象 */
        //@[classname]
        val adslot = AdSlot.Builder()
            .setCodeId(resources.getString(R.string.splash_media_id))
            .setImageAcceptedSize(UIUtils.getScreenWidthInPx(this),UIUtils.getScreenHeightInPx(this))
            .build()

        /** 2、创建TTAdNative对象 */
        //@[classname]//@[methodname]
        val adNativeLoader = TTAdSdk.getAdManager().createAdNative(this@MediationKotlinSplashActivity)

        /** 3、创建加载、展示监听器 */
        initListeners()

        /** 4、加载广告  */
        adNativeLoader?.loadSplashAd(adslot, csjSplashAdListener, 3500)
    }

    //@[classname]
    fun showAd(csjSplashAd: CSJSplashAd?){
        /** 5、渲染成功后，展示广告 */
        this.csjSplashAd = csjSplashAd
        csjSplashAd?.setSplashAdListener(splashAdListener)
        csjSplashAd?.let {
            it.splashView?.let {  splashView ->
                flContent?.addView(splashView)
            }
        }
    }

    private fun initListeners() {
        // 广告加载监听器
        //@[classname]
        csjSplashAdListener  = object:TTAdNative.CSJSplashAdListener{
            //@[classname]
            override fun onSplashLoadSuccess(ad: CSJSplashAd?) {
                Log.i(Const.TAG, "onSplashAdLoad")
            }
            //@[classname]
            override fun onSplashLoadFail(error: CSJAdError?) {
                Log.i(Const.TAG, "onError code = ${error?.code} msg = ${error?.msg}")
                this@MediationKotlinSplashActivity.finish()
            }
            //@[classname]
            override fun onSplashRenderSuccess(ad: CSJSplashAd?) {
                Log.i(Const.TAG, "onSplashRenderSuccess")
                showAd(ad)
            }
            //@[classname]
            override fun onSplashRenderFail(ad: CSJSplashAd?, error: CSJAdError?) {
                Log.i(Const.TAG, "onError code = ${error?.code} msg = ${error?.msg}")
            }
        }
        //@[classname]
        splashAdListener = object : CSJSplashAd.SplashAdListener{
            //@[classname]
            override fun onSplashAdShow(p0: CSJSplashAd?) {
                Log.i(Const.TAG, "onSplashAdShow")
            }
            //@[classname]
            override fun onSplashAdClick(p0: CSJSplashAd?) {
                Log.i(Const.TAG, "onSplashAdClick")
            }
            //@[classname]
            override fun onSplashAdClose(p0: CSJSplashAd?, closeType: Int) {
                //@[classname]
                if (closeType == CSJSplashCloseType.CLICK_SKIP) {
                    Log.d(Const.TAG, "开屏广告点击跳过")
                    //@[classname]
                } else if (closeType == CSJSplashCloseType.COUNT_DOWN_OVER) {
                    Log.d(Const.TAG, "开屏广告点击倒计时结束")
                    //@[classname]
                } else if (closeType == CSJSplashCloseType.CLICK_JUMP) {
                    Log.d(Const.TAG, "点击跳转")
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        /** 6、在onDestroy中销毁广告  */
        csjSplashAd?.mediationManager?.destroy()
    }
}