package com.union_test.toutiao.live;


import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.ViewGroup;

/**
 * 文件名称: EcMallPagerAdapter.java
 * 功能描述:
 *
 * @author: bytedance
 * 创建时间: 10/31/24
 * Copyright (C) 2024 bytedance
 */
public class EcMallPagerAdapter extends FragmentPagerAdapter {
    OnPageVisibleListener mCurrentFragment;

    public EcMallPagerAdapter(FragmentManager fm) {
        super(fm);
    }


    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        super.setPrimaryItem(container, position, object);
        if (object != mCurrentFragment && object instanceof OnPageVisibleListener) {
            if (mCurrentFragment != null) {
                // 这里通知商城页不可见了
                mCurrentFragment.onPageVisibleChange(false);
            }
            mCurrentFragment = (OnPageVisibleListener) object;
            // 这里通知商城页可见
            mCurrentFragment.onPageVisibleChange(true);
        }
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new ListMallFragment();
            case 1:
                return new ListTestFragment();
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        return 2;
    }

    public interface OnPageVisibleListener {
        void onPageVisibleChange(boolean visible);
    }

}
