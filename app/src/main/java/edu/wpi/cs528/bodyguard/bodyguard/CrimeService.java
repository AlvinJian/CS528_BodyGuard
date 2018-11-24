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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CrimeService extends Service {
    private static final String TAG = "CrimeService";

    private final HandlerThread workerThread;
    private final Handler clusterHandler;
    private final Runnable clusterRunner;
    private IBinder serviceBinder = new CrimeBinder();
    private Timer httpTimer;

    public CrimeService() {
        workerThread =  new HandlerThread("worker");
        workerThread.start();
        clusterHandler = new Handler(workerThread.getLooper());

        // TODO should we start timer in constructor?
        // schedDownloadAndCluster(100000);

        clusterRunner = new Runnable() {
            @Override
            public void run() {
                CrimeService.this.doCluster();
            }
        };
    }

    public void schedDownloadAndCluster(long period) {
        httpTimer = new Timer("forHTTP");
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "start downloading");
                // TODO take position from GPS
                String param_lat="42.364118";
                String param_lon="-71.058043";
                String param_radius="0.02";
                makeRequest(param_lat,param_lon, param_radius);
            }
        };
        httpTimer.schedule(task, 10, period);
    }

    // TODO Put cluster code here
    private void doCluster() {
        Log.d(TAG, "pretend to do cluster");
        for (int i=0; i<5; ++i) {
            try {
                Thread.sleep(100);
                Log.d(TAG, ".");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy() {
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

    public void makeRequest(String param_lat, String param_lon, String param_radius){
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
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        parseJson(response);
                        clusterHandler.post(clusterRunner);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        queue.add(stringRequest);
    }
    public List<Double[]> parseJson(String str) {
        Log.d(TAG, "parseJson");
        if (str == null) return new ArrayList<>();
        str = str.substring(45, str.length() - 1);
        List<Double[]> res = new ArrayList();
        try {
            JSONObject jsonObj = new JSONObject(str);
            JSONArray crimes = jsonObj.getJSONArray("crimes");
            for (int i = 0; i < crimes.length(); i++) {
                JSONObject crime = crimes.getJSONObject(i);
                String lat = crime.getString("lat");
                String lon = crime.getString("lon");
                res.add(new Double[]{Double.parseDouble(lat), Double.parseDouble(lon)});
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
