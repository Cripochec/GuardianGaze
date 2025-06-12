package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.isDarkModeEnabled;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mobileapp.utils.DataUtils;
import com.example.mobileapp.utils.VideoStreamUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI-элементы
    private Button trackingButton;
    private LinearLayout notificationContainer;
    private ImageButton settingsButton;

    // Флаги и счётчики
    private boolean isTracking = false;
    private int notificationCount = 0;

    // Video streaming через WebSocket
    private VideoStreamUtils videoStream;
    private com.airbnb.lottie.LottieAnimationView lottieAnimation;
    private final String serverWsUrl = "ws://192.168.1.100:5000/socket.io/?EIO=4&transport=websocket";
    // Замените 192.168.1.100:5000 на ваш реальный IP/порт

    // CameraX
    private Executor cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDarkModeEnabled(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Находим view по ID
        lottieAnimation = findViewById(R.id.lottieAnimation);
        trackingButton = findViewById(R.id.buttonTracking);
        notificationContainer = findViewById(R.id.notificationContainer);
        settingsButton = findViewById(R.id.buttonSettings);

        // Сначала инициализируем VideoStreamUtils (WebSocket)
        videoStream = new VideoStreamUtils(serverWsUrl, new VideoStreamUtils.Listener() {
            @Override
            public void onConnected() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "WebSocket: подключено", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "WebSocket: отключено", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(Throwable t) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "WebSocket ошибка: " + t.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });

        // Проверяем, есть ли разрешение CAMERA
        if (allPermissionsGranted()) {
            setupCamera();
        } else {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
            return;
        }

        // Обработка нажатия на кнопку «Отслеживание»
        trackingButton.setOnClickListener(v -> {
            isTracking = !isTracking;
            updateTrackingState();
        });

        // Обработка нажатия на кнопку «Настройки»
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Пример: при запуске отображаем стартовое уведомление
        addNotification("Система готова к отслеживанию");
    }


    // Запуск и остановка анимации
    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
    }

    // Обновляет UI и логику при старте/остановке трекинга
    private void updateTrackingState() {
        if (isTracking) {
            trackingButton.setText("Завершить отслеживание");
            trackingButton.setBackgroundColor(getColor(android.R.color.holo_red_dark));

            // 1) Открываем WebSocket
            videoStream.connect();

            // 2) Камера уже запущена в setupCamera(): при isTracking=true кадры начнут шляться
            addNotification("Отслеживание началось");
            Toast.makeText(this, "Отслеживание запущено", Toast.LENGTH_SHORT).show();

            // 3) Запускаем анимацию REC
            startLoading();
            findViewById(R.id.recOverlay).setVisibility(View.VISIBLE);
        } else {
            trackingButton.setText("Начать отслеживание");
            trackingButton.setBackgroundColor(getColor(android.R.color.holo_green_dark));

            // Закрываем WebSocket
            videoStream.disconnect();

            addNotification("Отслеживание остановлено");
            Toast.makeText(this, "Отслеживание остановлено", Toast.LENGTH_SHORT).show();

//            Отключаем анимацию REC
            runOnUiThread(this::stopLoading);
            this.runOnUiThread(() -> findViewById(R.id.recOverlay).setVisibility(View.GONE));
        }
    }


    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Запрашиваем разрешение камеры, если ещё не получено */
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

    /** Настраиваем CameraX + ImageAnalysis */
    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraExecutor = ContextCompat.getMainExecutor(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Создаём ImageAnalysis, чтобы получать кадры
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (isTracking) {
                            // Конвертируем ImageProxy → JPEG-байты
                            byte[] jpegBytes = imageProxyToJpeg(imageProxy);
                            if (jpegBytes != null) {
                                videoStream.sendFrame(jpegBytes);
                            }
                        }
                        imageProxy.close();
                    }
                });

                // Выбираем фронтальную камеру (если нужно менять — сделать настройку в UI)
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Отвязываем всё и привязываем заново
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        MainActivity.this,
                        cameraSelector,
                        imageAnalysis
                );
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Ошибка CameraProvider", e);
            }
        }, cameraExecutor);
    }



    /** Конвертирует ImageProxy (формат YUV) в JPEG-байты */
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
                    yuvBytes,
                    android.graphics.ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    60,
                    out
            );
            return out.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "Не удалось конвертировать ImageProxy в JPEG", e);
            return null;
        }
    }



    /** Добавление уведомления в контейнер, с вибрацией и звуком */
    private void addNotification(String message) {
        notificationCount++;

        // Вибрация, если включена в настройках
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
            Ringtone ringtone = RingtoneManager.getRingtone(this, notificationUri);
            if (ringtone != null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                ringtone.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Отображение в виде TextView
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

        // Добавляем наверх (в начало списка)
        notificationContainer.addView(notificationView, 0);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Закрываем WebSocket, если приложение уходит
        videoStream.disconnect();
    }
}
