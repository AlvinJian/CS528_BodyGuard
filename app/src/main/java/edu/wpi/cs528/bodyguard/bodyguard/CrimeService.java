package edu.wpi.cs528.bodyguard.bodyguard;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

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
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class CrimeService extends Service {
    private static final String TAG = "CrimeService";

    private final HandlerThread workerThread;
    private final Handler clusterHandler;
    private final Runnable clusterRunner;
    private IBinder serviceBinder = new CrimeBinder();
    private Timer httpTimer = null;

    public CrimeService() {
        workerThread =  new HandlerThread("worker");
        workerThread.start();
        clusterHandler = new Handler(workerThread.getLooper());
        // TODO should we start timer in constructor?
//         schedDownloadAndCluster(100000);

        clusterRunner = new Runnable() {
            @Override
            public void run() {
//                CrimeService.this.doCluster(0.05 ,50, //list of doublepoint positions);
            }
        };
    }

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
                doCluster(0.05,10,parseJson(response));
                clusterHandler.post(clusterRunner);
            }
        };

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "start downloading");
                // TODO take position from GPS
                String param_lat="42.364118";
                String param_lon="-71.058043";
                String param_radius="0.02";
                makeRequest(param_lat,param_lon, param_radius, responser);
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

    public void makeRequest(String param_lat, String param_lon, String param_radius,
                            Response.Listener<String> responser){
        String url="https://api.spotcrime.com/crimes.json?";
        String charset="UTF-8";
        String param_callback="jQuery21306773805840830203_1542076452837";
        String param_key="heythisisforpublicspotcrime.comuse-forcommercial-or-research-use-call-877.410.1607-or-email-pyrrhus-at-spotcrime.com";
        String param_="1542076452838";
        String query;
        try {
            query=String.format("lat=%s&lon=%s&radius=%s&callback=%s&key=%s&_=%s",
                    URLEncoder.encode(param_lat, charset),
                    URLEncoder.encode(param_lon, charset),
                    URLEncoder.encode(param_radius, charset),
                    URLEncoder.encode(param_callback, charset),
                    URLEncoder.encode(param_key, charset),
                    URLEncoder.encode(param_, charset)
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
                error.printStackTrace();
            }
        });
        queue.add(stringRequest);
    }
    public List<DoublePoint> parseJson(String str) {
        Log.d(TAG, "parseJson");
        if (str == null) return new ArrayList<>();
        str = str.substring(45, str.length() - 1);
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
}
