package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.*;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.mobileapp.utils.RequestUtils;
import com.example.mobileapp.utils.ToastUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONException;
import org.json.JSONObject;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_RINGTONE_PICKER = 1;
    private com.airbnb.lottie.LottieAnimationView lottieAnimation;
    private TextView textRingtone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDarkModeEnabled(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        lottieAnimation = findViewById(R.id.lottieAnimation);
        Switch switchVibration = findViewById(R.id.switchVibration);
        textRingtone = findViewById(R.id.textRingtone);
        Switch switchDarkMode = findViewById(R.id.switchDarkMode);
        ImageButton buttonBac = findViewById(R.id.buttonBac);
        ConstraintLayout outPanel = findViewById(R.id.out_panel);
        ConstraintLayout supportPanel = findViewById(R.id.support_panel);
        ConstraintLayout cameraPanel = findViewById(R.id.camera_panel);
        ConstraintLayout mlPanel = findViewById(R.id.ml_panel);
        SeekBar seekBarVolumeLevel = findViewById(R.id.seekBarVolumeLevel);
        TextView textVolumeLevel = findViewById(R.id.textVolumeLevel);

        buttonBac.setOnClickListener(v -> finish());

        supportPanel.setOnClickListener(v -> showSupportDialog());

        outPanel.setOnClickListener(v -> showLogoutDialog());

        mlPanel.setOnClickListener(v -> showMlDialog());

        cameraPanel.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, EyeSettingsActivity.class);
            startActivity(intent);
        });

        // Вибрация
        switchVibration.setChecked(isVibrationEnabled(this));
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveVibrationEnabled(this, isChecked)
        );

        // Звук уведомлений
        Uri currentUri = Uri.parse(getRingtoneUri(this));
        String title = RingtoneManager.getRingtone(this, currentUri).getTitle(this);
        textRingtone.setText("Уведомление: " + title);

        textRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите звук");
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri);
            startActivityForResult(intent, REQUEST_RINGTONE_PICKER);
        });

        // Громкость звука уведомлений
        seekBarVolumeLevel.setMax(10);
        seekBarVolumeLevel.setMin(0);
        seekBarVolumeLevel.setProgress(getVolumeLevel(this));
        textVolumeLevel.setText(String.valueOf(getVolumeLevel(this)));

        seekBarVolumeLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textVolumeLevel.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ToastUtils.showLongToast(SettingsActivity.this,"советуем установить 10");
                saveVolumeLevel(SettingsActivity.this, seekBarVolumeLevel.getProgress());
            }
        });

        // Тёмная тема
        switchDarkMode.setChecked(isDarkModeEnabled(this));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDarkMode(this, isChecked);
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
            recreate();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RINGTONE_PICKER && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                saveRingtoneUri(this, uri.toString());
                String title = RingtoneManager.getRingtone(this, uri).getTitle(this);
                textRingtone.setText("Уведомление: " + title);
            } else {
                showToast("Звук не выбран");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("SettingsActivity", "onDestroy called");
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

    // Обработка ответа от сервера
    RequestUtils.Callback callbackSendMessage = (fragment, result) -> {
        try {
            Log.d("ServerResponse", "Raw result: " + result);
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status");
            if (status == 0){
                showToast("Сообщение отправлено");
            }
            else {
                showToast("Ошибка на стороне сервера ERROR: "+status);
            }
            runOnUiThread(this::stopLoading);
            this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));
        } catch (Exception e) {
            new RequestUtils(this, "log", "POST", "{\"module\": \"SettingsActivity\", \"method\": \"callbackSendMessage\", \"error\": \"" + e + "\"}", callbackLog).execute();
            showToast("Ошибка callback.");
            runOnUiThread(this::stopLoading);
            this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));
        }
    };

    private void showSupportDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.support_dialog, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(dialogView);

        Button btnSend = dialogView.findViewById(R.id.btnSend);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        EditText editText = dialogView.findViewById(R.id.editTextTextMultiLine);

        btnSend.setOnClickListener(v -> {
            try {
                JSONObject loginData = new JSONObject();
                loginData.put("text", editText.getText().toString());
                loginData.put("driver_id", getUserId(this));

                startLoading();
                findViewById(R.id.loadingOverlay).setVisibility(View.VISIBLE);

                new RequestUtils(this, "support_message", "POST", loginData.toString(), callbackSendMessage).execute();
            } catch (JSONException e) {
                dialog.dismiss();
                runOnUiThread(this::stopLoading);
                this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));
                showToast("ОШИБКА: Сообщение не отправлено");
                new RequestUtils(this, "log", "POST", "{\"module\": \"SettingsActivity\", \"method\": \"showSupportDialog\", \"error\": \"" + e + "\"}", callbackLog).execute();
                throw new RuntimeException(e);
            }
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showMlDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.ml_dialog, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(dialogView);

        ImageButton buttonHelp = dialogView.findViewById(R.id.buttonHelp);
        Switch switchDetectionBlinking = dialogView.findViewById(R.id.switchDetectionBlinking);

        switchDetectionBlinking.setChecked(getDetectionBlinking(this));
        switchDetectionBlinking.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveDetectionBlinking(this, isChecked)
        );

        buttonHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Что такое функции достува нейронных сетей.")
                    .setMessage("Включая функцию, вы разрешаете нейронной сети запускаться и работать на вашем телефоне " +
                            "вне зависимости от доступа в интернет. Если функция отключена то нейронные сети включаются на " +
                            "телефоне тоько тогда, когда у вас пропадает связь с интернетом или связь очень плохого качества")
                    .setPositiveButton("OK", null)
                    .show();
        });

        dialog.show();
    }

    private void showLogoutDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.logout_dialog, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(dialogView);

        Button btnLogout = dialogView.findViewById(R.id.btnLogout);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            ToastUtils.showShortToast(SettingsActivity.this, "Выход выполнен");
            clearAllData(this);
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            startActivity(intent);
            finishAffinity();
            finish();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
    }

    private void showToast(String message) {
        this.runOnUiThread(() -> ToastUtils.showShortToast(this, message));
    }
}
