package edu.wpi.cs528.bodyguard.bodyguard;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class CrimeService extends Service {
    private static final String TAG = "CrimeService";

    private final HandlerThread workerThread;
    private final Handler clusterHandler;
    private final Runnable clusterRunner;
    private IBinder serviceBinder = new CrimeBinder();
    private Timer httpTimer;

    public CrimeService() {
        workerThread =  new HandlerThread("worker");
        workerThread.start();
        clusterHandler = new Handler(workerThread.getLooper());

        // TODO should we start timer in constructor?
        // schedDownloadAndCluster(100000);

        clusterRunner = new Runnable() {
            @Override
            public void run() {
                CrimeService.this.doCluster();
            }
        };
    }

    public void schedDownloadAndCluster(long period) {
        httpTimer = new Timer("forHTTP");
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "mock download message");
                clusterHandler.post(clusterRunner);
            }
        };
        httpTimer.schedule(task, 10, period);
    }

    private void doCluster() {
        Log.d(TAG, "pretend to do cluster");
        for (int i=0; i<5; ++i) {
            try {
                Thread.sleep(100);
                Log.d(TAG, ".");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        workerThread.quitSafely();
        httpTimer.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    public class CrimeBinder extends Binder {
        CrimeService getService() {
            return CrimeService.this;
        }
    }
}
