package com.union_test.toutiao.activity;

import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;

public class SplashMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_main);
        Button button = (Button) findViewById(R.id.btn_splash_back);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.btn_mian_splash, CSJSplashActivity.class);
        bindButton(R.id.btn_half_size_splash, CSJSplashActivity.class);
        bindButton(R.id.express_splash_ad, CSJSplashActivity.class);
        bindButton(R.id.horizontal_express_splash_ad, HorizontalSplashActivity.class);
        bindButton(R.id.horizontal_splash_ad, HorizontalSplashActivity.class);
        bindButton(R.id.btn_splash_ad_hand, CSJSplashActivity.class);
        bindButton(R.id.btn_splash_ad_shake, CSJSplashActivity.class);
        bindButton(R.id.btn_splash_ad_twist, CSJSplashActivity.class);
        bindButton(R.id.btn_splash_ad_slideup, CSJSplashActivity.class);
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SplashMainActivity.this, clz);
                //开屏代码位id
                if (v.getId() == R.id.btn_mian_splash) {
                    intent.putExtra("splash_rit", "801121648");
                    intent.putExtra("is_express", false);
                    intent.putExtra("is_half_size", false);
                }

                //半全屏开屏代码位id
                if (v.getId() == R.id.btn_half_size_splash) {
                    intent.putExtra("splash_rit", "801121648");
                    intent.putExtra("is_express", false);
                    intent.putExtra("is_half_size", true);
                }

                //开屏模板代码位id
                if (v.getId() == R.id.express_splash_ad) {
                    intent.putExtra("splash_rit", "801121974");
                    intent.putExtra("is_express", true);
                }

                //横版模版开屏代码位id
                if (v.getId() == R.id.horizontal_express_splash_ad) {
                    intent.putExtra("splash_rit", "887631026");
                    intent.putExtra("is_express", true);
                }

                //横版开屏代码位id
                if (v.getId() == R.id.horizontal_splash_ad) {
                    intent.putExtra("splash_rit", "887654027");
                    intent.putExtra("is_express", false);
                }

                //摇一摇代码位id
                if (v.getId() == R.id.btn_splash_ad_shake) {
                    intent.putExtra("splash_rit", "888041256");
                    intent.putExtra("is_express", false);
                }

                //小手代码位id
                if (v.getId() == R.id.btn_splash_ad_hand) {
                    intent.putExtra("splash_rit", "888041254");
                    intent.putExtra("is_express", false);
                }

                //扭一扭代码位id
                if (v.getId() == R.id.btn_splash_ad_twist) {
                    intent.putExtra("splash_rit", "888041255");
                    intent.putExtra("is_express", false);
                }

                //上滑代码位id
                if (v.getId() == R.id.btn_splash_ad_slideup) {
                    intent.putExtra("splash_rit", "888041257");
                    intent.putExtra("is_express", false);
                }

                startActivity(intent);
            }
        });
    }
}
