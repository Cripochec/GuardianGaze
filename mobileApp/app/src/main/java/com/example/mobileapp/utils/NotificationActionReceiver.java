package com.example.mobileapp.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.mobileapp.MainActivity;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_TOGGLE_TRACKING = "TOGGLE_TRACKING";
    public static final String ACTION_CLOSE_NOTIFICATION = "CLOSE_NOTIFICATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        Log.d("NotificationReceiver", "Action received: " + intent.getAction());

        switch (intent.getAction()) {
            case ACTION_TOGGLE_TRACKING:
                Intent toggleIntent = new Intent(context, MainActivity.class);
                toggleIntent.setAction(ACTION_TOGGLE_TRACKING);
                toggleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(toggleIntent);
                break;

            case ACTION_CLOSE_NOTIFICATION:
                NotificationUtils.cancelNotification(context);
                Intent closeIntent = new Intent(context, MainActivity.class);
                closeIntent.setAction(ACTION_CLOSE_NOTIFICATION);
                closeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(closeIntent);
                break;
        }
    }
}
