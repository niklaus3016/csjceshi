package com.union_test.toutiao.live;


import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 文件名称: ListTestFragment.java
 * 功能描述:
 *
 * @author: bytedance
 * 创建时间: 10/31/24
 * Copyright (C) 2024 bytedance
 */
public class ListTestFragment extends Fragment implements EcMallPagerAdapter.OnPageVisibleListener {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout layout = new FrameLayout(getContext());
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView textView = new TextView(getContext());
        textView.setText("测试页面");
        layout.addView(textView);
        return layout;
    }


    @Override
    public void onPageVisibleChange(boolean visible) {

    }
}
