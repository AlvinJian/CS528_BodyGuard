package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class CrimeService extends Service {
    private static final String TAG = "CrimeService";
    private static final String CHANNEL_ID = "CrimeService";
    private static final int NOTIFICATION_ID = 1095;

    private final HandlerThread workerThread;
    private final Handler clusterHandler;
    private final Runnable clusterRunner;
    private IBinder serviceBinder = new CrimeBinder();
    private Timer httpTimer = null;
    private final long PERIOD = 86400000;

    private List<DoublePoint> positions = null;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private double searchRadius=2.0; //2 miles

    private LocationRequest locationRequest;
    private final int UPDATE_INTERVAL = 30000;   // Defined in mili seconds.
    private final int FASTEST_INTERVAL = 10000;   // This number in extremely low, and should be used only for debug
    private double[] previousSearchLocation =new double[]{0.0,0.0};
    final private Object locationLck = new Object();
    private Location lastLocation;
    private PendingIntent pendingIntentForLocation;
    private Response.Listener<String> responser;

    NotificationManager mNotificationManager;
    private NotificationCompat.Builder notificationBuilder;

    //SMS part
    //SharedPreference keys
    public static final String SHARED_PREFS = "sharedPrefs";
    //key of appeared phone number
    public static final String TEXT = "text";
    //key of the message gonna to send
    private static final String MESSAGE = "message";

    //accept the appeared phone number stored in sharedPreference
    private String text;

    //accept the value(message) stored in sharedPreference
    private String sendMessage;
    //check to send sms
    private boolean start = true;

    private LatLng[] lastClusterCenter = null;
    private final Object clusterLck = new Object();

    public CrimeService() {
        workerThread =  new HandlerThread("worker");
        workerThread.start();
        clusterHandler = new Handler(workerThread.getLooper());
        // TODO should we start timer in constructor?
//         schedDownloadAndCluster(100000);

        clusterRunner = new Runnable() {
            @Override
            public void run() {
                CrimeService.this.doCluster(0.01 ,20, positions);
            }
        };

        listeners = new HashSet<>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        Intent intent = new Intent(this, this.getClass());
        pendingIntentForLocation = PendingIntent.getService(this, 1095,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        responser = new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                positions = parseJson(response);
                clusterHandler.post(clusterRunner);
            }
        };

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "crime_service";
            String description = "crime_service channel";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
    }

    // Get last known location
    private Set<DataUpdateListener> listeners;

    public void removeLocationUpdateListener(DataUpdateListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    public void startTrackingLocation(DataUpdateListener listener) {
        Log.d(TAG, "getLastKnownLocation()");
        if (listener != null) listeners.add(listener);
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        Log.i(TAG, location.toString());
                        synchronized (locationLck) {
                            lastLocation = location;
                        }
                        for (DataUpdateListener l: listeners) {
                            if (!l.isMute()) {
                                l.onLocationUpdate(location);
                            }
                        }
                    } else {
                        Log.w(TAG, "No location retrieved yet");
                    }
                    if (!isTrackLocation) {
                        startLocationUpdates();
                    }
                }
            });
        }
    }

    public void stopTrackingLocation() {
        if (isTrackLocation) {
            Log.d(TAG, "stopTrackingLocation");
            fusedLocationProviderClient.removeLocationUpdates(pendingIntentForLocation);
            isTrackLocation = false;
            listeners.clear();
        }
    }

    private boolean isTrackLocation = false;
    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                    pendingIntentForLocation);
            isTrackLocation = true;
            runAsForegroundService();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        LocationResult result = LocationResult.extractResult(intent);
        if (result != null) {
            Location location = result.getLastLocation();
            if (location != null) {
                String param_lat = "";
                String param_lon = "";
                double lat_double;
                double lon_double;

                StringBuilder strBuilder = new StringBuilder();
                strBuilder.append(String.format("lat=%.2f, lon=%.2f\n",
                        location.getLatitude(), location.getLongitude()));
                Log.d(TAG, strBuilder.toString());

                synchronized (locationLck) {
                    lastLocation = location;
                    param_lat = String.valueOf(lastLocation.getLatitude());
                    param_lon = String.valueOf(lastLocation.getLongitude());
                    lat_double=lastLocation.getLatitude();
                    lon_double=lastLocation.getLongitude();
                }

                double dist = distance(lat_double,lon_double,previousSearchLocation[0],
                        previousSearchLocation[1],'M');
                if (previousSearchLocation[0] == 0.0) {
                    schedDownloadAndCluster(PERIOD);
                } else if(previousSearchLocation[0] != 0.0 && dist >searchRadius){
                    Log.d(TAG, "start downloading due to LocationChange");
                    strBuilder.append(String.format("dist=%.2f > %.2f download data...\n", dist, searchRadius));
                    String param_radius=String.valueOf(searchRadius/100);
                    makeRequest(param_lat,param_lon, param_radius, responser);
                } else {
                    strBuilder.append(String.format("dist=%.2f\n", (previousSearchLocation[0]!=0.0)? dist: 0.0));
                }

                // Keep this code for debug purpose
                // notificationBuilder.setContentText(strBuilder.toString());
                // mNotificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

                for (DataUpdateListener l: listeners) {
                    if (!l.isMute()) {
                        l.onLocationUpdate(location);
                    }
                }
            }
        }

        return super.onStartCommand(intent, flags, startId);

    }

    private void runAsForegroundService() {

        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("CrimeService is running")
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Notification notification = notificationBuilder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    public boolean isDownloadSched() {return httpTimer != null;}

    public void schedDownloadAndCluster(long period) {
        if (httpTimer != null) {
            Log.i(TAG, "reschedule http timer");
            httpTimer.cancel();
        }
        httpTimer = new Timer("forHTTP");

//        final Response.Listener<String> responser = new Response.Listener<String>() {
//            @Override
//            public void onResponse(String response) {
//                // Display the first 500 characters of the response string.
////                parseJson(response);
//                positions = parseJson(response);
//                clusterHandler.post(clusterRunner);
//            }
//        };

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "start downloading due to TimeSchedule");
                if (lastLocation != null) {
                    String param_lat = "";
                    String param_lon = "";
//                    double lat_double;
//                    double lon_double;
                    synchronized (locationLck) {
                        param_lat = String.valueOf(lastLocation.getLatitude());
                        param_lon = String.valueOf(lastLocation.getLongitude());
//                        lat_double=lastLocation.getLatitude();
//                        lon_double=lastLocation.getLongitude();
                    }

                    String param_radius=String.valueOf(searchRadius/100);
                    makeRequest(param_lat,param_lon, param_radius, responser);
                } else {
                    Log.e(TAG, "last location is null");
                }

            }
        };
        httpTimer.schedule(task, 10, period);
    }

    // TODO Put cluster code here
    private void  doCluster( double eps ,int minPts  ,List<DoublePoint> positions ) {
        Log.d(TAG, "doCluster");
//        List<Double[]> center = new ArrayList<Double[]>();
        ArrayList<LatLng> centers = new ArrayList<>();
        Intent clusterIntent = new Intent(this,GeofenceService.class);
        DBSCANClusterer dbscan = new DBSCANClusterer(eps, minPts);
        List<Cluster<DoublePoint>> cluster = dbscan.cluster(positions);
        Log.d(TAG, String.valueOf(cluster.size()));
        if(cluster.size()>0){
            for(Cluster<DoublePoint> c : cluster){
                Double[] d = {0.0,0.0};
                for(int i =0; i<c.getPoints().size(); i++ ){
                    d[0] += c.getPoints().get(i).getPoint()[0];
                    d[1] += c.getPoints().get(i).getPoint()[1];


                }
                d[0] = d[0]/c.getPoints().size();
                d[1] = d[1]/c.getPoints().size();
                Log.d(TAG, String.format("cluster center: %f, %f", d[0], d[1]));
                centers.add(new LatLng(d[0], d[1]));
            }

            LatLng[] locArray = centers.toArray(new LatLng[centers.size()]);

            synchronized (clusterLck) {
                lastClusterCenter = locArray;
            }

            for (DataUpdateListener l: listeners) {
                if (!l.isMute()) {
                    l.onClusterUpdate(locArray);
                }
            }

            clusterIntent.putExtra("ClusterCenter", locArray);
            startService(clusterIntent);

        }

        else{
            Log.d(TAG, "No cluster formed!");
        }

    }

    public LatLng[] getLastClusterCenter() {
        return lastClusterCenter;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "die");
        super.onDestroy();
        if (isTrackLocation) {
            fusedLocationProviderClient.removeLocationUpdates(pendingIntentForLocation);
        }
        workerThread.quitSafely();
        if (httpTimer != null) httpTimer.cancel();
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

    public void makeRequest(String param_lat, String param_lon, String param_radius,
                            Response.Listener<String> responser){
        //update previousSearchLocation
        previousSearchLocation[0]=Double.parseDouble(param_lat);previousSearchLocation[1]=Double.parseDouble(param_lon);
        String url="https://api.spotcrime.com/crimes.json?";
        String charset="UTF-8";
//        String param_callback="jQuery21306773805840830203_1542076452837";
        String param_key="heythisisforpublicspotcrime.comuse-forcommercial-or-research-use-call-877.410.1607-or-email-pyrrhus-at-spotcrime.com";
//        String param_="1542076452838";
        String query;
        try {
            query=String.format("lat=%s&lon=%s&radius=%s&key=%s",
                    URLEncoder.encode(param_lat, charset),
                    URLEncoder.encode(param_lon, charset),
                    URLEncoder.encode(param_radius, charset),
//                    URLEncoder.encode(param_callback, charset),
                    URLEncoder.encode(param_key, charset)
//                    URLEncoder.encode(param_, charset)
            );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e(TAG, "query string format error");
            return;
        }
        query=url+query;
        RequestQueue queue = Volley.newRequestQueue(this);
        Log.d(TAG, query);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, query,
                responser, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "download error");
                error.printStackTrace();
            }
        });
        queue.add(stringRequest);
    }
    public List<DoublePoint> parseJson(String str) {
        Log.d(TAG, "parseJson");
        if (str == null) return new ArrayList<>();
//        str = str.substring(45, str.length() - 1);
//        List<Double[]> res = new ArrayList();
        List<DoublePoint> res = new ArrayList<DoublePoint>();
        try {
            JSONObject jsonObj = new JSONObject(str);
            JSONArray crimes = jsonObj.getJSONArray("crimes");
            for (int i = 0; i < crimes.length(); i++) {
                JSONObject crime = crimes.getJSONObject(i);
                double[] d = new double[2];
                d[0] = Double.parseDouble(crime.getString("lat"));
                d[1] = Double.parseDouble(crime.getString("lon"));

                res.add(new DoublePoint(d));

                // Log.d(TAG, res.get(res.size() - 1)[0] + " " + res.get(res.size() - 1)[1]);
            }
            Log.d(TAG, String.format("%d points are parsed", res.size()));
            return res;
        } catch (JSONException e) {
            Log.e(TAG, "Json parsing error: " + e.getMessage());
        }
        return new ArrayList<>();

    }
    private double distance(double lat1, double lon1, double lat2, double lon2, char unit) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        if (unit == 'K') {
            dist = dist * 1.609344;
        } else if (unit == 'N') {
            dist = dist * 0.8684;
        }
        return (dist);
    }
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts decimal degrees to radians             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::  This function converts radians to decimal degrees             :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    public static abstract class DataUpdateListener {
        private boolean isMute;

        DataUpdateListener() {
            isMute = false;
        }

        public boolean isMute() {
            return isMute;
        }

        public void mute() {isMute = true;}

        public void unMute() {isMute = false;}

        public abstract void onLocationUpdate(final Location loc);
        // NOTE: the same format as what are transfer to geofence service
        public abstract void onClusterUpdate(final LatLng[] pts);
    }


    //send SMS and pop dialog


    public void sendSMS() {
        //check permission
        Log.d("sendSMS", "???");
        if (!checkPermission(Manifest.permission.SEND_SMS)) {
            Log.d("sendSMS", "send?");
            SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
            text = sharedPreferences.getString(TEXT, "");
            sendMessage = sharedPreferences.getString(MESSAGE, "");
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(text, null, sendMessage, null, null);
            Toast.makeText(this, "Send successfully", Toast.LENGTH_LONG).show();
            Log.d("sendSMS", "Yes!!!");
        }
    }

    private boolean checkPermission(String permission) {
        int checkPermission = ContextCompat.checkSelfPermission(this, permission);
        return checkPermission != PackageManager.PERMISSION_GRANTED;
    }

    public void popUpDialog() {
        start = true;

        AlertDialog.Builder a_builder = new AlertDialog.Builder(this);
        a_builder.setMessage("Do you want to send sms to emergency contact !!!")
                .setCancelable(false)
                .setPositiveButton("Yes",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendSMS();
                        start = false;
                        Log.d("start", String.valueOf(start));
                    }
                })
                .setNegativeButton("No",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        start = false;
                        Log.d("start", String.valueOf(start));
                    }
                }) ;
        final AlertDialog alert = a_builder.create();
        alert.setTitle("Alert !!!");
        alert.getWindow().setType(WindowManager.LayoutParams.TYPE_TOAST);
        alert.show();

        Log.d("start", String.valueOf(start));

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                alert.dismiss();
                timer.cancel();
                if (start) {
                    Looper.prepare();
                    sendSMS();
                    Looper.loop();
                }
            }
        }, 5000);
    }
}
