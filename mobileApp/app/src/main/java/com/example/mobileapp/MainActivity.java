package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.getDetectionBlinking;
import static com.example.mobileapp.utils.DataUtils.getUserId;
import static com.example.mobileapp.utils.DataUtils.isDarkModeEnabled;

import com.example.mobileapp.services.FatigueDetectionService;
import com.example.mobileapp.utils.CameraHelper;
import com.example.mobileapp.utils.FaceDetectionProcessor;
import com.example.mobileapp.utils.NotificationActionReceiver;
import com.example.mobileapp.utils.NotificationUtils;
import com.example.mobileapp.utils.OverlayView;


import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.example.mobileapp.utils.DataUtils;
import com.example.mobileapp.utils.FatigueAnalyzer;
import com.example.mobileapp.utils.RequestUtils;
import com.example.mobileapp.utils.ToastUtils;
import com.example.mobileapp.utils.VideoStreamUtils;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;




public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Button trackingButton;
    private LinearLayout notificationContainer;
    private LottieAnimationView lottieAnimation;

    private boolean isTracking = false;
    private int notificationCount = 0;

    private VideoStreamUtils videoStream;

    private Executor cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageButton toggleCardButton;
    private boolean isCardVisible = false;
    private ValueAnimator cardAnimator;
    private ImageAnalysis fatigueImageAnalysis;
    private FaceDetectionProcessor fatigueProcessor;
    private CameraHelper cameraHelper;
    private final List<String> pendingNotifications = new ArrayList<>();


    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Разрешите показ уведомлений", Toast.LENGTH_SHORT).show();
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDarkModeEnabled(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lottieAnimation = findViewById(R.id.lottieAnimation);
        trackingButton = findViewById(R.id.buttonTracking);
        notificationContainer = findViewById(R.id.notificationContainer);
        ImageButton settingsButton = findViewById(R.id.buttonSettings);

        toggleCardButton = findViewById(R.id.toggleCardButton);

        TextureView textureView = findViewById(R.id.textureView);
        OverlayView overlayView = findViewById(R.id.overlayView);
        cameraHelper = new CameraHelper(this, textureView, overlayView);

        cardAnimator = ValueAnimator.ofInt(0, 300);
        cardAnimator.setDuration(300);
        cardAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        cardAnimator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = findViewById(R.id.cameraPreviewFrameLayout).getLayoutParams();
            layoutParams.height = (int) (val * getResources().getDisplayMetrics().density);
            findViewById(R.id.cameraPreviewFrameLayout).setLayoutParams(layoutParams);
        });

        toggleCardButton.setOnClickListener(v -> toggleCardView());

        String serverWsUrl = "ws://" + DataUtils.IP + "/ws";
        videoStream = new VideoStreamUtils(this, serverWsUrl, new VideoStreamUtils.Listener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket: подключено", Toast.LENGTH_SHORT).show());

                try {
                    JSONArray jsonArray = new JSONArray(pendingNotifications);

                    JSONObject Data = new JSONObject();
                    Data.put("message_list", jsonArray.toString());
                    Data.put("driver_id", getUserId(MainActivity.this));

                    new RequestUtils(MainActivity.this, "send_notification_list", "POST", Data.toString(), callbackSendNotificationList).execute();
                } catch (Exception e) {
                    Log.e(TAG, "MainActivity, OnCreate.videoStream" + e);
                    new RequestUtils(MainActivity.this, "log", "POST", "{\"module\": \"MainActivity\", \"method\": \"OnCreate.videoStream\", \"error\": \"" + e + "\"}", callbackLog).execute();
                }
                pendingNotifications.clear();
            }


            @Override
            public void onDisconnected() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket: отключено", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket ошибка: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
        fatigueProcessor = new FaceDetectionProcessor(this);

        if (!allPermissionsGranted()) {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }


        if (allPermissionsGranted()) {
            setupCamera();
        }

        Intent intent = getIntent();
        if (intent != null && "ACTION_TOGGLE_TRACKING".equals(intent.getAction())) {
            isTracking = !isTracking;
            updateTrackingState();
        }


        trackingButton.setOnClickListener(v -> {
            isTracking = !isTracking;
            updateTrackingState();
        });

        settingsButton.setOnClickListener(v -> {
            Intent intent2 = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent2);
        });

        addNotification("Система готова к отслеживанию");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoStream.disconnect();
        NotificationUtils.cancelNotification(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent == null || intent.getAction() == null) return;

        switch (intent.getAction()) {
            case NotificationActionReceiver.ACTION_TOGGLE_TRACKING:
                isTracking = !isTracking;
                updateTrackingState();
                break;
            case NotificationActionReceiver.ACTION_CLOSE_NOTIFICATION:
                finishAffinity(); // Закрывает всё
                break;
        }
    }

    // Обработка логирования на сервере
    RequestUtils.Callback callbackLog = (fragment, result) -> {
        try {
            JSONObject jsonObject = new JSONObject(result);
            if (jsonObject.getInt("status") != 0){
                showToast("Ошибка логирования на сервере.");
            }
        } catch (Exception e) {
            showToast("Ошибка логирования на клиенте.");
        }
    };

    RequestUtils.Callback callbackSendNotification = (fragment, result) -> {
        try {
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status");
            if (status == 1){
                showToast("Не удолось уведомить супервайзера");
            }
            else if (status == 2){
                showToast("Ошибка на стороне сервера ERROR: "+status);
            }

        } catch (Exception e) {
            new RequestUtils(this, "log", "POST", "{\"module\": \"MainActivity\", \"method\": \"callbackSendNotification\", \"error\": \"" + e + "\"}", callbackLog).execute();
            showToast("Ошибка callback.");

        }
    };

    RequestUtils.Callback callbackSendNotificationList = (fragment, result) -> {
        try {
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status");
            if (status == 1){
                showToast("Не удолось уведомить супервайзера");
            }
            else if (status == 2){
                showToast("Ошибка на стороне сервера ERROR: "+status);
            }

        } catch (Exception e) {
            new RequestUtils(this, "log", "POST", "{\"module\": \"MainActivity\", \"method\": \"callbackSendNotificationList\", \"error\": \"" + e + "\"}", callbackLog).execute();
            showToast("Ошибка callback.");

        }
    };

    private void showToast(String message) {
        this.runOnUiThread(() -> ToastUtils.showShortToast(this, message));
    }

    private void toggleCardView() {
        if (cardAnimator.isRunning()) return;

        isCardVisible = !isCardVisible;
        cardAnimator.removeAllListeners();

        if (isCardVisible) {
            findViewById(R.id.cameraPreviewFrameLayout).setVisibility(View.VISIBLE);
            cardAnimator.setIntValues(0, 420);
            toggleCardButton.setImageResource(R.drawable.ic_arrow_down);

            // ✅ Запускаем камеру при первом открытии
            cameraHelper.startCamera();

        } else {
            cardAnimator.setIntValues(420, 0);
            toggleCardButton.setImageResource(R.drawable.ic_arrow_up);

            cardAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    findViewById(R.id.cameraPreviewFrameLayout).setVisibility(View.GONE);
                    // ⛔ Остановка камеры при скрытии
                    cameraHelper.stopCamera();
                }
            });
        }

        cardAnimator.start();
    }

    private void updateTrackingState() {
        try {
            if (isTracking) {
                trackingButton.setText("Завершить отслеживание");
                trackingButton.setBackgroundColor(getColor(android.R.color.holo_red_dark));
                // Запускаем только сервис!
                Intent serviceIntent = new Intent(this, FatigueDetectionService.class);
                ContextCompat.startForegroundService(this, serviceIntent);

            } else {
                trackingButton.setText("Начать отслеживание");
                trackingButton.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                stopService(new Intent(this, FatigueDetectionService.class));

                videoStream.disconnect();
                addNotification("Отслеживание остановлено");
                stopLoading();
                findViewById(R.id.recOverlay).setVisibility(View.GONE);

                if (fatigueImageAnalysis != null) {
                    cameraProviderFuture.addListener(() -> {
                        try {
                            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbind(fatigueImageAnalysis);
                            fatigueImageAnalysis = null;
                        } catch (Exception e) {
                            Log.e(TAG, "Ошибка unbind FatigueAnalyzer", e);
                        }
                    }, ContextCompat.getMainExecutor(this));
                }
            }
            NotificationUtils.showTrackingNotification(this, isTracking);
        } catch (Exception ex) {
            Log.e("MainActivity", "Ошибка в updateTrackingState: " + ex);
        }
    }

    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraExecutor = ContextCompat.getMainExecutor(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isTracking) {
                        byte[] jpegBytes = imageProxyToJpeg(imageProxy);
                        if (jpegBytes != null) {
                            videoStream.sendFrame(jpegBytes);
                        }
                    }
                    imageProxy.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Ошибка CameraProvider", e);
            }
        }, cameraExecutor);
    }

    private byte[] imageProxyToJpeg(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            if (planes.length < 3) return null;

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] yuvBytes = new byte[ySize + uSize + vSize];
            yBuffer.get(yuvBytes, 0, ySize);
            vBuffer.get(yuvBytes, ySize, vSize);
            uBuffer.get(yuvBytes, ySize + vSize, uSize);

            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    yuvBytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 60, out);
            return out.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка преобразования ImageProxy в JPEG", e);
            return null;
        }
    }

    @SuppressLint("SetTextI18n")
    private void addNotification(String message) {
        notificationCount++;

        // Вибрация (если включена в настройках)
        if (DataUtils.isVibrationEnabled(this)) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        // Звук уведомления
        try {
            String ringtoneUriString = DataUtils.getRingtoneUri(this);
            Uri notificationUri = Uri.parse(ringtoneUriString);
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, notificationUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            int volumeLevel = DataUtils.getVolumeLevel(this); // от 0 до 10
            float volume = volumeLevel / 10f;
            mediaPlayer.setVolume(volume, volume);

            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.setOnCompletionListener(mp -> {
                mp.stop();
                mp.release();
            });
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            Log.e("Notification", "Ошибка воспроизведения звука", e);
        }

        // Добавление в интерфейс
        TextView notificationView = new TextView(this);
        notificationView.setText(notificationCount + ". " + message);
        notificationView.setPadding(20, 20, 20, 20);
        notificationView.setBackgroundResource(R.drawable.notification_background);
        notificationView.setTextColor(getColor(android.R.color.black));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 8, 16, 8);
        notificationView.setLayoutParams(params);
        notificationContainer.addView(notificationView, 0);

        // Попытка отправки на сервер
        if (videoStream.isConnected() && !message.equals("Система готова к отслеживанию")) {
            try {
                JSONObject sendDataNotification = new JSONObject();
                sendDataNotification.put("message", message);
                sendDataNotification.put("driver_id", getUserId(this));

                new RequestUtils(this, "send_notification", "POST", sendDataNotification.toString(), callbackSendNotification).execute();
            } catch (Exception e) {
                Log.e(TAG, "MainActivity, addNotification" + e);
                new RequestUtils(this, "log", "POST", "{\"module\": \"MainActivity\", \"method\": \"addNotification\", \"error\": \"" + e + "\"}", callbackLog).execute();
            }
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = true;
                for (Boolean ok : result.values()) {
                    if (!ok) {
                        granted = false;
                        break;
                    }
                }
                if (granted) {
                    setupCamera();
                } else {
                    Toast.makeText(this, "Разрешение на камеру обязательно", Toast.LENGTH_SHORT).show();
                }
            });

    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
    }

    private final BroadcastReceiver fatigueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            Log.d("MainActivity", "Получено событие усталости: " + message);
            if (message != null) addNotification(message);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(fatigueReceiver, new IntentFilter("com.example.mobileapp.FATIGUE_EVENT"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(fatigueReceiver, new IntentFilter("com.example.mobileapp.FATIGUE_EVENT"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(fatigueReceiver);
    }
}
