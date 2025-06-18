package com.example.mobileapp.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.mobileapp.MainActivity;

public class NetworkStateReceiver extends BroadcastReceiver {
    private final MainActivity mainActivity;

    public NetworkStateReceiver(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        Log.d("NetworkStateReceiver", "Интернет " + (isConnected ? "есть" : "нет"));

        if (isConnected) {
            mainActivity.onNetworkRestored();
        } else {
            mainActivity.onNetworkLost();
        }
    }
}
