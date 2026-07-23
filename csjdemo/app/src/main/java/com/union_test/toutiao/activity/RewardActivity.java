package com.union_test.toutiao.activity;

import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;

public class RewardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reward);
        findViewById(R.id.btn_reward_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.btn_main_reward, RewardVideoActivity.class);
        bindButton(R.id.express_rewarded_video_ad, RewardVideoActivity.class);
    }
    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RewardActivity.this, clz);
                //激励视频代码位id
                if (v.getId() == R.id.btn_main_reward) {
                    intent.putExtra("horizontal_rit","901121430");
                    intent.putExtra("vertical_rit","901121365");
                }
                //激励模板视频代码位id
                if (v.getId() == R.id.express_rewarded_video_ad) {
                    intent.putExtra("horizontal_rit", "901121543");
                    intent.putExtra("vertical_rit", "901121593");
                }
                startActivity(intent);
            }
        });
    }
}
