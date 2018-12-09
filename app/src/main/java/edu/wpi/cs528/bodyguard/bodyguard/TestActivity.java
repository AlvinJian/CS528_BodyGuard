package edu.wpi.cs528.bodyguard.bodyguard;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.model.LatLng;

public class TestActivity extends AppCompatActivity {
    private final String TAG = "TestActivity";
    private final long PERIOD = 900000;

    private CrimeService crimeService;
    private CrimeService.DataUpdateListener updaterListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        findViewById(R.id.start_crime_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent crimeServiceIntent = new Intent(TestActivity.this,
                        CrimeService.class);
                bindService(crimeServiceIntent, conn, BIND_AUTO_CREATE);

            }
        });

        findViewById(R.id.stop_crime_service).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (crimeService != null) {
                    crimeService.stopTrackingLocation();
                    TestActivity.this.unbindService(conn);
                    crimeService = null;
                }
            }
        });

        Log.d(TAG, "onCreate is done");
        updaterListener = new CrimeService.DataUpdateListener() {
            @Override
            public void onLocationUpdate(final Location loc) {
                Log.d(TAG, String.format("lat=%f, lon=%f",
                        loc.getLatitude(), loc.getLongitude()));
                if (crimeService!= null && !crimeService.isDownloadSched())
                {
                    crimeService.schedDownloadAndCluster(PERIOD);
                }
            }

            @Override
            public void onClusterUpdate(final LatLng[] pts) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        // TODO add markers in this runnable
                        if (pts != null) {
                            for (Parcelable pa: pts) {
                                LatLng latlng = (LatLng) pa;
                                Log.i(TAG, latlng.toString());
                            }
                        }
                    }
                };
                runOnUiThread(r);
            }
        };
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CrimeService.CrimeBinder binder = (CrimeService.CrimeBinder) service;
            crimeService = binder.getService();
            crimeService.startTrackingLocation(updaterListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        updaterListener.mute();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updaterListener.unMute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (crimeService != null) unbindService(conn);
    }
}
