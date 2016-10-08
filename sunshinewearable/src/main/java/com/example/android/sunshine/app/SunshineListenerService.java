package com.example.android.sunshine.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;


public class SunshineListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {

    private static final String TAG = "SunshineListenerService";
    private static final String DATA_ITEM_RECEIVED_PATH = "/weather";

    private static final String WEATHER_HIGH_KEY = "com.example.android.sunshine.app.high.key";
    private static final String WEATHER_LOW_KEY = "com.example.android.sunshine.app.low.key";
    private static final String WEATHER_ID_KEY = "com.example.android.sunshine.app.id.key";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        Log.e(TAG, "OnCreate Called");
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Log.e(TAG, "Google API Client was connected");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.e(TAG, "On data changed");

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo(DATA_ITEM_RECEIVED_PATH) == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    Double high = dataMap.getDouble(WEATHER_HIGH_KEY);
                    Double low = dataMap.getDouble(WEATHER_LOW_KEY);
                    int weatherId = dataMap.getInt(WEATHER_ID_KEY);
                    updateWeather(weatherId, high, low);
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                // DataItem deleted
            }
        }
    }

    // Our method to update weather - save to shared preferences
    private void updateWeather(int weatherId, Double high, Double low) {

        Log.e(TAG, "Weather Received: " + high + " " + low);
        //save to shared preferences
        SharedPreferences sharedPref = this.getSharedPreferences(getString(R.string.weather_data_prefs), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(getString(R.string.high_temp), Double.toString(high));
        editor.putString(getString(R.string.low_temp), Double.toString(low));
        editor.putInt(getString(R.string.weather_id), weatherId);
        editor.commit();


    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        Log.e(TAG, "Connection failed");

    }

}
