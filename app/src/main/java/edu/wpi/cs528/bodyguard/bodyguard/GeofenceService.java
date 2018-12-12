package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class GeofenceService extends IntentService {
    private static final String TAG = "GeofenceService";
    private GeofencingEvent geofencingEvent;
    private static final String channelId = "default_channel_id";
    private static final String channelDescription = "Default Channel";

    private static final int LOC_PERM_REQ_CODE = 1;
    private static final String GEOFENCE_REQ_ID = "My Geofence";
    private static final int GEOFENCE_RADIUS = 500;
    private GeofencingClient geofencingClient;

    private Timer geofenceTimer = null;
    TimerTask timerTask = null;

    @Override
    public void onCreate() {
        super.onCreate();
         geofencingClient = LocationServices.getGeofencingClient(this);
    }

    private Map<String, Marker> geoFenceMarkerMap;

    // Defines a custom Intent action
    public static final String BROADCAST_ACTION = "edu.wpi.cs528.bodyguard.bodyguard.BROADCAST";


    public GeofenceService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        List<LatLng> clusters = new ArrayList<>();
        Parcelable[] pts = intent.getParcelableArrayExtra("ClusterCenter");
        if (pts != null) {
            Log.i(TAG, "cluster point size="+pts.length);
            for (Parcelable pa: pts) {
                LatLng latlng = (LatLng) pa;
                clusters.add(latlng);
                Log.i(TAG, latlng.toString());
            }
            setGeofence(clusters);
            sendClusters(clusters);
        }
        geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "" + getErrorString(geofencingEvent.getErrorCode()));
            return;
        }


        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.i(TAG, "geofence enter");
            startTimer();
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            String transitionDetails = getGeofenceTransitionInfo(
                    triggeringGeofences);

            String transitionType = getTransitionString(geofenceTransition);

            notifyLocationAlert(transitionType, transitionDetails);
        }

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.i(TAG, "geofence exit");
            stopTimer();
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            String transitionDetails = getGeofenceTransitionInfo(
                    triggeringGeofences);

            String transitionType = getTransitionString(geofenceTransition);

            notifyLocationAlert(transitionType, transitionDetails);
        }
    }

    public void startTimer() {
        Log.i(TAG, "start geofence timer");
        geofenceTimer = new Timer();
        //initialize the TimerTask's job
        initializeTimerTask();
        //schedule the timer, after the first delay time the TimerTask will run every period
        geofenceTimer.schedule(timerTask, 0, 1000); //
    }

    public void stopTimer() {
        Log.i(TAG, "stop geofence timer");
        //stop the timer, if it's not already null
        if (geofenceTimer != null) {
            geofenceTimer.cancel();
            geofenceTimer = null;
        }
    }


    public void initializeTimerTask() {

        timerTask = new TimerTask() {
            public void run() {

                //use a handler to run a toast that shows the current timestamp
//                handler.post(new Runnable() {
//                    public void run() {
//                        //get the current timeStamp
//                        Calendar calendar = Calendar.getInstance();
//                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
//                        final String strDate = simpleDateFormat.format(calendar.getTime());
//
//                        //show the toast
//                        int duration = Toast.LENGTH_SHORT;
//                        Toast toast = Toast.makeText(getApplicationContext(), strDate, duration);
//                        toast.show();
//                    }
//                });
            }
        };
    }

    // Send an Intent with an action named "custom-event-name". The Intent sent should
    // be received by the ReceiverActivity.
    private void sendClusters(List<LatLng> cluster) {
        // You can also include some extra data.
        Intent intent = new Intent(BROADCAST_ACTION);
        Log.i("send cluster", "" + cluster.size());
        intent.putExtra("cluster", (ArrayList)cluster);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
//                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
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
                .setLoiteringDelay(1000)
                .build();
    }

    // Start Geofence creation process
    private void setGeofence(List<LatLng> centers) {
        Log.i(TAG, Integer.toString(centers.size()));
        if(centers != null && centers.size() != 0) {
            removeGeofence();
            for(LatLng center : centers) {
                Geofence geofence = getGeofence(center);
                addGeofence(geofence, center);
            }
        }
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
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.i(TAG, "success");
                        }
                    })
                    .addOnFailureListener( new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.i(TAG, "failure");
                            Log.i(TAG, e.getMessage());
                        }
                    });
            return;
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
                            } else {
                                Log.i(TAG, "removed fail");
                            }
                        }
                    });
        }
    }

    public Location getLocationLatLng() {
        return geofencingEvent.getTriggeringLocation();
    }

    private String getGeofenceTransitionInfo(List<Geofence> triggeringGeofences) {
        ArrayList<String> locationNames = new ArrayList<>();
        for (Geofence geofence : triggeringGeofences) {
            locationNames.add(getLocationName(geofence.getRequestId()));
        }
        String triggeringLocationsString = TextUtils.join(", ", locationNames);

        return triggeringLocationsString;
    }

    private String getLocationName(String key) {
        String[] strs = key.split("-");

        String locationName = null;
        if (strs != null && strs.length == 2) {
            double lat = Double.parseDouble(strs[0]);
            double lng = Double.parseDouble(strs[1]);

            locationName = getLocationNameGeocoder(lat, lng);
        }
        if (locationName != null) {
            return locationName;
        } else {
            return key;
        }
    }

    private String getLocationNameGeocoder(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        List<Address> addresses = null;

        try {
            addresses = geocoder.getFromLocation(lat, lng, 1);
        } catch (Exception ioException) {
            Log.e("", "Error in getting location name for the location");
        }

        if (addresses == null || addresses.size() == 0) {
            Log.d("", "no location name");
            return null;
        } else {
            Address address = addresses.get(0);
            ArrayList<String> addressInfo = new ArrayList<>();
            for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                addressInfo.add(address.getAddressLine(i));
            }

            return TextUtils.join(System.getProperty("line.separator"), addressInfo);
        }
    }

    private String getErrorString(int errorCode) {
        switch (errorCode) {
            case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
                return "Geofence not available";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
                return "geofence too many_geofences";
            case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
                return "geofence too many pending_intents";
            default:
                return "geofence error";
        }
    }

    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "location entered";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "location exited";
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                return "dwell at location";
            default:
                return "location transition";
        }
    }

    private void createChannel() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        //Check if notification channel exists and if not create one
        // Reference: https://stackoverflow.com/questions/45668079/notificationchannel-issue-in-android-o
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = mNotificationManager.getNotificationChannel(channelId);
            if (notificationChannel == null) {
                int importance = NotificationManager.IMPORTANCE_HIGH; //Set the importance level
                notificationChannel = new NotificationChannel(channelId, channelDescription, importance);
                notificationChannel.setLightColor(Color.GREEN); //Set if it is necesssary
                notificationChannel.enableVibration(true); //Set if it is necesssary
                mNotificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    private void notifyLocationAlert(String locTransitionType, String locationDetails) {
        createChannel();
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setContentTitle(locTransitionType)
                        .setContentText(locationDetails);

        builder.setAutoCancel(true);


        mNotificationManager.notify(0, builder.build());
    }

}

