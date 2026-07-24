package com.qingxujifen.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CsjAdPlugin.class);
        super.onCreate(savedInstanceState);
    }
    
    public void performRiskCheckFromFrontend() {
        RiskDetector.RiskResult result = RiskDetector.checkAllRisks(this);
        if (result.hasRisk) {
        }
    }
}
