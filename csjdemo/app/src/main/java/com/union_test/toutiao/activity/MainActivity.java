package com.union_test.toutiao.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;

import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import com.bytedance.sdk.openadsdk.ExitInstallListener;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;
import com.union_test.toutiao.utils.SplashCardManager;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView.setWebContentsDebuggingEnabled(true);
        setContentView(R.layout.activity_main);
        TextView tvVersion = findViewById(R.id.tv_version);
        String ver = getString(R.string.main_sdk_version_tip, TTAdManagerHolder.get().getSDKVersion());
        tvVersion.setText(ver);

        bindButton(R.id.btn_main_feed, FeedActivity.class);
        bindButton(R.id.btn_main_draw, DrawActivity.class);
        bindButton(R.id.btn_main_banner, BannerActivity.class);
        bindButton(R.id.btn_main_Splash, SplashMainActivity.class);
        bindButton(R.id.btn_main_Reward, RewardActivity.class);
        bindButton(R.id.btn_main_fullscreen, FullScreenActivity.class);
        bindButton(R.id.btn_main_full_interaction, NewInteractionActivity.class);
        bindButton(R.id.btn_main_stream, StreamCustomPlayerActivity.class);
        bindButton(R.id.btn_waterfall, NativeWaterfallActivity.class);
//        bindButton(R.id.btn_adapter, AdapterActivity.class);
        bindButton(R.id.btn_test_tool, AllTestToolActivity.class);
       // 申请部分权限,建议在sdk初始化前申请,如：READ_PHONE_STATE、ACCESS_COARSE_LOCATION及ACCESS_FINE_LOCATION权限，
        // 以获取更好的广告推荐效果，如read_phone_state,防止获取不了imei时候，下载类广告没有填充的问题。
        TTAdManagerHolder.get().requestPermissionIfNecessary(this);

        // 开屏卡片
        SplashCardManager.getInstance().showBetweenActivityCard(this, new SplashCardManager.Callback() {
            @Override
            public void onStart() {
                // 当动画开始时回调，您可以在此处理渲染卡片背后的界面等操作
            }

            @Override
            public void onClose() {
                // 当卡片关闭时回调，您可以在这里处理Activity的关闭操作等
            }
        });
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, clz);
                startActivity(intent);
            }
        });
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        boolean isShowInstallDialog = TTAdManagerHolder.get().tryShowInstallDialogWhenExit(this, new ExitInstallListener() {
            @Override
            public void onExitInstall() {
                finish();
//                System.exit(0);
            }
        });

        if (!isShowInstallDialog) {
            //没有弹出安装对话框时交由系统处理或者自己的业务逻辑
            super.onBackPressed();
//            System.exit(0);
        }
    }

}


