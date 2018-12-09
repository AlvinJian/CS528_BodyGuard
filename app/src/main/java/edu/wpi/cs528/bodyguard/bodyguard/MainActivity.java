package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
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
    private Location lastLocation;
    private static final LatLng fullerLab = new LatLng(42.275078, -71.806574);
    private static final LatLng gordanLibrary = new LatLng(42.274228, -71.806544);

    private static final String GEOFENCE_REQ_ID = "My Geofence";
    private Bundle bundle = new Bundle();
    private static final int GEOFENCE_RADIUS = 50;              //meters
    private GeofencingClient geofencingClient;
    private Map<String, Marker> geoFenceMarkerMap;
    private List<LatLng> geofenceCluster;

    //phone number input part
    private TextView textView;
    private EditText editText;
    private Button applyTextButton;
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

        updaterListener = new CrimeService.DataUpdateListener() {
            @Override
            public void onLocationUpdate(final Location loc) {
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

            @Override
            public void onClusterUpdate(final LatLng[] pts) {
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        // TODO add markers in this runnable
                        if (pts != null) {
                            setGeofence(pts);
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
        applyTextButton = (Button) findViewById(R.id.apply_text_button);
        //switch1 = (Switch) findViewById(R.id.switch1);
        saveButton = (Button) findViewById(R.id.save_button);
        //sendSMSButton = (Button) findViewById(R.id.send_sms);

        applyTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText(editText.getText().toString());
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
        geofenceCluster = new ArrayList<>();

//        geofenceCluster.add(fullerLab);
//        geofenceCluster.add(gordanLibrary);
//        markerForGeofence("fullerLab", fullerLab);
//        markerForGeofence("gordanLibrary", gordanLibrary);
//        setGeofence();
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

    private PendingIntent getGeofencePendingIntent() {
        Log.d(TAG, "createGeofencePendingIntent");

        Intent intent = new Intent(this, GeofenceService.class);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private GeofencingRequest getGeofencingRequest(Geofence geofence) {
        Log.d(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private Geofence getGeofence(LatLng latlng) {
        Log.d(TAG, "createGeofence");

        return new Geofence.Builder()
                .setRequestId(GEOFENCE_REQ_ID)
                .setCircularRegion(latlng.latitude, latlng.longitude, GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL
                        | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(3000)
                .build();
    }

    // Start Geofence creation process
    private void setGeofence(LatLng[] centers) {
        Log.i(TAG, Integer.toString(centers.length));
        if(centers != null && centers.length != 0) {
            for(LatLng center : centers) {
                Geofence geofence = getGeofence(center);
                addGeofence(geofence, center);
            }
        }
//        if (geoFenceMarkerMap != null && geoFenceMarkerMap.size() != 0) {
//            removeGeofence();
//            Iterator it = geoFenceMarkerMap.entrySet().iterator();
//            while (it.hasNext()) {
//                Map.Entry pair = (Map.Entry) it.next();
//                Marker geoFenceMarker = (Marker) pair.getValue();
//                Geofence geofence = getGeofence(geoFenceMarker.getPosition());
//                addGeofence(geofence, geoFenceMarker);
//            }
//        }
    }

    private void addGeofence(Geofence geofence, final LatLng center) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            geofencingClient.addGeofences(getGeofencingRequest(geofence), getGeofencePendingIntent())
                    .addOnSuccessListener(this, new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.i(TAG, "success");
                            Toast.makeText(MainActivity.this,
                                    "Location alter has been added",
                                    Toast.LENGTH_SHORT).show();
                            drawGeofence(center);
                        }
                    })
                    .addOnFailureListener(this, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.i(TAG, "failure");
                            Log.i(TAG, e.getMessage());
                            Toast.makeText(MainActivity.this,
                                    "Location alter could not be added",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        } else {
            requestLocationAccessPermission();
        }
    }

    private void removeGeofence() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.removeGeofences(getGeofencePendingIntent())
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                Log.i(TAG, "removed successfully");
                                Toast.makeText(MainActivity.this,
                                        "Location alters have been removed",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Log.i(TAG, "removed fail");
                                Toast.makeText(MainActivity.this,
                                        "Location alters could not be removed",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            requestLocationAccessPermission();
        }
    }

    private void drawGeofence(LatLng geofence) {
        Log.d(TAG, "drawGeofence()");

//        if (geoFenceLimits != null)
//            geoFenceLimits.remove();

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
            // Remove last geoFenceMarker
//            if (geoFenceMarker != null)
//                geoFenceMarker.remove();
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
