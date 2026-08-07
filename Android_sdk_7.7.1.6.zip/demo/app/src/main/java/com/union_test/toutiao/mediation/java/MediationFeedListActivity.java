package com.union_test.toutiao.mediation.java;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;
import com.union_test.toutiao.activity.NativeEcMallActivity;

public class MediationFeedListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediation_feed);
        Button button = (Button)findViewById(R.id.btn_FD_back);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.btn_main_feed_lv, MediationFeedActivity.class);

        bindButton(R.id.native_btn_ec_mall, NativeEcMallActivity.class);
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediationFeedListActivity.this, clz);
                startActivity(intent);
            }
        });
    }
}
