package com.union_test.toutiao.activity;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.live.EcMallPagerAdapter;
import com.union_test.toutiao.live.ListMallFragment;

/**
 * 商城页面使用Demo
 */
public class NativeEcMallActivity extends AppCompatActivity {
    private static final String TAG = "NativeEcMallActivity";
    private Context mContext;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ec_mall);
        mContext = this.getApplicationContext();
        //step3:(可选，强烈建议在合适的时机调用):申请部分权限，如read_phone_state,防止获取不了imei时候，下载类广告没有填充的问题。
        TTAdManagerHolder.get().requestPermissionIfNecessary(this);
        findViewById(R.id.btn_ane_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViewById(R.id.btn_mall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(0);
            }
        });

        findViewById(R.id.btn_test_page).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(1);
            }
        });

        EcMallPagerAdapter adapter = new EcMallPagerAdapter(getSupportFragmentManager());
        viewPager = findViewById(com.bytedance.tools.R.id.view_pager);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {
            }

            @Override
            public void onPageSelected(int position) {
                pageSelected(position);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });
    }

    private void pageSelected(int position) {
        switch (position) {
            case 0:
                // 商城列表页
                Toast.makeText(this, "商城列表页", Toast.LENGTH_SHORT).show();
                break;
            case 1:
                // 全局配置页
                Toast.makeText(this, "其他页面", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }


}
