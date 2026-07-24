package com.gaoqianleme.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    
    public void performRiskCheckFromFrontend() {
        RiskDetector.RiskResult result = RiskDetector.checkAllRisks(this);
        if (result.hasRisk) {
            // 暂时注释掉进程杀死逻辑，便于测试
            // android.os.Process.killProcess(android.os.Process.myPid());
            // System.exit(0);
        }
    }
}
