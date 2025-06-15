package com.example.mobileapp;

import static com.example.mobileapp.utils.DataUtils.getEntry;
import static com.example.mobileapp.utils.DataUtils.isDarkModeEnabled;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.mobileapp.utils.RequestUtils;
import com.example.mobileapp.utils.ToastUtils;
import com.example.mobileapp.utils.DataUtils;

import org.json.JSONObject;


public class LoginActivity extends AppCompatActivity {

    private EditText editTextUsername, editTextPassword;
    private Button buttonLogin;
    private ImageButton buttonHelp;
    private com.airbnb.lottie.LottieAnimationView lottieAnimation;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isDarkModeEnabled(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (!getEntry(this)){
            // Запускаем активити MainActivity.java
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonHelp = findViewById(R.id.buttonHelp);
        lottieAnimation = findViewById(R.id.lottieAnimation);


        buttonLogin.setOnClickListener(v -> {
            try {
                // Обработка нажатия на кнопку but_login
                String enteredLogin = editTextUsername.getText().toString(); // Получаем введенный email
                String enteredPassword = editTextPassword.getText().toString(); // Получаем введенный пароль

                if (!TextUtils.isEmpty(enteredLogin) && !TextUtils.isEmpty(enteredPassword)){

                    // Отправляем запрос на сервер для входа
                    JSONObject loginData = new JSONObject();
                    loginData.put("login", enteredLogin);
                    loginData.put("password", enteredPassword);

                    startLoading();
                    findViewById(R.id.loadingOverlay).setVisibility(View.VISIBLE);

                    new RequestUtils(this, "authorize_driver", "POST", loginData.toString(), callbackAuthorizeDriver).execute();
                } else {
                    // Показываем сообщение об ошибке, если поля пусты
                    showToast("\"Все поля должны быть заполнены\"");
                    clearEditText(); // Очищаем поля ввода
                }

            } catch (Exception e) {
                runOnUiThread(this::stopLoading);
                this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));
                new RequestUtils(this, "log", "POST", "{\"module\": \"LoginActivity\", \"method\": \"buttonLogin.setOnClickListener\", \"error\": \"" + e + "\"}", callbackLog).execute();
            }
        });

        buttonHelp.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Возникла ошибка?")
                    .setMessage("Если возникли проблемы со входом обратитесь к своему супервайзеру или напишите на почту danilbiryukov2003@gmail.com")
                    .setPositiveButton("OK", null)
                    .show();
                });

        editTextPassword.setOnTouchListener((v, event) -> {
            final int DRAWABLE_END = 2; // Индекс иконки справа
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editTextPassword.getRight() - editTextPassword.getCompoundDrawables()[DRAWABLE_END].getBounds().width())) {
                    // Переключение видимости пароля
                    if (editTextPassword.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                        editTextPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        editTextPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_open, 0);
                    } else {
                        editTextPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        editTextPassword.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_eye_closed, 0);
                    }
                    // Устанавливаем курсор в конец текста
                    editTextPassword.setSelection(editTextPassword.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("LoginActivity", "Activity destroyed");

        // Остановка анимации Lottie
        if (lottieAnimation != null) {
            lottieAnimation.cancelAnimation();
            lottieAnimation = null;
        }

        // Очистка ссылок на view элементы
        editTextUsername = null;
        editTextPassword = null;
        buttonLogin = null;
        buttonHelp = null;
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
    RequestUtils.Callback callbackAuthorizeDriver = (fragment, result) -> {
        try {
            Log.d("ServerResponse", "Raw result: " + result);

            // Получение JSON объекта из ответа сервера
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status"); // Получаем статус из ответа
            // status
            // 0 - успешно
            // 1 - неверный логин или пароль
            // ~ - ошибка сервера
            if (status == 0){
                DataUtils.saveUserId(this, jsonObject.getInt("driver_id")); // Сохраняем ID пользователя
                DataUtils.saveEntry(this, false); // Сохраняем информацию о входе

                // Запускаем активити MainActivity.java
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }

            else if (status == 1){
                showToast("email не найден");
                clearEditText(); // Очищаем поля ввода
            }

            else if (status == 2){
                showToast("Неверный логин или пароль");
                clearEditText(); // Очищаем поля ввода
            }

            else {
                showToast("Ошибка на стороне сервера ERROR: "+status);
                clearEditText(); // Очищаем поля ввода
            }
            runOnUiThread(this::stopLoading);
            this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));

        } catch (Exception e) {
            new RequestUtils(this, "log", "POST", "{\"module\": \"LoginActivity\", \"method\": \"callbackEntryPerson\", \"error\": \"" + e + "\"}", callbackLog).execute();
            showToast("Ошибка callback.");
            runOnUiThread(this::stopLoading);
            this.runOnUiThread(() -> findViewById(R.id.loadingOverlay).setVisibility(View.GONE));
        }
    };

    private void startLoading() {
        lottieAnimation.setVisibility(View.VISIBLE);
        lottieAnimation.playAnimation();
        buttonLogin.setEnabled(false); // Блокируем кнопку входа
    }

    private void stopLoading() {
        lottieAnimation.cancelAnimation();
        lottieAnimation.setVisibility(View.GONE);
        buttonLogin.setEnabled(true); // Разблокируем кнопку входа
    }

    // Очистка полей для ввода текста
    public void clearEditText() {
        editTextUsername.setText(""); // Очищаем поле email
        editTextPassword.setText(""); // Очищаем поле пароля
    }

    // Метод для показа сообщения об ошибке
    private void showToast(String message) {
        this.runOnUiThread(() -> ToastUtils.showShortToast(this, message));
    }

}