package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private final String TAG = "MainActivity";

    private static final int LOC_PERM_REQ_CODE = 1;
    private GoogleMap mMap;
    private MapView mMapView;
    private Location lastLocation = null;

    private static final String GEOFENCE_REQ_ID = "My Geofence";
    private Bundle bundle = new Bundle();
    private static final int GEOFENCE_RADIUS = 50;              //meters
    private GeofencingClient geofencingClient;
    private Map<String, Marker> geoFenceMarkerMap;

    public static final String BROADCAST_ACTION = "edu.wpi.cs528.bodyguard.bodyguard.BROADCAST";
    //phone number input part
    private TextView textView;
    private EditText editText;
    //private Button applyTextButton;
    private Button saveButton;
    //private Button sendSMSButton;

    //SharedPreference keys
    public static final String SHARED_PREFS = "sharedPrefs";
    //key of appeared phone number
    public static final String TEXT = "text";
    //key of the message gonna to send
    private static final String MESSAGE = "message";

    //accept the appeared phone number stored in sharedPreference
    private String text;

    //content of message
    private static final String message = "I am now in the dangerous area";
    //private int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
    //accept the value(message) stored in sharedPreference
    private String sendMessage;

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
    private CrimeService.DataUpdateListener updaterListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = findViewById(R.id.map);
        mMapView.getMapAsync(this);
        mMapView.onCreate(savedInstanceState);
        geofencingClient = LocationServices.getGeofencingClient(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(BROADCAST_ACTION));

        updaterListener = new CrimeService.DataUpdateListener() {
            @Override
            public void onLocationUpdate(final Location loc) {
                Location prevLoc = lastLocation;
                lastLocation = loc;
                Log.d(TAG, String.format("lat=%f, lon=%f",
                        loc.getLatitude(), loc.getLongitude()));
                if (prevLoc == null) {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.showLastLocationOnMap();
                            LatLng[] cluster = crimeService.getLastClusterCenter();
                            if (cluster != null) {
                                for(LatLng center : cluster) {
                                    drawGeofence(center);
                                }
                            }
                        }

                    };
                    runOnUiThread(r);
                }
            }

            @Override
            public void onClusterUpdate(final LatLng[] pts) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        if (pts != null) {
//                            setGeofence(pts);
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

        Intent crimeServiceIntent = new Intent(this, CrimeService.class);
        bindService(crimeServiceIntent, conn, BIND_AUTO_CREATE);


        //phone number input part
        textView = (TextView) findViewById(R.id.textview);
        editText = (EditText) findViewById(R.id.edittext);
        //applyTextButton = (Button) findViewById(R.id.apply_text_button);
        //switch1 = (Switch) findViewById(R.id.switch1);
        saveButton = (Button) findViewById(R.id.save_button);
        //sendSMSButton = (Button) findViewById(R.id.send_sms);

//         applyTextButton.setOnClickListener(new View.OnClickListener() {
//             @Override
//             public void onClick(View view) {
//                 textView.setText(editText.getText().toString());
//             }
//         });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText(editText.getText().toString());
                saveData();
            }
        });

        loadData();
        updateViews();
        //sendSMS();
    }

    public void saveData() {
        SharedPreferences sharedPreferences= getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(TEXT, textView.getText().toString());
        editor.putString(MESSAGE, message);
        //editor.putBoolean(SWITCH1, switch1.isChecked());

        editor.apply();

        Toast.makeText(this, "Date saved", Toast.LENGTH_SHORT).show();
    }

    public void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        text = sharedPreferences.getString(TEXT, "");
        sendMessage = sharedPreferences.getString(MESSAGE, "");
    }

    public void updateViews() {
        //phone number appeared on the top
        textView.setText(text);

    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            ArrayList<LatLng> cluster = (ArrayList<LatLng>)intent.getSerializableExtra("cluster");
            Log.i("cluster", "Got cluster: " + cluster.size());
            for(LatLng center : cluster) {
                drawGeofence(center);
            }
        }
    };

    private void requestLocationAccessPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOC_PERM_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (crimeService != null) {
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

    private void drawGeofence(LatLng geofence) {
        Log.d(TAG, "drawGeofence()");

        CircleOptions circleOptions = new CircleOptions()
                .center(geofence)
                .strokeColor(Color.argb(50, 70, 70, 70))
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(GEOFENCE_RADIUS);
        mMap.addCircle(circleOptions);
    }

    private void markerForGeofence(String location, LatLng latLng) {
        Log.i(TAG, "markerForGeofence(" + latLng + ")");
        String title = latLng.latitude + ", " + latLng.longitude;
        // Define marker options
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .title(title);
        if (mMap != null) {
            if (geoFenceMarkerMap == null)
                geoFenceMarkerMap = new HashMap<>();
            if (geoFenceMarkerMap.get(location) == null) {
                geoFenceMarkerMap.put(location, mMap.addMarker(markerOptions));
            }
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
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
