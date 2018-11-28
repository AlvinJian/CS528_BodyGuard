package edu.wpi.cs528.bodyguard.bodyguard;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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

    private final HandlerThread workerThread;
    private final Handler clusterHandler;
    private final Runnable clusterRunner;
    private IBinder serviceBinder = new CrimeBinder();
    private Timer httpTimer = null;

    private List<DoublePoint> positions = null;
    private FusedLocationProviderClient fusedLocationProviderClient;

    private LocationRequest locationRequest;
    private final int UPDATE_INTERVAL = 1000;   // Defined in mili seconds.
    private final int FASTEST_INTERVAL = 500;   // This number in extremely low, and should be used only for debug

    final private Object locationLck = new Object();
    private Location lastLocation;

    public CrimeService() {
        workerThread =  new HandlerThread("worker");
        workerThread.start();
        clusterHandler = new Handler(workerThread.getLooper());
        // TODO should we start timer in constructor?
//         schedDownloadAndCluster(100000);

        clusterRunner = new Runnable() {
            @Override
            public void run() {
                CrimeService.this.doCluster(0.05 ,5, positions);
            }
        };

        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        listeners = new HashSet<>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    // Get last known location
    private Set<LocationUpdateListener> listeners;

    public void removeLocationUpdateListener(LocationUpdateListener listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }

    public void startTrackingLocation(LocationUpdateListener listener) {
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
                        for (LocationUpdateListener l: listeners) {
                            if (!l.isMute()) {
                                l.onUpdate(location);
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

    private boolean isTrackLocation = false;
    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates()");
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationCallback locationCb = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    Location location =  locationResult.getLastLocation();
                    if (location != null) {
                        for (LocationUpdateListener l: listeners) {
                            if (!l.isMute()) {
                                l.onUpdate(location);
                            }
                        }
                    }
                }
            };
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCb,
                    workerThread.getLooper());
            isTrackLocation = true;
        }
    }

    public boolean isDownloadSched() {return httpTimer != null;}

    public void schedDownloadAndCluster(long period) {
        if (httpTimer != null) {
            Log.i(TAG, "reschedule http timer");
            httpTimer.cancel();
        }
        httpTimer = new Timer("forHTTP");

        final Response.Listener<String> responser = new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                // Display the first 500 characters of the response string.
//                parseJson(response);
                positions = parseJson(response);
                clusterHandler.post(clusterRunner);
            }
        };

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "start downloading");
                if (lastLocation != null) {
                    String param_lat = "";
                    String param_lon = "";
                    synchronized (locationLck) {
                        param_lat = String.valueOf(lastLocation.getLatitude());
                        param_lon = String.valueOf(lastLocation.getLongitude());
                    }
                    String param_radius="0.02";
                    makeRequest(param_lat,param_lon, param_radius, responser);
                } else {
                    Log.e(TAG, "last location is null");
                }

            }
        };
        httpTimer.schedule(task, 10, period);
    }

    // TODO Put cluster code here
    private List<Cluster<DoublePoint>> doCluster( double eps ,int minPts  ,List<DoublePoint> positions ) {
        Log.d(TAG, "pretend to do cluster");
        DBSCANClusterer dbscan = new DBSCANClusterer(eps, minPts);
        List<Cluster<DoublePoint>> cluster = dbscan.cluster(positions);
        for(Cluster<DoublePoint> c : cluster){
            Log.d("CLUSTERING", c.getPoints().get(0).toString() );
            Log.d("CLUSTERING", String.valueOf(c.getPoints().size()));
//            System.out.println(c.getPoints().get(0));
        }
        return cluster;
//        for (int i=0; i<5; ++i) {
//            try {
//                Thread.sleep(100);
//                Log.d(TAG, ".");
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "die");

        super.onDestroy();
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

    public static abstract class LocationUpdateListener {
        private boolean isMute;

        LocationUpdateListener() {
            isMute = false;
        }

        public boolean isMute() {
            return isMute;
        }

        public void mute() {isMute = true;}

        public void unMute() {isMute = false;}

        public abstract void onUpdate(Location loc);
    }
}
