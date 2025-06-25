package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.getEntry;
import static com.example.mobileapp.utils.DataUtils.isDarkModeEnabled;
import com.example.mobileapp.utils.CameraHelper;
import com.example.mobileapp.utils.FaceDetectionProcessor;
import com.example.mobileapp.utils.NetworkStateReceiver;
import com.example.mobileapp.utils.NotificationActionReceiver;
import com.example.mobileapp.utils.NotificationUtils;
import com.example.mobileapp.utils.OverlayView;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
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
    private ImageButton toggleCardButton, settingsButton;
    private boolean isCardVisible = false;
    private ValueAnimator cardAnimator;
    private ImageAnalysis fatigueImageAnalysis;
    private FaceDetectionProcessor fatigueProcessor;
    private CameraHelper cameraHelper;
    private final List<String> pendingNotifications = new ArrayList<>();
    private final List<String> serverNotifications = new ArrayList<>();
    private int currentTutorialStep = 0;
    OverlayView overlayView;
    TextureView textureView;
    private NetworkStateReceiver networkStateReceiver;
    private boolean wasStreaming = false;
    private boolean isPreviewActive = false;
    private boolean isFirstPreviewActive = true;

    private android.os.Handler notificationHandler = new android.os.Handler();
    private final Runnable notificationRunnable = new Runnable() {
        @Override
        public void run() {
            checkServerNotifications();
            notificationHandler.postDelayed(this, 10000); // Проверка уведомлений каждые 10 секунд
        }
    };

    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    showToast("Разрешите показ уведомлений");
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        lottieAnimation = findViewById(R.id.lottieAnimation);
        trackingButton = findViewById(R.id.buttonTracking);
        notificationContainer = findViewById(R.id.notificationContainer);
        settingsButton = findViewById(R.id.buttonSettings);
        toggleCardButton = findViewById(R.id.toggleCardButton);
        textureView = findViewById(R.id.textureView);
        overlayView = findViewById(R.id.overlayView);

        cardAnimator = ValueAnimator.ofInt(0, 420);
        cardAnimator.setDuration(420);
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
            public void onConnected() {}

            @Override
            public void onDisconnected() {}

            @Override
            public void onError(Throwable t) {
                showToast("WebSocket ошибка: " + t.getMessage());
            }
        });

        Intent intent = getIntent();
        if (intent != null && "ACTION_TOGGLE_TRACKING".equals(intent.getAction())) {
            isTracking = !isTracking;
            updateTrackingState();
        }

        // Туториал по использованию приложения
        if (getEntry(this)) {
            startTutorial();
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

        toggleCardView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoStream.disconnect();
        NotificationUtils.cancelNotification(this);
        stopLocalDetection();
        notificationHandler.removeCallbacks(notificationRunnable);
        if (networkStateReceiver != null) {
            unregisterReceiver(networkStateReceiver);
            networkStateReceiver = null;
        }
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
            isPreviewActive = false;
        }
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
                finishAffinity();
                break;
        }
    }

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
            Log.e(TAG, "Ошибка callback. callbackSendNotification. "+ e);
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
            Log.e(TAG, "Ошибка callback. callbackSendNotificationList. "+ e);
        }
    };

    RequestUtils.Callback callbackGetNewNotifications = (fragment, result) -> {
        try {
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status");
            if (status == 0) {
                JSONArray notifications = jsonObject.getJSONArray("notifications");
                for (int i = 0; i < notifications.length(); i++) {
                    JSONObject notif = notifications.getJSONObject(i);
                    String message = notif.optString("message", "");
                    runOnUiThread(() -> addNotification(message));
                }
            } else {
                Log.e(TAG, "Ошибка обработки уведомлений с сервера");
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка callback. callbackGetNewNotifications. "+e);}
    };

    @Override
    protected void onStart() {
        super.onStart();
        networkStateReceiver = new NetworkStateReceiver(this);
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkStateReceiver, filter);

        notificationHandler.post(notificationRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkStateReceiver != null) {
            unregisterReceiver(networkStateReceiver);
            networkStateReceiver = null;
        }
        notificationHandler.removeCallbacks(notificationRunnable);
    }

    @SuppressLint("SetTextI18n")
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
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, notificationUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            int volumeLevel = DataUtils.getVolumeLevel(this);
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

        // Добавление уведомления в интерфейс
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
        if (DataUtils.getDetectionBlinking(this) && !message.equals("Система готова к отслеживанию")) {
            try {
                JSONObject loginData = new JSONObject();
                loginData.put("message", message);
                loginData.put("driver_id", DataUtils.getUserId(this));

                new RequestUtils(this, "send_notification", "POST", loginData.toString(), callbackSendNotification).execute();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка ReqestUtils. send_notification. "+e);}
        } else if (!message.equals("Система готова к отслеживанию")) {
            pendingNotifications.add(message);
        }
    }

    // Открытие/закрытие превью камеры
    private void toggleCardView() {
        if (cardAnimator.isRunning()) return;

        // Если включено отслеживание — выключаем его перед открытием превью
        if (isTracking) {
            isTracking = false;
            updateTrackingState();
        }

        isCardVisible = !isCardVisible;
        cardAnimator.removeAllListeners();

        if (isCardVisible) {
            findViewById(R.id.cameraPreviewFrameLayout).setVisibility(View.VISIBLE);
            cardAnimator.setIntValues(0, 420);
            toggleCardButton.setImageResource(R.drawable.ic_arrow_down);

            // Запустить превью, если ещё не запущено
            if (cameraHelper == null) {
                cameraHelper = new CameraHelper(this, textureView, overlayView);
            }
            cameraHelper.startCamera();
            isPreviewActive = true;

            if (isFirstPreviewActive){
                isFirstPreviewActive = false;

                isCardVisible = !isCardVisible;
                cardAnimator.removeAllListeners();

                cardAnimator.setIntValues(420, 0);
                toggleCardButton.setImageResource(R.drawable.ic_arrow_up);

                cardAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        findViewById(R.id.cameraPreviewFrameLayout).setVisibility(View.GONE);
                        if (cameraHelper != null) {
                            cameraHelper.stopCamera();
                        }
                        isPreviewActive = false;
                    }
                });
            }

        } else {
            cardAnimator.setIntValues(420, 0);
            toggleCardButton.setImageResource(R.drawable.ic_arrow_up);

            cardAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    findViewById(R.id.cameraPreviewFrameLayout).setVisibility(View.GONE);
                    if (cameraHelper != null) {
                        cameraHelper.stopCamera();
                    }
                    isPreviewActive = false;
                }
            });
        }

        cardAnimator.start();
    }

    // Обновление состояния отслеживания
    private void updateTrackingState() {
        try {
            // Если открыто превью — закрываем его перед запуском отслеживания
            if (isCardVisible) {
                toggleCardView();
            }

            if (isTracking) {
                trackingButton.setText("Завершить отслеживание");
                trackingButton.setBackgroundColor(getColor(android.R.color.holo_red_dark));
                startLoading();
                findViewById(R.id.recOverlay).setVisibility(View.VISIBLE);

                boolean localDetection = DataUtils.getDetectionBlinking(this);

                if (localDetection) {
                    startLocalDetection();
                    videoStream.disconnect();
                } else {
                    startVideoStreaming();
                    stopLocalDetection();
                }

            } else {
                trackingButton.setText("Начать отслеживание");
                trackingButton.setBackgroundColor(getColor(android.R.color.holo_green_dark));
                stopLoading();
                findViewById(R.id.recOverlay).setVisibility(View.GONE);

                stopVideoStreaming();
                stopLocalDetection();
            }

            NotificationUtils.showTrackingNotification(this, isTracking);
        } catch (Exception ex) {
            Log.e("MainActivity", "Ошибка в updateTrackingState: " + ex);
        }
    }

    // Запуск стриминга видео на сервер
    private void startVideoStreaming() {
        if (isPreviewActive && cameraHelper != null) {
            cameraHelper.stopCamera();
            isPreviewActive = false;
        }
        videoStream.connect();
        setupCameraForStreaming();
    }

    private void stopVideoStreaming() {
        videoStream.disconnect();
    }

    // Запуск локальной детекции усталости
    private void startLocalDetection() {
        if (isPreviewActive && cameraHelper != null) {
            cameraHelper.stopCamera();
            isPreviewActive = false;
        }
        setupCameraForLocalDetection();
    }

    private void stopLocalDetection() {
        if (cameraProviderFuture != null && fatigueImageAnalysis != null) {
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    cameraProvider.unbind(fatigueImageAnalysis);
                    fatigueImageAnalysis = null;
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка остановки локальной детекции", e);
                }
            }, ContextCompat.getMainExecutor(this));
        }
    }

    // Настройка камеры для стриминга
    private void setupCameraForStreaming() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraExecutor = ContextCompat.getMainExecutor(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    byte[] jpegBytes = imageProxyToJpeg(imageProxy);
                    if (jpegBytes != null) {
                        videoStream.sendFrame(jpegBytes);
                    }
                    imageProxy.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Ошибка setupCameraForStreaming", e);
            }
        }, cameraExecutor);
    }

    // Настройка камеры для локальной детекции
    private void setupCameraForLocalDetection() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraExecutor = ContextCompat.getMainExecutor(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                fatigueImageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                FatigueAnalyzer analyzer = new FatigueAnalyzer(this, new FatigueAnalyzer.Listener() {
                    @Override
                    public void onBlink() {
                        runOnUiThread(() -> addNotification("Долгое закрытие глаз"));
                    }

                    @Override
                    public void onHeadTilt() {
                        runOnUiThread(() -> addNotification("Долгий наклон головы"));
                    }
                });

                fatigueImageAnalysis.setAnalyzer(cameraExecutor, analyzer);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, fatigueImageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Ошибка setupCameraForLocalDetection", e);
            }
        }, cameraExecutor);
    }

    // Преобразование ImageProxy в JPEG
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

    // Анимация REC
    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
    }

    // Туториал по использованию приложения
    private void startTutorial() {
        showTutorialStep(currentTutorialStep);
    }

    private void showTutorialStep(int step) {
        switch (step) {
            case 0:
                highlightTrackingButton();
                break;
            case 1:
                highlightSettingsButton();
                break;
            case 2:
                highlightCameraPreview();
                break;
            default:
                completeTutorial();
        }
    }

    private void highlightTrackingButton() {
        TapTargetView.showFor(this,
                TapTarget.forView(findViewById(R.id.buttonTracking),
                                "Кнопка отслеживания",
                                "Нажмите здесь, чтобы начать мониторинг вашего состояния")
                        .outerCircleColor(R.color.teal_200)
                        .outerCircleAlpha(0.96f)
                        .targetCircleColor(android.R.color.white)
                        .titleTextSize(20)
                        .descriptionTextSize(16)
                        .drawShadow(true)
                        .cancelable(false)
                        .tintTarget(true)
                        .transparentTarget(true),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                        currentTutorialStep++;
                        showTutorialStep(currentTutorialStep);
                    }
                });
    }

    private void highlightSettingsButton() {
        View settingsBtn = findViewById(R.id.buttonSettings);
        settingsBtn.post(() -> {
            TapTargetView.showFor(this,
                TapTarget.forView(settingsBtn,
                        "Настройки",
                        "Здесь вы можете изменить параметры приложения")
                    .outerCircleColor(R.color.purple_200)
                    .titleTextSize(20)
                    .descriptionTextSize(16)
                    .drawShadow(true)
                    .cancelable(false),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                        currentTutorialStep++;
                        showTutorialStep(currentTutorialStep);
                    }
                });
        });
    }

    private void highlightCameraPreview() {
        TapTargetView.showFor(this,
                TapTarget.forView(findViewById(R.id.toggleCardButton),
                                "Превью камеры",
                                "Здесь вы можете увидеть то, как вас видит приложение")
                        .outerCircleColor(R.color.teal_700)
                        .titleTextSize(20)
                        .descriptionTextSize(16)
                        .drawShadow(true)
                        .cancelable(false),
                new TapTargetView.Listener() {
                    @Override
                    public void onTargetClick(TapTargetView view) {
                        super.onTargetClick(view);
                        completeTutorial();
                    }
                });
    }

    private void completeTutorial() {
        settingsButton.callOnClick();
    }

    // Toast уведомление
    private void showToast(String message) {
        this.runOnUiThread(() -> ToastUtils.showShortToast(this, message));
    }

    // Обработка потери сети
    public void onNetworkLost() {
        if (!DataUtils.getDetectionBlinking(this)) {
            wasStreaming = true;
            stopVideoStreaming();
            startLocalDetection();
            showToast("Интернет пропал, включена локальная обработка!");
        }
    }

    // Обработка восстановления сети
    public void onNetworkRestored() {
        if (wasStreaming) {
            stopLocalDetection();
            startVideoStreaming();
            sendPendingNotifications();
            wasStreaming = false;
            showToast("Интернет пропал, включена локальная обработка!");
        } else if (DataUtils.getDetectionBlinking(this)) {
            sendPendingNotifications();
        }
    }

    // Отправка накопленных уведомлений на сервер
    private void sendPendingNotifications() {
        if (!pendingNotifications.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(pendingNotifications);
                JSONObject Data = new JSONObject();
                Data.put("message_list", jsonArray.toString());
                Data.put("driver_id", DataUtils.getUserId(this));
                new RequestUtils(this, "send_notification_list", "POST", Data.toString(), callbackSendNotificationList).execute();
                pendingNotifications.clear();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка отправки накопленных уведомлений", e);
            }
        }
    }

    // Проверка новых уведомлений с сервера
    private void checkServerNotifications() {
        int driverId = DataUtils.getUserId(this);
        new RequestUtils(this, "api/get_new_notifications/" + driverId, "GET", "", callbackGetNewNotifications).execute();
    }
}