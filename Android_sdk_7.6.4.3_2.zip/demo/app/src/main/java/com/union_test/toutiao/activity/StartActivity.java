package com.union_test.toutiao.activity;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.union_test.toutiao.R;
import com.union_test.toutiao.config.TTAdManagerHolder;

public class StartActivity extends Activity {
    private Button mBtnInit;
    private Button mBtnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediation_start);
        mBtnInit = findViewById(R.id.btn_init);
        mBtnStart = findViewById(R.id.btn_start);


        if(TTAdSdk.isSdkReady()){
            TTAdManagerHolder.startActivity(this);
        }

        mBtnInit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //调用初始化
                TTAdManagerHolder.init(StartActivity.this.getApplicationContext());
            }
        });
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //开始展示广告
                TTAdManagerHolder.start(StartActivity.this);
            }
        });
    }
}
