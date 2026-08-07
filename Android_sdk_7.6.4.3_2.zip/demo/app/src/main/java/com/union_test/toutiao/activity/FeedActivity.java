package com.union_test.toutiao.activity;

import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.union_test.toutiao.R;

public class FeedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);
        Button button = (Button)findViewById(R.id.btn_FD_back);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bindButton(R.id.btn_main_feed_lv, FeedListActivity.class);
        bindButton(R.id.btn_main_feed_rv, FeedRecyclerActivity.class);
        bindButton(R.id.express_native_ad, NativeExpressActivity.class);
        bindButton(R.id.express_native_ad_list, NativeExpressListActivity.class);
        bindButton(R.id.express_native_icon_ad, NativeExpressIconActivity.class);
        bindButton(R.id.native_btn_ec_mall, NativeEcMallActivity.class);
    }

    private void bindButton(@IdRes int id, final Class clz) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FeedActivity.this, clz);
                startActivity(intent);
            }
        });
    }
}
