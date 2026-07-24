package com.union_test.toutiao.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;

/**
 * 新插屏代码位
 */
public class NewInteractionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_interaction);
        findViewById(R.id.btn_fullscreen_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.express_half_interaction_ad, FullScreenVideoActivity.class);
        bindButton(R.id.express_full_interaction_ad, FullScreenVideoActivity.class);
        getExtraInfo();
    }

    private void getExtraInfo() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        Button halfInteractionBtn = findViewById(R.id.express_half_interaction_ad);
        Button fullInteractionBtn = findViewById(R.id.express_full_interaction_ad);
        halfInteractionBtn.setVisibility(View.VISIBLE);
        fullInteractionBtn.setVisibility(View.VISIBLE);
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(NewInteractionActivity.this, clz);
                //新插屏半屏代码位id
                if (v.getId() == R.id.express_half_interaction_ad) {
                    intent.putExtra("horizontal_rit","947934020");
                    intent.putExtra("vertical_rit","947793385");
                    intent.putExtra("is_interaction", true);
                }
                //新插屏全屏代码位id
                if (v.getId() == R.id.express_full_interaction_ad) {
                    intent.putExtra("horizontal_rit","947934073");
                    intent.putExtra("vertical_rit","947747681");
                    intent.putExtra("is_interaction", true);
                }
                startActivity(intent);
            }
        });
    }
}
