package com.union_test.toutiao.activity;

import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;

public class FullScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen);
        findViewById(R.id.btn_fullscreen_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.btn_main_full, FullScreenVideoActivity.class);
        bindButton(R.id.express_full_screen_video_ad, FullScreenVideoActivity.class);
        getExtraInfo();
    }

    private void getExtraInfo() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        Button fullBtn = findViewById(R.id.btn_main_full);
        Button expressFullBtn = findViewById(R.id.express_full_screen_video_ad);
        fullBtn.setVisibility(View.VISIBLE);
        expressFullBtn.setVisibility(View.VISIBLE);
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FullScreenActivity.this, clz);
                //全屏视频代码位id
                if (v.getId() == R.id.btn_main_full) {
                    intent.putExtra("horizontal_rit","901121184");
                    intent.putExtra("vertical_rit","901121375");
                }
                //全屏模板视频代码位id
                if (v.getId() == R.id.express_full_screen_video_ad) {
                    intent.putExtra("horizontal_rit", "901121516");
                    intent.putExtra("vertical_rit", "901121073");
                }
                startActivity(intent);
            }
        });
    }
}
