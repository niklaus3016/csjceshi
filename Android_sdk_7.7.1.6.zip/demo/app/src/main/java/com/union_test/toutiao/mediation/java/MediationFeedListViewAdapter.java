package com.union_test.toutiao.mediation.java;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTFeedAd;
import com.bytedance.sdk.openadsdk.TTNativeAd;
import com.bytedance.sdk.openadsdk.mediation.manager.MediationNativeManager;
import com.union_test.toutiao.R;
import com.union_test.toutiao.mediation.java.utils.Const;
import com.union_test.toutiao.mediation.java.utils.FeedAdUtils;

import java.util.List;

public class MediationFeedListViewAdapter extends BaseAdapter {

    private static final int ITEM_VIEW_TYPE_NORMAL = 0;         // 开发者App内容
    private static final int ITEM_VIEW_TYPE_GROUP_PIC_AD = 1;   // 组图
    private static final int ITEM_VIEW_TYPE_SMALL_PIC_AD = 2;   // 小图
    private static final int ITEM_VIEW_TYPE_LARGE_PIC_AD = 3;   // 大图
    private static final int ITEM_VIEW_TYPE_VIDEO = 4;          // 视频
    private static final int ITEM_VIEW_TYPE_VERTICAL_IMG = 5;   // 竖版图片
    private static final int ITEM_VIEW_TYPE_VIDEO_VERTICAL = 6; // 竖版视频
    private static final int ITEM_VIEW_TYPE_EXPRESS_AD = 7;     // 竖版视频
    private static final int ITEM_VIEW_TYPE_CREATIVE_EFFECT = 8;  // 创意素材
    private static final int ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT = 9;  // 创意素材

    private static final int ITEM_VIEW_TYPE_UNKNOW = 10;     // 未知



    private final List<TTFeedAd> mData;
    private final Activity mActivity;


    public MediationFeedListViewAdapter(Activity activity, List<TTFeedAd> data) {
        mData = data;
        mActivity = activity;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override

    public TTFeedAd getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        TTFeedAd ad = getItem(position);
        if (ad == null) {
            // 返回开发者App的内容view
            return getNormalView(position, convertView, parent);
        } else {
            // 返回广告view
            MediationNativeManager manager = ad.getMediationManager();
            if (manager != null && manager.isExpress()) { // 模板广告，直接获取adView展示

                return ad.getAdView();

            } else {                                      // 自渲染广告，需开发者自己将素材渲染成view


                return FeedAdUtils.getFeedAdFromFeedInfo(ad, mActivity, convertView, new TTNativeAd.AdInteractionListener() {
                    @Override

                    public void onAdClicked(View view, TTNativeAd ttNativeAd) {
                        Log.d(Const.TAG, "listview feed click");
                    }

                    @Override

                    public void onAdCreativeClick(View view, TTNativeAd ttNativeAd) {
                        Log.d(Const.TAG, "listview feed creative click");
                    }

                    @Override

                    public void onAdShow(TTNativeAd ttNativeAd) {
                        Log.d(Const.TAG, "listview feed show");
                    }
                });
            }
        }
    }

    @Override
    public int getViewTypeCount() {
        return 11;
    }

    @Override
    public int getItemViewType(int position) {

        TTFeedAd ad = getItem(position);
        if (ad == null) {
            return ITEM_VIEW_TYPE_NORMAL;
        } else {
            MediationNativeManager manager = ad.getMediationManager();
            if (manager != null && manager.isExpress()) {
                return ITEM_VIEW_TYPE_EXPRESS_AD;
            } else {
                switch (ad.getImageMode()) {

                    case TTAdConstant.IMAGE_MODE_SMALL_IMG:
                        return ITEM_VIEW_TYPE_SMALL_PIC_AD;

                    case TTAdConstant.IMAGE_MODE_LARGE_IMG:
                        return ITEM_VIEW_TYPE_LARGE_PIC_AD;

                    case TTAdConstant.IMAGE_MODE_GROUP_IMG:
                        return ITEM_VIEW_TYPE_GROUP_PIC_AD;

                    case TTAdConstant.IMAGE_MODE_VIDEO : //视频
                        return ITEM_VIEW_TYPE_VIDEO;

                    case TTAdConstant.IMAGE_MODE_VERTICAL_IMG:
                        return ITEM_VIEW_TYPE_VERTICAL_IMG;

                    case TTAdConstant.IMAGE_MODE_VIDEO_VERTICAL: //视频
                        return ITEM_VIEW_TYPE_VIDEO_VERTICAL;

                    case TTAdConstant.IMAGE_MODE_CREATIVE_EFFECT: //创意素材
                        return ITEM_VIEW_TYPE_CREATIVE_EFFECT;

                    case TTAdConstant.IMAGE_MODE_VERTICAL_CREATIVE_EFFECT: //创意素材
                        return ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT;

                    default:
                        return ITEM_VIEW_TYPE_UNKNOW;
                }
            }
        }
    }

    // 开发者自己的内容view
    @SuppressLint("SetTextI18n")
    private View getNormalView(int position, View convertView, ViewGroup parent) {
        NormalViewHolder normalViewHolder;
        if (convertView == null) {
            normalViewHolder = new NormalViewHolder();
            convertView = LayoutInflater.from(mActivity).inflate(R.layout.listitem_normal, parent, false);
            normalViewHolder.idle = (TextView) convertView.findViewById(R.id.text_idle);
            convertView.setTag(normalViewHolder);
        } else {
            normalViewHolder = (NormalViewHolder) convertView.getTag();
        }
        normalViewHolder.idle.setText("ListView item " + position);
        return convertView;
    }

    private class NormalViewHolder {
        TextView idle;
    }
}
