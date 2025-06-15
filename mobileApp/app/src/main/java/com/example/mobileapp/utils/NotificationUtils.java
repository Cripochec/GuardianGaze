package com.example.mobileapp.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.mobileapp.MainActivity;
import com.example.mobileapp.R;

public class NotificationUtils {

    private static final String CHANNEL_ID = "guardian_channel";
    private static final int NOTIF_ID = 1;

    public static void showTrackingNotification(Context context, boolean isTracking) {
        createChannel(context);

        Intent toggleIntent = new Intent(context, NotificationActionReceiver.class);
        toggleIntent.setAction("TOGGLE_TRACKING");
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent closeIntent = new Intent(context, NotificationActionReceiver.class);
        closeIntent.setAction("CLOSE_NOTIFICATION");
        PendingIntent closePendingIntent = PendingIntent.getBroadcast(
                context, 1, closeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent contentIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context, 2, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle("GuardianGaze")
                .setContentText("Детекция усталости")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(mainPendingIntent)
                .setOngoing(true) // 🔒 Несвайпаемое
                .setAutoCancel(false) // 🔒 Не закрывается свайпом
                .setOnlyAlertOnce(true)
                .addAction(isTracking ? R.drawable.ic_stop : R.drawable.ic_play,
                        isTracking ? "Остановить" : "Включить",
                        togglePendingIntent)
                .addAction(R.drawable.ic_cross, "Закрыть", closePendingIntent);


        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIF_ID, builder.build());
    }

    public static void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIF_ID);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Guardian Notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }
}
