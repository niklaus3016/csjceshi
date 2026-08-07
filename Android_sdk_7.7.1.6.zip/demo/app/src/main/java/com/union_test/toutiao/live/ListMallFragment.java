package com.union_test.toutiao.live;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdInteractionListener;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTFeedAd;
import com.bytedance.sdk.openadsdk.TTNativeAd;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.utils.TToast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 文件名称: ListMallFragment.java
 * 功能描述:
 *
 * @author: bytedance
 * 创建时间: 10/31/24
 * Copyright (C) 2024 bytedance
 */
public class ListMallFragment extends Fragment implements EcMallPagerAdapter.OnPageVisibleListener {

    private static final String TAG = "ListMallFragment";

    private TTAdNative mTTAdNative;
    private FrameLayout mContainer;
    private View adView;
    private Context mContext;

    private TTFeedAd mTTAd;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();

        mTTAdNative = TTAdManagerHolder.get().createAdNative(mContext);
        loadAd();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mContainer = new FrameLayout(getContext());
        return mContainer;
    }

    private void loadAd() {
        //step4:创建广告请求参数AdSlot,具体参数含义参考文档

        AdSlot adSlot = new AdSlot.Builder()
                .setCodeId("960072674") //广告位id,这里需要申请指定对应的广告位置才能加载商城页面，
                .setAdCount(1) //请求广告数量为1到3条
                .supportIconStyle()
                .setUserData(getUserData())
                .setExpressViewAcceptedSize(160,0) //期望模板广告view的size,单位dp
                .build();
        //step5:请求广告，对请求回调的广告作渲染处理

        mTTAdNative.loadFeedAd(adSlot, new TTAdNative.FeedAdListener() {
            @Override
            public void onError(int code, String message) {
                TToast.show(getContext(), "load error : " + code + ", " + message);
            }

            @Override

            public void onFeedAdLoad(List<TTFeedAd> ads) {
                if (ads == null || ads.size() == 0){
                    return;
                }
                mTTAd = ads.get(0);

                adView = mTTAd.getAdView();
                if (adView != null) {
                    bindAdListener(mTTAd);
                    mContainer.addView(adView);
                } else {
                    TToast.show(getContext(), "load error : adView is null");
                }

            }

        });
    }

    private String getUserData(){
        JSONArray jsonArray = new JSONArray();
        JSONObject object = new JSONObject();
        try {
            object.put("name", "auth_reward_gold");
            object.put("value", "3000");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        jsonArray.put(object);

        JSONObject slide = new JSONObject();
        try {
            slide.put("name", "slide_reward_gold");
            slide.put("value", "1000");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        jsonArray.put(slide);
        return jsonArray.toString();
    }


    private void bindAdListener(TTFeedAd ad) {

        ad.setAdInteractionListener(new TTAdInteractionListener() {
            @Override
            public void onAdEvent(int code, Map map) {
                Log.i(TAG, "广告事件回调 --> code：" + code + "，map：" + map);
                if (map != null) {
                    for (Object key : map.keySet()) {
                        Log.i(TAG, "广告事件回调 --> key：" + key + "，value：" + map.get(key));
                    }
                }

                if (code == TTAdConstant.AD_EVENT_AUTH_DOUYIN && map != null) {
                    // 抖音授权成功状态回调, 媒体可以通过map获取抖音openuid用以判断是否下发奖励
                    String openUid = (String) map.get("open_uid");
                    Log.i(TAG, "授权成功 --> uid：" + openUid);
                }


                if (code == TTAdConstant.AD_EVENT_MALL_REACH_BOUND) {
                    Log.i(TAG, "商城页滑动到边界了：" + map);
                }
            }
        });

        // 这里的逻辑同自渲染， 但是 onAdClicked 和 onAdCreativeClick 不会回调，仅回调onAdShow
        //重要! 这个涉及到广告计费，必须正确调用。convertView必须使用ViewGroup。

        mTTAd.registerViewForInteraction(mContainer, mContainer,new TTNativeAd.AdInteractionListener() {
            @Override

            public void onAdClicked(View view, TTNativeAd ad) {
                if (ad != null) {
                    TToast.show(mContext, "广告" + ad.getTitle() + "被点击");
                }
            }

            @Override

            public void onAdCreativeClick(View view, TTNativeAd ad) {
                if (ad != null) {
                    TToast.show(mContext, "广告" + ad.getTitle() + "被创意按钮被点击");
                }
            }

            @Override

            public void onAdShow(TTNativeAd ad) {
                if (ad != null) {
                    TToast.show(mContext, "广告" + ad.getTitle() + "展示");
                }
            }
        });

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTTAd != null) {
            mTTAd.destroy();
        }
    }

    @Override
    public void onPageVisibleChange(boolean visible) {
        if (adView != null) {
            adView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
