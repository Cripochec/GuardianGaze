package com.example.mobileapp.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.example.mobileapp.R;
import com.example.mobileapp.utils.FatigueAnalyzer;
import com.example.mobileapp.utils.NotificationActionReceiver;
import com.example.mobileapp.utils.NotificationUtils;
import com.example.mobileapp.utils.ToastUtils;
import com.example.mobileapp.utils.addNotificationUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FatigueDetectionService extends LifecycleService {

    private static final String CHANNEL_ID = "fatigue_detection_channel";
    private static final int NOTIF_ID = 101;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Executor cameraExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        startFatigueDetection();
    }

    private void startFatigueDetection() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                FatigueAnalyzer analyzer = new FatigueAnalyzer(this, new FatigueAnalyzer.Listener() {
                    @Override
                    public void onBlink() {
                        addNotificationUtils.notifyFatigue(getApplicationContext(), "Долгое моргание", null, null, null);
                        sendFatigueEvent("Долгое моргание");
                        ToastUtils.showShortToast(FatigueDetectionService.this, "Моргнул");
                    }

                    @Override
                    public void onHeadTilt() {
                        addNotificationUtils.notifyFatigue(getApplicationContext(), "Наклон головы", null, null, null);
                        sendFatigueEvent("Наклон головы");
                        ToastUtils.showShortToast(FatigueDetectionService.this, "наклон");
                    }
                });

                analysis.setAnalyzer(cameraExecutor, analyzer);

                cameraProvider.unbindAll();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.bindToLifecycle(this, cameraSelector, analysis);

            } catch (Exception e) {
                Log.e("FatigueService", "Ошибка запуска детекции", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private Notification buildNotification() {
        // Кнопка "Остановить детекцию"
        Intent stopIntent = new Intent(this, NotificationActionReceiver.class);
        stopIntent.setAction(NotificationActionReceiver.ACTION_TOGGLE_TRACKING);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Кнопка "Закрыть приложение"
        Intent closeIntent = new Intent(this, NotificationActionReceiver.class);
        closeIntent.setAction(NotificationActionReceiver.ACTION_CLOSE_NOTIFICATION);
        PendingIntent closePendingIntent = PendingIntent.getBroadcast(
                this, 1, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Открытие MainActivity по нажатию на уведомление
        Intent contentIntent = new Intent(this, com.example.mobileapp.MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                this, 2, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GuardianGaze")
                .setContentText("Фоновая детекция усталости активна")
                .setSmallIcon(R.drawable.ic_eye_closed)
                .setContentIntent(mainPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_stop, "Остановить", stopPendingIntent)
                .addAction(R.drawable.ic_cross, "Закрыть", closePendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fatigue Detection",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Если система убьет сервис, он перезапустится
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("FatigueService", "Сервис уничтожен");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendFatigueEvent(String message) {
        Intent intent = new Intent("com.example.mobileapp.FATIGUE_EVENT");
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }
}
