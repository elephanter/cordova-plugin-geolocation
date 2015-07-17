package org.apache.cordova;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaResourceApi.OpenForReadResult;
import android.net.Uri;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Looper;
import android.util.Log;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class Geolocation extends  CordovaPlugin implements LocationListener {

    class WatchedCallback{
        public Boolean keepWatching;
        public CallbackContext ctx;
        public WatchedCallback(CallbackContext _ctx, Boolean keepWatch){
            keepWatching = keepWatch;
            ctx = _ctx;
        }
    }

    static Location currentLocation; // здесь будет всегда доступна самая последняя информация о местоположении пользователя.
    private static final String LOG_TAG = "Geolocation";
    private static int distanceFilter = 5;
    private static Boolean locationLooking = false;
    private static Map<String, WatchedCallback> watchCallbacks = new HashMap();
    private static LocationManager locationManager;
    private static CordovaInterface _cordova;
    private static Geolocation _instance;

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        Log.d(LOG_TAG, "Got command: " + action);
        if (action.equals("getLocation")) {
            final Integer distanceFilter = args.getInt(1);

            watchCallbacks.put(UUID.randomUUID().toString(), new WatchedCallback(callbackContext, false));

            threadhelper(new Runnable() {
                public void run() {
                    startLocation(true);
                }
            }, callbackContext);
        }
        else if (action.equals("addWatch")) {
            final String callId=args.getString(0);

            watchCallbacks.put(callId, new WatchedCallback(callbackContext, true));
            Log.d(LOG_TAG, "Now watching for: " + watchCallbacks.size());
            final Integer newDistanceFilter = args.getInt(1);

            threadhelper(new Runnable() {
                public void run() {

                    if (distanceFilter< newDistanceFilter){ //need restart
                        distanceFilter = newDistanceFilter;
                        startLocation(true);
                    }else{
                        startLocation(false);
                    }

                }
            }, callbackContext);
        }
        else if (action.equals("clearWatch")) {
            final String callId=args.getString(0);

            if (watchCallbacks.containsKey(callId)) {
                Log.d(LOG_TAG, "Removeing watcher: " + callId);
                watchCallbacks.remove(callId);

                if (watchCallbacks.size() == 0) {
                    stopLocation();
                }
            }

            PluginResult p = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(p);
        }
        else {
            return false;
        }
        return true;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.d(LOG_TAG, "Initialize location plugin");
        locationLooking = false;
        locationManager = (LocationManager)
                cordova.getActivity().getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        _cordova = cordova;
        _instance = this;
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onPause(boolean multitasking) {
        if (locationLooking) {
            stopLocation();
        }
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking		Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onResume(boolean multitasking) {
        if (watchCallbacks.size() > 0){
            startLocation(true);
        }
    }

    /* Location listener interface implementation */
    public void onLocationChanged(Location loc) {
        currentLocation = loc;
        if (currentLocation!=null) {
            notify_loc();
        }
    }
    public void onProviderDisabled(String provider) {
        Log.d(LOG_TAG, "Provider disabled");
    }
    public void onProviderEnabled(String provider) {
        Log.d(LOG_TAG, "Provider enabled");
        currentLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (currentLocation!=null) {
            notify_loc();
        }
    }
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(LOG_TAG, "Status changed");
    }

    private void notify_loc(){
        for(String key: watchCallbacks.keySet()){

            WatchedCallback w = watchCallbacks.get(key);
            returnLocationInfo(w.ctx, w.keepWatching);

            if (!w.keepWatching){
                watchCallbacks.remove(key);
            }
        }

        if (watchCallbacks.size() == 0)
            stopLocation();
    }
    private void startLocation(boolean doRestart){
        if (locationLooking) {
            if (doRestart) {
                Log.d(LOG_TAG, "Restart location service");
                stopLocation(); //restart if already looking
            }else{
                return;
            }
        }
        Log.d(LOG_TAG, "Start location service");
        locationLooking = true;

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                distanceFilter,
                _instance, Looper.getMainLooper());
        currentLocation=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (currentLocation!=null) {
            notify_loc();
        }

    }

    private void stopLocation(){
        Log.d(LOG_TAG, "Stop location service");
        locationLooking = false;
        locationManager.removeUpdates(this);
    }

    private void returnLocationInfo(CallbackContext callbackContext, Boolean keepCallBack){
        Log.d(LOG_TAG, "Returning new location info");
        JSONObject jso = new JSONObject();

        try {

            jso.put("timestamp", currentLocation.getTime());
            jso.put("velocity", currentLocation.getSpeed());
            jso.put("accuracy", currentLocation.getAccuracy());
            jso.put("heading", currentLocation.getBearing());
            jso.put("altitude", currentLocation.getAltitude());
            jso.put("latitude", currentLocation.getLatitude());
            jso.put("longitude", currentLocation.getLongitude());

            PluginResult p = new PluginResult(PluginResult.Status.OK, jso);
            p.setKeepCallback(keepCallBack);

            callbackContext.sendPluginResult(p);
        }catch (Exception ex){
            returnLocationError(callbackContext, ex.hashCode(), ex.getMessage());
        }
    }
    private void returnLocationError(CallbackContext callbackContext, int code, String message){
        JSONObject jso = new JSONObject();
        try{
            jso.put("code", code);
            jso.put("message", message);
        }catch (Exception ex){}

        PluginResult p = new PluginResult(PluginResult.Status.ERROR, jso);
        callbackContext.sendPluginResult(p);
    }

    /* helper to execute functions async and handle the result codes
 *
 */
    private void threadhelper(final Runnable f, final CallbackContext callbackContext){
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    f.run();
                } catch ( Exception e) {
                    e.printStackTrace();

                   callbackContext.error(e.getMessage());
                }
            }
        });
    }
}
