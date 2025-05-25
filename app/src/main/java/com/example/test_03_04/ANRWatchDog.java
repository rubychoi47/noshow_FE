package com.example.test_03_04;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class ANRWatchDog extends Thread {
    private static final int ANR_TIMEOUT = 5000;
    private boolean isRunning = true;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void run() {
        while (isRunning) {
            long startTime = System.currentTimeMillis();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 메인 스레드가 응답하는지 확인
                }
            });
            
            try {
                Thread.sleep(ANR_TIMEOUT);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            if (System.currentTimeMillis() - startTime > ANR_TIMEOUT) {
                Log.e("ANR", "Application Not Responding");
            }
        }
    }
} 