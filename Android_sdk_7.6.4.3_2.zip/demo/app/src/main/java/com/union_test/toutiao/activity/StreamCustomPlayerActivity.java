package com.union_test.toutiao.activity;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

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

public class StreamCustomPlayerActivity extends AppCompatActivity {
    public static final String TAG = "StreamCpActivity";


    private TTAdNative mTTAdNative;

    private StreamAdPlayer mStreamAdPlayer;

    private ViewGroup mAdLayout;
    private Button mBtLoadAd;
    private Button mBtPlayAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);
        final Button btn = (Button) findViewById(R.id.btn_as_back);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        initView();

        // step1:初始化sdk

        TTAdManager ttAdManager = TTAdManagerHolder.get();
        // step2:创建TTAdNative对象,用于调用广告请求接口

        mTTAdNative = ttAdManager.createAdNative(getApplicationContext());
        // step3:(可选，强烈建议在合适的时机调用):申请部分权限，如read_phone_state,防止获取不了imei时候，下载类广告没有填充的问题。
        TTAdManagerHolder.get().requestPermissionIfNecessary(this);


    }

    private void initView() {
        mAdLayout = findViewById(R.id.ad_layout);
        mBtLoadAd = findViewById(R.id.bt_load_ad);
        mBtPlayAd = findViewById(R.id.bt_play_ad);
        mBtPlayAd.setText("播放（自定义播放器）");

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
                TToast.show(StreamCustomPlayerActivity.this, message);
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
                // 使用自定义播放器需要考虑为视频Url预加载
                // 各家开发者的视频播放器个有不同
                // 需要预加载请在通过ttFeedAd.getCustomVideo().getVideoUrl()拿到视频url之后自行预加载
                mStreamAdPlayer = new StreamAdPlayer(ads, mAdLayout);

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mStreamAdPlayer != null) {
            mStreamAdPlayer.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mStreamAdPlayer != null) {
            mStreamAdPlayer.onResume();
        }
    }

    class StreamAdPlayer {
        private StreamActivity.TTHandler mHandler = new StreamActivity.TTHandler(Looper.getMainLooper());

        private ViewGroup mParent;

        private List<TTFeedAd> mAdList;
        private int mNowPlayIndex = 0;
        private OneAd mNowPlayAd;

        private Runnable mPlayNext = new Runnable() {
            @Override
            public void run() {
                mParent.removeAllViews();
                // 自定义播放器上报播放结束
                if (mNowPlayAd != null) {
                    mNowPlayAd.stop();
                }
                if (mAdList.size() <= mNowPlayIndex) {
                    return;
                }


                TTFeedAd ttFeedAd = mAdList.get(mNowPlayIndex++);
                mNowPlayAd = getAdView(ttFeedAd);
                if (mNowPlayAd != null) {
                    mNowPlayAd.play();
                    Log.d(TAG, "开始播放 " + ttFeedAd.getDescription() + mNowPlayIndex);
                    mParent.addView(mNowPlayAd.adView);
                    //视频播放完之后延迟1s再移除广告，避免销毁广告后没有视频播放结束回调
                    mHandler.sendPostDelayed(mPlayNext, (long) (mNowPlayAd.duration * 1000 + 1000));
                } else {
                    // 尝试播下一个
                    run();
                }
            }
        };

        public void onPause() {
            if (mNowPlayAd != null) {
                mNowPlayAd.pauseVideo();
            }
            mHandler.postDelayedPause();
        }

        public void onResume() {
            if (mNowPlayAd != null) {
                mNowPlayAd.continueVideo();
            }
            mHandler.postDelayedResume();
        }


        public OneAd getAdView(TTFeedAd ttFeedAd) {
            OneAd oneAd = null;

            switch (ttFeedAd.getImageMode()) {

                case TTAdConstant.IMAGE_MODE_LARGE_IMG:

                case TTAdConstant.IMAGE_MODE_SMALL_IMG:

                case TTAdConstant.IMAGE_MODE_GROUP_IMG:

                case TTAdConstant.IMAGE_MODE_VERTICAL_IMG:
                    oneAd = getImageTypeView(ttFeedAd);
                    break;

                case TTAdConstant.IMAGE_MODE_CREATIVE_EFFECT: //创意素材

                case TTAdConstant.IMAGE_MODE_VERTICAL_CREATIVE_EFFECT: //创意素材
                    //需要使用sdk内部的adView
                    oneAd = getSDKAdView(ttFeedAd);
                    break;

                case TTAdConstant.IMAGE_MODE_VIDEO:  //视频

                case TTAdConstant.IMAGE_MODE_VIDEO_VERTICAL: //视频
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
            return new OneAd(imageView, ttImage.getDuration(), ttFeedAd);
        }

        //穿山甲播放器播放

        public OneAd getSDKAdView(TTFeedAd ttFeedAd){
            View adView = ttFeedAd.getAdView();
            FrameLayout container = new FrameLayout(StreamCustomPlayerActivity.this);
            container.addView(adView);
            Button button = new Button(StreamCustomPlayerActivity.this);

            button.setText("点击转化");
            container.addView(button,new ViewGroup.LayoutParams(240,120));
            //更多用法，详见FeedListActivity

            ttFeedAd.registerViewForInteraction(container, button, new TTNativeAd.AdInteractionListener() {
                @Override

                public void onAdClicked(View view, TTNativeAd ttNativeAd) {
                    if (ttNativeAd != null) {
                        TToast.show(getApplicationContext(), "广告" + ttNativeAd.getTitle() + "被点击");
                    }
                }

                @Override

                public void onAdCreativeClick(View view, TTNativeAd ttNativeAd) {
                    if (ttNativeAd != null) {
                        TToast.show(getApplicationContext(), "广告" + ttNativeAd.getTitle() + "被创意按钮被点击");
                    }
                }

                @Override

                public void onAdShow(TTNativeAd ttNativeAd) {
                    if (ttNativeAd != null) {
                        TToast.show(getApplicationContext(), "广告" + ttNativeAd.getTitle() + "展示");
                    }
                }
            });
            return new OneAd(container, ttFeedAd.getVideoDuration(), ttFeedAd);
        }


        public OneAd getVideoTypeView(final TTFeedAd ttFeedAd) {
            if (ttFeedAd == null || ttFeedAd.getCustomVideo() == null || TextUtils.isEmpty(ttFeedAd.getCustomVideo().getVideoUrl())) {
                return null;
            }
            VideoView videoView = new MyVideoView(getApplicationContext());
            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (ttFeedAd.getCustomVideo() != null) {
                        ttFeedAd.getCustomVideo().reportVideoFinish();
                    }
                }
            });
            videoView.setVideoURI(Uri.parse(ttFeedAd.getCustomVideo().getVideoUrl()));
            final ViewGroup.LayoutParams layoutParams
                    = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            videoView.setLayoutParams(layoutParams);

            List<View> clickViews = new LinkedList<>();

            clickViews.add(ttFeedAd.getAdView());
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

            return new OneAd(videoView, ttFeedAd.getVideoDuration(), ttFeedAd);
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

        class OneAd {
            View adView;
            double duration;

            TTFeedAd ttFeedAd;


            public OneAd(View adView, double duration, TTFeedAd ttFeedAd) {
                this.adView = adView;
                this.duration = duration;
                this.ttFeedAd = ttFeedAd;
            }

            public void play() {
                if (adView instanceof VideoView) {
                    ((VideoView) adView).start();
                    if (ttFeedAd.getCustomVideo() != null) {
                        ttFeedAd.getCustomVideo().reportVideoAutoStart();
                        // 使用自定义播放器需要上报视频播放的埋点
                        ttFeedAd.getCustomVideo().reportVideoStart();
                    }
                }
            }

            public void stop() {
                if (adView instanceof VideoView) {
                    if (ttFeedAd.getCustomVideo() != null) {
                        // 使用自定义播放器需要上报视频播放结束的埋点
                        ttFeedAd.getCustomVideo().reportVideoBreak(((VideoView) adView).getCurrentPosition());
                    }
                }
            }

            public void pauseVideo() {
                if (adView instanceof VideoView) {
                    ((VideoView) adView).pause();
                    if (ttFeedAd.getCustomVideo() != null) {
                        // 使用自定义播放器需要上报视频暂停埋点
                        ttFeedAd.getCustomVideo().reportVideoPause(((VideoView) adView).getCurrentPosition());
                    }
                }
            }

            public void continueVideo() {
                if (adView instanceof VideoView) {
                    // Android 自带的VideoView继续播放有关键帧问题
                    // 请使用自己的播放器自行处理继续播放的问题
                    ((VideoView) adView).start();

                    if (ttFeedAd.getCustomVideo() != null) {
                        // 使用自定义播放器需要上报视频继续播放埋点
                        ttFeedAd.getCustomVideo().reportVideoContinue(((VideoView) adView).getCurrentPosition());
                    }
                }
            }
        }

        private class MyVideoView extends VideoView {

            public MyVideoView(Context context) {
                super(context);
            }

            public MyVideoView(Context context, AttributeSet attrs) {
                super(context, attrs);
            }

            public MyVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
                super(context, attrs, defStyleAttr);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                /**
                 * 如果使用了系统VideoView 在7.0, 6.0系统上有bug无法响应到点击事件，所以需要特殊处理下
                 */
                if (ev.getAction() == MotionEvent.ACTION_DOWN && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    performClick();
                }
                return super.onTouchEvent(ev);
            }
        }
    }
}
