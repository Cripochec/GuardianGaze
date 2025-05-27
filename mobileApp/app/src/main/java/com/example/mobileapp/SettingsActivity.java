package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.*;

import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.mobileapp.utils.ToastUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_RINGTONE_PICKER = 1;
    private Uri selectedRingtoneUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch switchVibration = findViewById(R.id.switchVibration);
        SeekBar seekBarVolume = findViewById(R.id.seekBarVolume);
        TextView textVolume = findViewById(R.id.textVolume);
        TextView textRingtone = findViewById(R.id.textRingtone);
        Switch switchDarkMode = findViewById(R.id.switchDarkMode);
        ImageButton buttonBac = findViewById(R.id.buttonBac);
        ConstraintLayout out_panel = findViewById(R.id.out_panel);


        buttonBac.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
            startActivity(intent);
        });

        out_panel.setOnClickListener(v -> {
            showLogoutDialog();
        });

        // Вибрация
        switchVibration.setChecked(isVibrationEnabled(this));
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                saveVibrationEnabled(this, isChecked)
        );

        // Громкость
        int savedVolume = getVolumeLevel(this);
        seekBarVolume.setMax(10);
        seekBarVolume.setProgress(savedVolume);
        textVolume.setText("Громкость: " + savedVolume);

        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textVolume.setText("Громкость: " + progress);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                saveVolumeLevel(SettingsActivity.this, seekBar.getProgress());
            }
        });

        // Звук уведомлений
        Uri currentUri = Uri.parse(getRingtoneUri(this));
        String title = RingtoneManager.getRingtone(this, currentUri).getTitle(this);
        textRingtone.setText("Уведомление: " + title);

        textRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите звук");
            startActivityForResult(intent, REQUEST_RINGTONE_PICKER);
        });

        // Тёмная тема
        switchDarkMode.setChecked(isDarkModeEnabled(this));
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDarkMode(this, isChecked);
            recreate();
        });
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RINGTONE_PICKER && resultCode == RESULT_OK && data != null) {
            selectedRingtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (selectedRingtoneUri != null) {
                saveRingtoneUri(this, selectedRingtoneUri.toString());

                String title = RingtoneManager.getRingtone(this, selectedRingtoneUri).getTitle(this);
                TextView textRingtone = findViewById(R.id.textRingtone);
                textRingtone.setText("Уведомление: " + title);
            }
        }
    }
}
