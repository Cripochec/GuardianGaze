package com.example.mobileapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mobileapp.utils.ToastUtils;
import com.github.appintro.AppIntro;
import com.github.appintro.AppIntroFragment;

public class IntroActivity extends AppIntro {

    private int currentSlideIndex = 0;
    private boolean flag = true;

    // Добавь поля для launcher'ов
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;
    private ActivityResultLauncher<String> requestNotificationPermissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(AppIntroFragment.createInstance(
                "Добро пожаловать!",
                "Это приложение поможет отслеживать усталость водителя.",
                R.drawable.logo2,
                R.color.custom_gray3
        ));

        AgreementSlideFragment agreementSlide = new AgreementSlideFragment();
        addSlide(agreementSlide);

        addSlide(AppIntroFragment.createInstance(
                "Как это работает?",
                "Приложение анализирует моргания и наклон головы водителя в реальном времени.",
                R.drawable.logo2,
                R.color.purple_500
        ));

        setSkipButtonEnabled(true);

        // Инициализация launcher'ов
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean granted = true;
                    for (Boolean ok : result.values()) {
                        if (!ok) {
                            granted = false;
                            break;
                        }
                    }
                    if (!granted) {
                        if (flag){
                            Toast.makeText(this, "Разрешения обязательны для работы приложения", Toast.LENGTH_SHORT).show();
                            flag = false;
                        }
                    }
                });

        requestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(this, "Разрешите показ уведомлений", Toast.LENGTH_SHORT).show();
                    }
                });

        // Запрос разрешений
        requestAllPermissions();
    }

    @Override
    public boolean onCanRequestNextPage() {
        // Проверяем только если сейчас открыт слайд с соглашением
        if (currentSlideIndex == 1) {
            Fragment fragment = getSupportFragmentManager().getFragments()
                    .stream()
                    .filter(f -> f != null && f.isVisible() && f instanceof AgreementSlideFragment)
                    .findFirst()
                    .orElse(null);

            if (fragment instanceof AgreementSlideFragment) {
                AgreementSlideFragment agreement = (AgreementSlideFragment) fragment;
                if (!agreement.isAgreed()) {
                    ToastUtils.showShortToast(this, "Для продолжения, примите условия соглашения");
                    return false;
                }
            }
        }

        return super.onCanRequestNextPage();
    }

    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        currentSlideIndex = position;
    }

    @Override
    protected void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        finishIntro();
    }

    private void finishIntro() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void requestAllPermissions() {
        // Камера
        String[] permissions = new String[] { Manifest.permission.CAMERA };

        // Запрос камеры
        requestPermissionsLauncher.launch(permissions);

        // Запрос уведомлений (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
