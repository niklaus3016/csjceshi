package com.union_test.toutiao;

import android.content.Context;
import android.util.Log;
import androidx.test.core.app.ActivityScenario;
import androidx.test.rule.ActivityTestRule;
import com.bytedance.sdk.openadsdk.TTAdConfig;
import com.bytedance.sdk.openadsdk.TTAdSdk;
import com.bytedance.sdk.openadsdk.TTAppContextHolder;
import com.union_test.toutiao.activity.RewardActivity;

import org.junit.Before;
import org.junit.Rule;

public abstract class BaseTestCase {

    abstract void onSuccess();

    abstract void onFail();

    //注册调起规则
    @Rule
    public ActivityTestRule<RewardActivity> rule = new ActivityTestRule<RewardActivity>(RewardActivity.class);

    @Before
    public void setUp() throws InterruptedException {
        ActivityScenario<RewardActivity> scenario = ActivityScenario.launch(RewardActivity.class);
        scenario.onActivity(activity -> {
            Log.d("CSJUnitTest", "拉起activity成功");
        });
        initSDK(TTAppContextHolder.getContext());
    }


    private static TTAdConfig buildConfig(Context context) {

        return new TTAdConfig.Builder()
                .appId("5001121")
                .debug(true) //测试阶段打开，可以通过日志排查问题，上线时去除该调用
                .supportMultiProcess(false)//是否支持多进程
                .build();
    }

    public void initSDK(Context context) {
        TTAdSdk.init(context, buildConfig(context));
        TTAdSdk.start(new TTAdSdk.Callback() {
            @Override
            public void success() {
                Log.d("CSJUnitTest", "初始化SDK成功");
                onSuccess();
            }

            @Override
            public void fail(int code, String msg) {
                Log.d("CSJUnitTest", "初始化SDK失败");
                onFail();
            }
        });
    }
}
