package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.isDarkModeEnabled;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.mobileapp.utils.CameraHelper;
import com.example.mobileapp.utils.DataUtils;
import com.example.mobileapp.utils.FaceDetectionProcessor;
import com.example.mobileapp.utils.OverlayView;
import com.example.mobileapp.utils.ToastUtils;



public class EyeSettingsActivity extends AppCompatActivity {

    private com.airbnb.lottie.LottieAnimationView lottieAnimation;
    private Button btnCalibrateClosed;
    private CameraHelper cameraHelper;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDarkModeEnabled(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eye_settings);

        lottieAnimation = findViewById(R.id.lottieAnimation);
        btnCalibrateClosed = findViewById(R.id.btnCalibrateClosed);

        RadioGroup radioCameraSide = findViewById(R.id.radioCameraSide);
        RadioButton radioLeft = findViewById(R.id.radioLeft);
        RadioButton radioRight = findViewById(R.id.radioRight);
        SeekBar seekBarEye = findViewById(R.id.seekBarEye);
        TextView textEyeValue = findViewById(R.id.textEyeValue);
        EditText editIp = findViewById(R.id.editIpCamera);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnCalibrateOpen = findViewById(R.id.btnCalibrateOpen);
        ImageButton buttonBac = findViewById(R.id.buttonBac);
        ImageButton buttonHelp = findViewById(R.id.buttonHelp);

        TextureView textureView = findViewById(R.id.textureView);
        OverlayView overlayView = findViewById(R.id.overlayView);
        overlayView.bringToFront();
        overlayView.invalidate();

        cameraHelper = new CameraHelper(this, textureView, overlayView);

        buttonBac.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String side = radioLeft.isChecked() ? "left" : "right";
            DataUtils.saveCameraSide(this, side);
            DataUtils.saveCameraIp(this, editIp.getText().toString());
            ToastUtils.showShortToast(this, "Настройки взгляда сохранены");
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            finish();
        });

        buttonHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Как отколибровать взгляд.")
                    .setMessage("Нажмите на (кнопку колибровать закрытый глаз) и закройте глаза на 3 секунды, " +
                            "после откройте глаза и нажмите на кнопку колибровать открытый глаз. " +
                            "Протестируйте срабатывания, сщюрьтесь и смотрите загараються ли индикаторы глаз красным цветом, " +
                            "если нет то двигайте ползунок, чтобы донастроить чувствительность определения моргания. " +
                            "Если глаза часто определяются как закрытые — уменьшите значение. Если часто как открытые — увеличьте.")
                    .setPositiveButton("OK", null)
                    .show();
        });


        // Предзагрузка настроек
        if (DataUtils.getCameraSide(this).equals("left")) radioLeft.setChecked(true);
        else radioRight.setChecked(true);
        editIp.setText(DataUtils.getCameraIp(this));


        seekBarEye.setMax(100);
        seekBarEye.setMin(0);
        seekBarEye.setProgress((int)(DataUtils.getCalibratedClosed(this) * 100f));
        textEyeValue.setText(String.format("%.2f", DataUtils.getCalibratedClosed(this)));

        seekBarEye.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textEyeValue.setText(String.valueOf(progress / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                DataUtils.saveCalibratedClosed(EyeSettingsActivity.this, seekBarEye.getProgress() / 100f);
            }
        });

        btnCalibrateOpen.setOnClickListener(v -> {
            float lastOpen = FaceDetectionProcessor.getLastLeftEyeOpenProbability();
            DataUtils.saveCalibratedOpen(this, lastOpen);
            ToastUtils.showShortToast(this, "Калибровка открытого глаза сохранена: " + lastOpen);
        });

        btnCalibrateClosed.setOnClickListener(v -> {
            ToastUtils.showShortToast(this, "Закрой глаза — 3 секунды…");

            startLoading();
            findViewById(R.id.countdownOverlay).setVisibility(View.VISIBLE);

            new Handler().postDelayed(() -> {
                float lastClosed = FaceDetectionProcessor.getLastLeftEyeOpenProbability();
                DataUtils.saveCalibratedClosed(this, lastClosed);
                ToastUtils.showShortToast(this, "Сохранено: " + lastClosed);
                seekBarEye.setProgress((int)(DataUtils.getCalibratedClosed(this) * 100f));
                textEyeValue.setText(String.format("%.2f", DataUtils.getCalibratedClosed(this)));

                runOnUiThread(this::stopLoading);
                this.runOnUiThread(() -> findViewById(R.id.countdownOverlay).setVisibility(View.GONE));
            }, 3000);
        });

    }

    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
        btnCalibrateClosed.setEnabled(false); // Блокируем кнопку
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
        btnCalibrateClosed.setEnabled(true); // Разблокируем кнопку
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
            Log.d("EyeSettingsActivity", "Camera stopped in onDestroy");
        }
    }

}
