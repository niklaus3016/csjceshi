package com.union_test.toutiao.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdManager;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTFeedAd;
import com.bytedance.sdk.openadsdk.TTImage;
import com.bytedance.sdk.openadsdk.TTNativeAd;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.utils.TToast;

import java.util.LinkedList;
import java.util.List;

public class StreamActivity extends AppCompatActivity {

    public static final String TAG = "StreamActivity";


    private TTAdNative mTTAdNative;

    private StreamAdPlayer mStreamAdPlayer;

    private ViewGroup mAdLayout;
    private Button mBtLoadAd;
    private Button mBtPlayAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        initView();

        // step1:初始化sdk

        TTAdManager ttAdManager = TTAdManagerHolder.get();
        // step2:创建TTAdNative对象,用于调用广告请求接口

        mTTAdNative = ttAdManager.createAdNative(getApplicationContext());
        // step3:(可选，强烈建议在合适的时机调用):申请部分权限，如read_phone_state,防止获取不了imei时候，下载类广告没有填充的问题。
        TTAdManagerHolder.get().requestPermissionIfNecessary(this);
        final Button button = (Button) findViewById(R.id.btn_as_back);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStreamAdPlayer != null) {
            mStreamAdPlayer.resumeHandler();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStreamAdPlayer != null) {
            mStreamAdPlayer.pauseHandler();
        }
    }

    private void initView() {
        mAdLayout = findViewById(R.id.ad_layout);
        mBtLoadAd = findViewById(R.id.bt_load_ad);
        mBtPlayAd = findViewById(R.id.bt_play_ad);

        mBtLoadAd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadStreamAd();
            }
        });
        mBtPlayAd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mStreamAdPlayer == null) {
                    return;
                }
                mStreamAdPlayer.play();
            }
        });

    }

    private void loadStreamAd() {
        // step4:创建feed广告请求类型参数AdSlot,具体参数含义参考文档

        AdSlot adSlot = new AdSlot.Builder()
                .setCodeId("945593053")
                .setImageAcceptedSize(640, 320)
                .setAdCount(1) //请求广告数量为1到3条
                .build();
        if (mTTAdNative == null) {
            return;
        }
        // step5:请求广告，调用feed广告异步请求接口，加载到广告后，拿到广告素材自定义渲染

        mTTAdNative.loadStream(adSlot, new TTAdNative.FeedAdListener() {
            @Override
            public void onError(int code, String message) {
                TToast.show(StreamActivity.this, message);
            }

            @Override

            public void onFeedAdLoad(List<TTFeedAd> ads) {

                if (ads == null || ads.isEmpty()) {
                    TToast.show(getApplicationContext(), "广告加载失败");
                    return;
                }
                TToast.show(getApplicationContext(), "广告加载完成");

                if (mStreamAdPlayer != null) {
                    mStreamAdPlayer.clear();
                }
                // step6: StreamAdPlayer是我们提供的Demo示例，仅供参考
                mStreamAdPlayer = new StreamAdPlayer(ads, mAdLayout);

            }
        });
    }

    class StreamAdPlayer {
        private TTHandler mHandler = new TTHandler(Looper.getMainLooper());

        private ViewGroup mParent;

        private List<TTFeedAd> mAdList;
        private int mNowPlay = 0;

        private Runnable mPlayNext = new Runnable() {
            @Override
            public void run() {
                mParent.removeAllViews();
                if (mAdList.size() <= mNowPlay) {
                    return;
                }

                TTFeedAd ttFeedAd = mAdList.get(mNowPlay++);
                OneAd oneAd = getAdView(ttFeedAd);
                Log.d(TAG, "开始播放 " + ttFeedAd.getDescription() + mNowPlay);
                mParent.addView(oneAd.adView);
                //视频播放完之后延迟1s再移除广告，避免销毁广告后没有视频播放结束回调
                mHandler.sendPostDelayed(mPlayNext, (long) (oneAd.duration * 1000 + 1000));
            }
        };


        public OneAd getAdView(TTFeedAd ttFeedAd) {
            OneAd oneAd = null;

            switch (ttFeedAd.getImageMode()) {

                case TTAdConstant.IMAGE_MODE_LARGE_IMG:

                case TTAdConstant.IMAGE_MODE_SMALL_IMG:

                case TTAdConstant.IMAGE_MODE_GROUP_IMG:

                case TTAdConstant.IMAGE_MODE_VERTICAL_IMG:
                    oneAd = getImageTypeView(ttFeedAd);
                    break;

                case TTAdConstant.IMAGE_MODE_VIDEO:

                case TTAdConstant.IMAGE_MODE_VIDEO_VERTICAL:
                    oneAd = getVideoTypeView(ttFeedAd);
                    break;
            }
            return oneAd;
        }


        public StreamAdPlayer(List<TTFeedAd> adList, ViewGroup parent) {
            this.mAdList = adList;
            this.mParent = parent;
        }


        public OneAd getImageTypeView(TTFeedAd ttFeedAd) {
            List<TTImage> ttImages = ttFeedAd.getImageList();
            if (ttImages.size() == 0) {
                return null;
            }
            TTImage ttImage = ttImages.get(0);
            if (!ttImage.isValid()) {
                return null;
            }
            ImageView imageView = new ImageView(getApplicationContext());
            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000);
            imageView.setLayoutParams(layoutParams);
            Glide.with(getApplicationContext()).load(ttImage.getImageUrl()).into(imageView);


            List<View> clickViews = new LinkedList<>();
            clickViews.add(imageView);
            List<View> creativeClickViews = new LinkedList<>();
            creativeClickViews.add(imageView);

            ttFeedAd.registerViewForInteraction(mAdLayout, clickViews, creativeClickViews, new TTNativeAd.AdInteractionListener() {
                @Override

                public void onAdClicked(View view, TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "被点击");
                    }
                }

                @Override

                public void onAdCreativeClick(View view, TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "被创意按钮被点击");
                    }
                }

                @Override

                public void onAdShow(TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "展示");
                    }
                }
            });
            return new OneAd(imageView, ttImage.getDuration());
        }


        public OneAd getVideoTypeView(TTFeedAd ttFeedAd) {


            View videoView = ttFeedAd.getAdView();

            List<View> clickViews = new LinkedList<>();

            clickViews.add(videoView);
            List<View> creativeClickViews = new LinkedList<>();
            creativeClickViews.add(videoView);

            ttFeedAd.registerViewForInteraction(mAdLayout, clickViews, creativeClickViews, new TTNativeAd.AdInteractionListener() {
                @Override

                public void onAdClicked(View view, TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "被点击");
                    }
                }

                @Override

                public void onAdCreativeClick(View view, TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "被创意按钮被点击");
                    }
                }

                @Override

                public void onAdShow(TTNativeAd ad) {
                    if (ad != null) {
                        TToast.show(getApplicationContext(), "广告" + ad.getTitle() + "展示");
                    }
                }
            });
            return new OneAd(videoView, ttFeedAd.getVideoDuration());
        }

        public void play() {
            if (mParent == null) {
                return;
            }

            mParent.removeAllViews();

            mPlayNext.run();
        }

        public void clear() {
            mHandler.clearCallback();
        }

        public void pauseHandler() {
            mHandler.postDelayedPause();
        }

        public void resumeHandler() {
            mHandler.postDelayedResume();
        }

        class OneAd {
            View adView;
            double duration;

            public OneAd(View adView, double duration) {
                this.adView = adView;
                this.duration = duration;
            }
        }

    }

    /**
     * 可暂停Handler，在界面onPause，onResume切换时，能保证视频postDelay的时间不被后台时消耗
     */
    static class TTHandler extends Handler {

        private Runnable mRunnable;
        private long mOldAtTime;
        private long mRemainTime;

        TTHandler(Looper looper) {
            super(looper);
        }

        void postDelayedPause() {
            mRemainTime = mOldAtTime - System.currentTimeMillis();
            Log.d(TAG, "TTHandler pause remainTime: " + mRemainTime);
            removeCallbacks(mRunnable);
        }

        void postDelayedResume() {
            if (mRemainTime > 0) {
                Log.d(TAG, "TTHandler resume remainTime: " + mRemainTime);
                sendPostDelayed(mRunnable, mRemainTime);
            }
        }

        void sendPostDelayed(Runnable r, long delayMillis) {
            mRemainTime = 0;
            mRunnable = r;
            mOldAtTime = System.currentTimeMillis() + delayMillis;
            postDelayed(mRunnable, delayMillis);
        }

        void clearCallback() {
            mOldAtTime = 0;
            mRemainTime = 0;
            removeCallbacks(mRunnable);
        }

    }
}
