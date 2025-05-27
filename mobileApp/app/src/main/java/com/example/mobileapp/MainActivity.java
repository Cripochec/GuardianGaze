package com.example.mobileapp;


import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobileapp.utils.DataUtils;

public class MainActivity extends AppCompatActivity {

    private boolean isTracking = false;
    private Button trackingButton;
    private LinearLayout notificationContainer;
    private int notificationCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trackingButton = findViewById(R.id.buttonTracking);
        notificationContainer = findViewById(R.id.notificationContainer);
        ImageButton settingsButton = findViewById(R.id.buttonSettings);

        trackingButton.setOnClickListener(v -> {
            isTracking = !isTracking;
            updateTrackingState();
        });

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Пример добавления уведомления
        addNotification("Система готова к отслеживанию");
    }

    private void updateTrackingState() {
        if (isTracking) {
            trackingButton.setText("Завершить отслеживание");
            trackingButton.setBackgroundColor(getColor(android.R.color.holo_red_dark));
            Toast.makeText(this, "Отслеживание запущено", Toast.LENGTH_SHORT).show();
            addNotification("Отслеживание началось");
        } else {
            trackingButton.setText("Начать отслеживание");
            trackingButton.setBackgroundColor(getColor(android.R.color.holo_green_dark));
            Toast.makeText(this, "Отслеживание остановлено", Toast.LENGTH_SHORT).show();
            addNotification("Отслеживание остановлено");
        }
    }

    private void addNotification(String message) {
        notificationCount++;

        // --- Вибрация, если включена в настройках
        if (DataUtils.isVibrationEnabled(this)) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        // --- Звук уведомления
        try {
            String ringtoneUriString = DataUtils.getRingtoneUri(this);
            Uri notificationUri = Uri.parse(ringtoneUriString);
            Ringtone ringtone = RingtoneManager.getRingtone(this, notificationUri);
            if (ringtone != null) {
                int volume = DataUtils.getVolumeLevel(this); // 0-10
                // Можно регулировать звук через AudioAttributes
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                ringtone.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // --- Отображение уведомления
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

        notificationContainer.addView(notificationView, 0); // Добавляем в начало
    }
}
