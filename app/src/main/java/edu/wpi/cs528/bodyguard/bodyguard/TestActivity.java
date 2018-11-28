package edu.wpi.cs528.bodyguard.bodyguard;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class TestActivity extends AppCompatActivity {
    private final String TAG = "TestActivity";
    private final long PERIOD = 900000;

    private CrimeService crimeService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        findViewById(R.id.start_crime_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (crimeService == null) {
                    Intent crimeServiceIntent = new Intent(TestActivity.this,
                            CrimeService.class);
                    bindService(crimeServiceIntent, conn, BIND_AUTO_CREATE);
                } else {
                    crimeService.schedDownloadAndCluster(PERIOD);
                }

            }
        });

        findViewById(R.id.stop_crime_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (crimeService != null) {
                    TestActivity.this.unbindService(conn);
                    crimeService = null;
                }
            }
        });

        Log.d(TAG, "onCreate is done");
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CrimeService.CrimeBinder binder = (CrimeService.CrimeBinder) service;
            crimeService = binder.getService();
            crimeService.schedDownloadAndCluster(PERIOD);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            crimeService = null;
        }
    };
}
