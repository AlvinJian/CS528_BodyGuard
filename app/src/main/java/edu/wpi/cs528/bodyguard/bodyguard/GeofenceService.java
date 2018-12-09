package edu.wpi.cs528.bodyguard.bodyguard;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeofenceService extends IntentService {
    private static final String TAG = "GeofenceService";
    private GeofencingEvent geofencingEvent;
    private static final String channelId = "default_channel_id";
    private static final String channelDescription = "Default Channel";

    // Defines a custom Intent action
    public static final String BROADCAST_ACTION = "com.example.android.threadsample.BROADCAST";


    public GeofenceService() {
        super(TAG);
    }

    // Send an Intent with an action named "custom-event-name". The Intent sent should
    // be received by the ReceiverActivity.
    private void sendMessage(String message) {
        // You can also include some extra data.
        Intent intent = new Intent(BROADCAST_ACTION);
        intent.putExtra("message", message);
//        switch (message) {
//            case "fullerLab":
//                visit_fuller_count += 1;
//                intent.putExtra("fuller", visit_fuller_count);
//
//                break;
//            case "gordanLibrary":
//                visit_gordon_count += 1;
//                intent.putExtra("gordon", visit_gordon_count);
//                break;
//            default:
//                break;
//        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "*************************************");
        Parcelable[] pts = intent.getParcelableArrayExtra("ClusterCenter");
        if (pts != null) {
            for (Parcelable pa: pts) {
                LatLng latlng = (LatLng) pa;
                Log.i(TAG, latlng.toString());
            }
        }
        geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "" + getErrorString(geofencingEvent.getErrorCode()));
            return;
        }


        int geofenceTransition = geofencingEvent.getGeofenceTransition();
//        Log.i(TAG, "*************************************");
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
//            Log.i(TAG, geofencingEvent.getTriggeringLocation().getLatitude() + "" + geofencingEvent.getTriggeringLocation().getLongitude());
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            String transitionDetails = getGeofenceTransitionInfo(
                    triggeringGeofences);

            String transitionType = getTransitionString(geofenceTransition);

            notifyLocationAlert(transitionType, transitionDetails);
//            Log.i(TAG, "*************************************" + triggeringGeofences.size());
//            sendMessage(triggeringGeofences.get(0).getRequestId());
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

