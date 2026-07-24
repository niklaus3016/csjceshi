package com.union_test.toutiao;

import android.util.Log;
import com.bytedance.sdk.openadsdk.AdSlot;
import com.bytedance.sdk.openadsdk.TTAdNative;
import com.bytedance.sdk.openadsdk.TTAppContextHolder;
import com.bytedance.sdk.openadsdk.TTRewardVideoAd;
import com.union_test.toutiao.config.TTAdManagerHolder;
import static org.junit.Assert.*;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExpressRewardVideoTestCase extends BaseTestCase {
    String loadMethodName;
    Boolean isInitSuccess = false;

    @Test
    public void loadRewardVideoAd() throws InterruptedException {
        while (!isInitSuccess){

        }
        CountDownLatch loadLatch = new CountDownLatch(2);
        TTAdNative mTTAdNative;
        AdSlot adSlot = new AdSlot.Builder()
                .setCodeId("901121593")
                .build();
        Log.d("CSJUnitTest", "reward video get context: " + TTAppContextHolder.getContext());
        mTTAdNative = TTAdManagerHolder.get().createAdNative(TTAppContextHolder.getContext());
        mTTAdNative.loadRewardVideoAd(adSlot, new TTAdNative.RewardVideoAdListener() {
            @Override
            public void onError(int code, String message) {
                Log.d("CSJUnitTest", "reward video load error: " + message);
            }

            @Override
            public void onRewardVideoAdLoad(TTRewardVideoAd ad) {
                Log.d("CSJUnitTest", "reward video load success" );
                loadMethodName = Thread.currentThread().getStackTrace()[2].getMethodName();
                loadLatch.countDown();
            }

            @Override
            public void onRewardVideoCached() {
                Log.d("CSJUnitTest", "reward video cached" );
            }

            @Override
            public void onRewardVideoCached(TTRewardVideoAd ad) {
                Log.d("CSJUnitTest", "reward video cached" );
            }
        });
        try {
            loadLatch.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.d("CSJUnitTest", "reward video load: " + e.toString());
        }
        assertEquals("onRewardVideoAdLoad", loadMethodName);
        assertTrue(" loadLatch should be 1 but is " + loadLatch.getCount(), loadLatch.getCount() == 1);
    }

    @Override
    void onSuccess() {
        isInitSuccess = true;
    }

    @Override
    void onFail() {
        isInitSuccess = false;
    }
}
