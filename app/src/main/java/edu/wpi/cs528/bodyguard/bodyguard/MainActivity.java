package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final String TAG = "MainActivity";

    private static final int LOC_PERM_REQ_CODE = 1;
    private GoogleMap mMap;
    private MapView mMapView;
    private Location lastLocation;

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CrimeService.CrimeBinder binder = (CrimeService.CrimeBinder) service;
            crimeService = binder.getService();
            crimeService.startTrackingLocation(updaterListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            crimeService.removeLocationUpdateListener(updaterListener);
            crimeService = null;
        }
    };

    private CrimeService crimeService;
    private CrimeService.LocationUpdateListener updaterListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = findViewById(R.id.map);
        mMapView.getMapAsync(this);
        mMapView.onCreate(savedInstanceState);

        updaterListener = new CrimeService.LocationUpdateListener() {
            @Override
            public void onUpdate(Location loc) {
                lastLocation = loc;
                Log.d(TAG, String.format("lat=%f, lon=%f",
                        loc.getLatitude(), loc.getLongitude()));
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.showLastLocationOnMap();
                    }
                };
                runOnUiThread(r);
            }
        };

        Intent crimeServiceIntent = new Intent(this, CrimeService.class);
        bindService(crimeServiceIntent, conn, BIND_AUTO_CREATE);

        // TODO schedule crime spot download
        // i.e. crimeService.schedDownloadAndCluster(PERIOD);
    }

    private void requestLocationAccessPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOC_PERM_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            if (crimeService != null ){
                crimeService.startTrackingLocation(updaterListener);
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        showLastLocationOnMap();
    }

    private void showLastLocationOnMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null && lastLocation != null) {
                mMap.setMyLocationEnabled(true);
                LatLng pt = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                if (mMap != null) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pt, 17.0f));
                }
            }
        } else {
            requestLocationAccessPermission();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
        unbindService(conn);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMapView.onStop();
        updaterListener.mute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
        updaterListener.unMute();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMapView.onStart();
    }
}
