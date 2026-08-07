package com.union_test.toutiao.mediation.java;

import android.app.Activity;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bytedance.sdk.openadsdk.ComplianceInfo;
import com.bytedance.sdk.openadsdk.TTAdConstant;
import com.bytedance.sdk.openadsdk.TTAdDislike;
import com.bytedance.sdk.openadsdk.TTFeedAd;
import com.bytedance.sdk.openadsdk.TTNativeAd;
import com.bytedance.sdk.openadsdk.mediation.manager.MediationNativeManager;
import com.union_test.toutiao.R;
import com.union_test.toutiao.mediation.java.utils.Const;
import com.union_test.toutiao.utils.UIUtils;
import com.union_test.toutiao.view.LoadMoreView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MediationFeedRecyclerViewAdapter extends RecyclerView.Adapter {

    private static final int FOOTER_VIEW_COUNT = 1;

    private static final int ITEM_VIEW_TYPE_LOAD_MORE = -1;     // loadMore
    private static final int ITEM_VIEW_TYPE_NORMAL = 0;         // 开发者App内容
    private static final int ITEM_VIEW_TYPE_GROUP_PIC_AD = 1;   // 组图
    private static final int ITEM_VIEW_TYPE_SMALL_PIC_AD = 2;   // 小图
    private static final int ITEM_VIEW_TYPE_LARGE_PIC_AD = 3;   // 大图
    private static final int ITEM_VIEW_TYPE_VIDEO = 4;          // 视频
    private static final int ITEM_VIEW_TYPE_VERTICAL_IMG = 5;   // 竖版图片
    private static final int ITEM_VIEW_TYPE_VIDEO_VERTICAL = 6; // 竖版视频
    private static final int ITEM_VIEW_TYPE_EXPRESS_AD = 7;     // 竖版视频
    private static final int ITEM_VIEW_TYPE_CREATIVE_EFFECT = 8;          //创意素材
    private static final int ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT = 9;          //创意素材



    private final List<TTFeedAd> mData;
    private final Activity mActivity;


    public MediationFeedRecyclerViewAdapter(Activity activity, List<TTFeedAd> data) {
        mData = data;
        mActivity = activity;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case ITEM_VIEW_TYPE_LOAD_MORE:
                return new LoadMoreViewHolder(new LoadMoreView(mActivity));
            case ITEM_VIEW_TYPE_SMALL_PIC_AD:
                return new SmallAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_small_pic, parent, false));
            case ITEM_VIEW_TYPE_LARGE_PIC_AD:
                return new LargeAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_large_pic, parent, false));
            case ITEM_VIEW_TYPE_GROUP_PIC_AD:
                return new GroupAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_group_pic, parent, false));
            case ITEM_VIEW_TYPE_VIDEO:
            case ITEM_VIEW_TYPE_VIDEO_VERTICAL:
                return new VideoAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_large_video, parent, false));
            case ITEM_VIEW_TYPE_VERTICAL_IMG:
                return new VerticalAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_vertical_pic, parent, false));
            case ITEM_VIEW_TYPE_EXPRESS_AD:
                return new ExpressAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.listitem_ad_express, parent, false));
            case ITEM_VIEW_TYPE_CREATIVE_EFFECT:
            case ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT:
                return new CreativeEffectAdViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.mediation_listitem_ad_creative_effect, parent, false));
            default:
                return new NormalViewHolder(LayoutInflater.from(mActivity).inflate(R.layout.listitem_normal, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        switch (getItemViewType(position)) {
            case ITEM_VIEW_TYPE_SMALL_PIC_AD:
            case ITEM_VIEW_TYPE_LARGE_PIC_AD:
            case ITEM_VIEW_TYPE_GROUP_PIC_AD:
            case ITEM_VIEW_TYPE_CREATIVE_EFFECT:
            case ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT:
            case ITEM_VIEW_TYPE_VIDEO:
            case ITEM_VIEW_TYPE_VIDEO_VERTICAL:
            case ITEM_VIEW_TYPE_VERTICAL_IMG:
                if (viewHolder instanceof AdViewHolder) {
                    AdViewHolder adViewHolder = (AdViewHolder) viewHolder;
                    bindData(adViewHolder.itemView, adViewHolder, mData.get(position), mActivity);
                }
                break;
            case ITEM_VIEW_TYPE_EXPRESS_AD:
                if (viewHolder instanceof ExpressAdViewHolder) {
                    ExpressAdViewHolder expressAdViewHolder = (ExpressAdViewHolder) viewHolder;

                    TTFeedAd ad = mData.get(position);
                    UIUtils.removeFromParent(ad.getAdView());
                    expressAdViewHolder.expressViewContainer.removeAllViews();
                    expressAdViewHolder.expressViewContainer.addView(ad.getAdView());
                }
                break;
            case ITEM_VIEW_TYPE_LOAD_MORE:
                if (viewHolder instanceof LoadMoreViewHolder) {
                    LoadMoreViewHolder loadMoreAdViewHolder = (LoadMoreViewHolder) viewHolder;
                }
                break;
            default:
                if (viewHolder instanceof NormalViewHolder) {
                    NormalViewHolder normalViewHolder = (NormalViewHolder) viewHolder;
                    normalViewHolder.idle.setText("Recycler item " + position);
                }
        }
    }

    @Override
    public int getItemCount() {
        int count = mData == null ? 0 : mData.size();
        return count + FOOTER_VIEW_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        if (mData != null) {
            if (position >= mData.size()) {
                return ITEM_VIEW_TYPE_LOAD_MORE;
            } else {

                TTFeedAd ad = mData.get(position);
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

                            case TTAdConstant.IMAGE_MODE_VIDEO: //视频
                                return ITEM_VIEW_TYPE_VIDEO;

                            case TTAdConstant.IMAGE_MODE_VERTICAL_IMG:
                                return ITEM_VIEW_TYPE_VERTICAL_IMG;

                            case TTAdConstant.IMAGE_MODE_VIDEO_VERTICAL: //视频
                                return ITEM_VIEW_TYPE_VIDEO_VERTICAL;

                            case TTAdConstant.IMAGE_MODE_CREATIVE_EFFECT: //创意素材
                                return ITEM_VIEW_TYPE_CREATIVE_EFFECT;

                            case TTAdConstant.IMAGE_MODE_VERTICAL_CREATIVE_EFFECT: //创意素材
                                return ITEM_VIEW_TYPE_VERTICAL_CREATIVE_EFFECT;
                        }
                    }
                }
            }
        }
        return super.getItemViewType(position);
    }


    private static void bindData(View convertView, AdViewHolder adViewHolder, TTFeedAd ad, Activity activity) {
        // 设置dislike弹窗，如果有
        if (ad.getMediationManager().hasDislike()) {

            final TTAdDislike ttAdDislike = ad.getDislikeDialog(activity);
            adViewHolder.mDislike.setVisibility(View.VISIBLE);
            adViewHolder.mDislike.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ttAdDislike.showDislikeDialog();
                }
            });
        } else {
            if (adViewHolder.mDislike != null) adViewHolder.mDislike.setVisibility(View.GONE);
        }
        // 下载类广告合规五要素信息
        setDownLoadAppInfo(ad, adViewHolder);

        // 可以被点击的view, 也可以把convertView放进来意味item可被点击
        ArrayList<View> clickViewList = new ArrayList<>();
        clickViewList.add(convertView);
        clickViewList.add(adViewHolder.mSource);
        clickViewList.add(adViewHolder.mTitle);
        clickViewList.add(adViewHolder.mDescription);
        clickViewList.add(adViewHolder.mIcon);
        // 添加点击区域
        if (adViewHolder instanceof LargeAdViewHolder) {
            clickViewList.add(((LargeAdViewHolder) adViewHolder).mLargeImage);
            if (ad.getImageList() != null && ad.getImageList().size() > 0) {
                Glide.with(activity).load(ad.getImageList().get(0).getImageUrl()).into(((LargeAdViewHolder) adViewHolder).mLargeImage);
            }
        } else if (adViewHolder instanceof SmallAdViewHolder) {
            clickViewList.add(((SmallAdViewHolder) adViewHolder).mSmallImage);
            if (ad.getImageList() != null && ad.getImageList().size() > 0) {
                Glide.with(activity).load(ad.getImageList().get(0).getImageUrl()).into(((SmallAdViewHolder) adViewHolder).mSmallImage);
            }
        } else if (adViewHolder instanceof VerticalAdViewHolder) {
            clickViewList.add(((VerticalAdViewHolder) adViewHolder).mVerticalImage);
            if (ad.getImageList() != null && ad.getImageList().size() > 0) {
                Glide.with(activity).load(ad.getImageList().get(0).getImageUrl()).into(((VerticalAdViewHolder) adViewHolder).mVerticalImage);
            }
        } else if (adViewHolder instanceof VideoAdViewHolder) {
            clickViewList.add(((VideoAdViewHolder) adViewHolder).videoView);
            //展示视频view
            View adView = ad.getAdView();
            if (adView != null && ((VideoAdViewHolder) adViewHolder).videoView != null) {
                removeSelfFromParent(adView);
                ((VideoAdViewHolder) adViewHolder).videoView.removeAllViews();
                ((VideoAdViewHolder) adViewHolder).videoView.addView(adView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            }
        } else if (adViewHolder instanceof CreativeEffectAdViewHolder) {
            clickViewList.add(((CreativeEffectAdViewHolder) adViewHolder).mCreativeEffectView);
            //展示视频view
            View adView = ad.getAdView();
            if (adView != null && ((CreativeEffectAdViewHolder) adViewHolder).mCreativeEffectView != null) {
                removeSelfFromParent(adView);
                ((CreativeEffectAdViewHolder) adViewHolder).mCreativeEffectView.removeAllViews();
                ((CreativeEffectAdViewHolder) adViewHolder).mCreativeEffectView.addView(adView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            }
        } else if (adViewHolder instanceof GroupAdViewHolder) {
            clickViewList.add(((GroupAdViewHolder) adViewHolder).mGroupImage1);
            clickViewList.add(((GroupAdViewHolder) adViewHolder).mGroupImage2);
            clickViewList.add(((GroupAdViewHolder) adViewHolder).mGroupImage3);
            if (ad.getImageList() != null && ad.getImageList().size() >= 3) {
                String image1 = ad.getImageList().get(0).getImageUrl();
                String image2 = ad.getImageList().get(1).getImageUrl();
                String image3 = ad.getImageList().get(2).getImageUrl();
                if (image1 != null) {
                    Glide.with(activity).load(image1).into(((GroupAdViewHolder) adViewHolder).mGroupImage1);
                }
                if (image2 != null) {
                    Glide.with(activity).load(image2).into(((GroupAdViewHolder) adViewHolder).mGroupImage2);
                }
                if (image3 != null) {
                    Glide.with(activity).load(image3).into(((GroupAdViewHolder) adViewHolder).mGroupImage3);
                }
            }
        }
        // 触发创意广告的view（点击下载或拨打电话）
        ArrayList<View> creativeViewList = new ArrayList<>();
        creativeViewList.add(adViewHolder.mCreativeButton);

        // 重要! 这个涉及到广告计费，必须正确调用。
        ad.registerViewForInteraction((ViewGroup) convertView, null, clickViewList, creativeViewList,
                null, getInteractionListener());

        adViewHolder.mTitle.setText(ad.getTitle()); // title为广告的简单信息提示
        adViewHolder.mDescription.setText(ad.getDescription()); // description为广告的较长的说明
        adViewHolder.mSource.setText(TextUtils.isEmpty(ad.getSource()) ? "" : ad.getSource());
        String icon = ad.getIcon() != null ? ad.getIcon().getImageUrl() : null;
        if (!TextUtils.isEmpty(icon)) {
            Glide.with(activity).load(icon).into(adViewHolder.mIcon);
        }

        //添加logo
        if (adViewHolder.mLogo != null) {
            Bitmap logoBitMap = ad.getAdLogo();
            if (logoBitMap != null) {
                adViewHolder.mLogo.removeAllViews();
                adViewHolder.mLogo.setVisibility(View.VISIBLE);
                ImageView logo = new ImageView(activity);
                logo.setImageBitmap(logoBitMap);
                logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ViewGroup.LayoutParams logolayoutPl = adViewHolder.mLogo.getLayoutParams();
                logolayoutPl.width = UIUtils.dp2px(activity, 38);
                logolayoutPl.height = UIUtils.dp2px(activity, 38);
                adViewHolder.mLogo.setLayoutParams(logolayoutPl);
                adViewHolder.mLogo.addView(logo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            } else {
                adViewHolder.mLogo.setVisibility(View.GONE);
            }
        }

        Button adCreativeButton = adViewHolder.mCreativeButton;
        switch (ad.getInteractionType()) {

            case TTAdConstant.INTERACTION_TYPE_DOWNLOAD:
                adCreativeButton.setVisibility(View.VISIBLE);
                adCreativeButton.setText(TextUtils.isEmpty(ad.getButtonText()) ? "立即下载" : ad.getButtonText());
                break;

            case TTAdConstant.INTERACTION_TYPE_DIAL:
                adCreativeButton.setVisibility(View.VISIBLE);
                adCreativeButton.setText("立即拨打");
                break;

            case TTAdConstant.INTERACTION_TYPE_LANDING_PAGE:

            case TTAdConstant.INTERACTION_TYPE_BROWSER:
                adCreativeButton.setVisibility(View.VISIBLE);
                adCreativeButton.setText(TextUtils.isEmpty(ad.getButtonText()) ? "查看详情" : ad.getButtonText());
                break;
            default:
                adCreativeButton.setVisibility(View.GONE);
                Toast.makeText(activity, "交互类型异常", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    /**
     * 从父 view 中移除自己
     *
     * @param child
     */
    public static void removeSelfFromParent(View child) {
        try {
            if (child != null) {
                ViewParent parent = child.getParent();
                if (parent != null && parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(child);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static TTNativeAd.AdInteractionListener getInteractionListener() {

        return new TTNativeAd.AdInteractionListener() {
            @Override

            public void onAdClicked(View view, TTNativeAd ttNativeAd) {
                Log.d(Const.TAG, "recyclerview feed click");
            }

            @Override

            public void onAdCreativeClick(View view, TTNativeAd ttNativeAd) {
                Log.d(Const.TAG, "recyclerview feed creative click");
            }

            @Override

            public void onAdShow(TTNativeAd ttNativeAd) {
                Log.d(Const.TAG, "recyclerview feed show");
            }
        };
    }


    private static void setDownLoadAppInfo(TTFeedAd ttNativeAd, AdViewHolder adViewHolder) {
        if (adViewHolder == null) {
            return;
        }
        if (ttNativeAd == null || ttNativeAd.getComplianceInfo() == null) {
            adViewHolder.app_info.setVisibility(View.GONE);
        } else {
            adViewHolder.app_info.setVisibility(View.VISIBLE);
            ComplianceInfo appInfo = ttNativeAd.getComplianceInfo();
            adViewHolder.app_name.setText("应用名称：" + appInfo.getAppName());
            adViewHolder.author_name.setText("开发者：" + appInfo.getDeveloperName());
            adViewHolder.privacy_agreement.setText("隐私url：" + appInfo.getPrivacyUrl());
            adViewHolder.version_name.setText("版本号：" + appInfo.getAppVersion());
            adViewHolder.permissions_content.setText("权限内容:" + getPermissionsContent(appInfo.getPermissionsMap()));
        }
    }

    private static String getPermissionsContent(Map<String, String> permissionsMap) {
        if (permissionsMap == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : permissionsMap.entrySet()) {
            String str = entry.getKey() + ", " + entry.getValue();
            stringBuilder.append(str);
        }
        return stringBuilder.toString();
    }

    private static class NormalViewHolder extends RecyclerView.ViewHolder {
        TextView idle;

        public NormalViewHolder(View itemView) {
            super(itemView);

            idle = (TextView) itemView.findViewById(R.id.text_idle);
        }
    }

    private static class ExpressAdViewHolder extends RecyclerView.ViewHolder {
        FrameLayout expressViewContainer;

        public ExpressAdViewHolder(View itemView) {
            super(itemView);

            expressViewContainer = (FrameLayout) itemView.findViewById(R.id.listitem_ad_express);
        }
    }

    private static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        TextView mTextView;
        ProgressBar mProgressBar;

        public LoadMoreViewHolder(View itemView) {
            super(itemView);

            itemView.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));

            mTextView = (TextView) itemView.findViewById(R.id.tv_load_more_tip);
            mProgressBar = (ProgressBar) itemView.findViewById(R.id.pb_load_more_progress);
        }
    }

    private static class VideoAdViewHolder extends AdViewHolder {
        FrameLayout videoView = null;

        public VideoAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            videoView = itemView.findViewById(R.id.iv_listitem_video);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class CreativeEffectAdViewHolder extends AdViewHolder {
        FrameLayout mCreativeEffectView = null;

        public CreativeEffectAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            mCreativeEffectView = itemView.findViewById(R.id.iv_listitem_creative_effect);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class LargeAdViewHolder extends AdViewHolder {
        ImageView mLargeImage = null;

        public LargeAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            mLargeImage = itemView.findViewById(R.id.iv_listitem_image);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class SmallAdViewHolder extends AdViewHolder {
        ImageView mSmallImage = null;

        public SmallAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mSmallImage = itemView.findViewById(R.id.iv_listitem_image);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class VerticalAdViewHolder extends AdViewHolder {
        ImageView mVerticalImage = null;

        public VerticalAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mVerticalImage = itemView.findViewById(R.id.iv_listitem_image);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class GroupAdViewHolder extends AdViewHolder {
        ImageView mGroupImage1 = null;
        ImageView mGroupImage2 = null;
        ImageView mGroupImage3 = null;

        public GroupAdViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.tv_listitem_ad_title);
            mSource = itemView.findViewById(R.id.tv_listitem_ad_source);
            mDescription = itemView.findViewById(R.id.tv_listitem_ad_desc);
            mGroupImage1 = itemView.findViewById(R.id.iv_listitem_image1);
            mGroupImage2 = itemView.findViewById(R.id.iv_listitem_image2);
            mGroupImage3 = itemView.findViewById(R.id.iv_listitem_image3);
            mIcon = itemView.findViewById(R.id.iv_listitem_icon);
            mDislike = itemView.findViewById(R.id.iv_listitem_dislike);
            mCreativeButton = itemView.findViewById(R.id.btn_listitem_creative);
            mLogo = itemView.findViewById(R.id.tt_ad_logo); //logoView 建议传入GroupView类型
            app_info = itemView.findViewById(R.id.app_info);
            app_name = itemView.findViewById(R.id.app_name);
            author_name = itemView.findViewById(R.id.author_name);
            package_size = itemView.findViewById(R.id.package_size);
            permissions_url = itemView.findViewById(R.id.permissions_url);
            permissions_content = itemView.findViewById(R.id.permissions_content);
            privacy_agreement = itemView.findViewById(R.id.privacy_agreement);
            version_name = itemView.findViewById(R.id.version_name);
        }
    }

    private static class AdViewHolder extends RecyclerView.ViewHolder {

        public AdViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        ImageView mIcon = null;
        ImageView mDislike = null;
        Button mCreativeButton = null;
        TextView mTitle = null;
        TextView mDescription = null;
        TextView mSource = null;
        RelativeLayout mLogo = null;
        LinearLayout app_info = null;
        TextView app_name = null;
        TextView author_name = null;
        TextView package_size = null;
        TextView permissions_url = null;
        TextView privacy_agreement = null;
        TextView version_name = null;
        TextView permissions_content = null;
    }
}
