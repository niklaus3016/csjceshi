package com.union_test.toutiao;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.union_test.toutiao.activity.MainActivity;
import com.union_test.toutiao.mediation.java.MediationMainActivity;
import com.union_test.toutiao.mediation.java.utils.AdUtils;

public class SelectActivity extends Activity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mediation_activity_start);
        Switch aSwitch = findViewById(R.id.s_mediation_open);
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AdUtils.setMediationSwitch(SelectActivity.this, isChecked);
            }
        });
        aSwitch.setChecked(AdUtils.getMediationSwitch(SelectActivity.this));
        findViewById(R.id.bt_csj_ad_show).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(AdUtils.getMediationSwitch(SelectActivity.this)){
                    Intent intent = new Intent(SelectActivity.this, MediationMainActivity.class);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(SelectActivity.this, MainActivity.class);
                    startActivity(intent);
                }
                finish();
            }
        });
    }
}
