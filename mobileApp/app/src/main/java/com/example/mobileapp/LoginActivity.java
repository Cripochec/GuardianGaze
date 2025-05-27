package com.example.mobileapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobileapp.utils.RequestUtils;
import com.example.mobileapp.utils.ToastUtils;
import com.example.mobileapp.utils.DataUtils;

import org.json.JSONObject;


public class LoginActivity extends AppCompatActivity {

    private EditText editTextUsername, editTextPassword;
    private Button buttonLogin;
    private ImageButton buttonHelp;
    private com.airbnb.lottie.LottieAnimationView lottieAnimation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        editTextUsername = findViewById(R.id.editTextUsername);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonHelp = findViewById(R.id.buttonHelp);
        lottieAnimation = findViewById(R.id.lottieAnimation);


        buttonLogin.setOnClickListener(v -> {
            try {
                // Обработка нажатия на кнопку but_login
                String enteredEmail = editTextUsername.getText().toString(); // Получаем введенный email
                String enteredPassword = editTextPassword.getText().toString(); // Получаем введенный пароль

                if (!TextUtils.isEmpty(enteredEmail) && !TextUtils.isEmpty(enteredPassword)){

                    // Отправляем запрос на сервер для входа
                    JSONObject loginData = new JSONObject();
                    loginData.put("email", enteredEmail);
                    loginData.put("password", enteredPassword);

                    startLoading();
                    findViewById(R.id.loadingOverlay).setVisibility(View.VISIBLE);

//                    new RequestUtils(this, "entry_person", "POST", loginData.toString(), callbackEntryPerson).execute();

                    // Запускаем активити MainActivity.java
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                    
                } else {
                    // Показываем сообщение об ошибке, если поля пусты
                    showToast("\"Все поля должны быть заполнены\"");
                    clearEditText(); // Очищаем поля ввода
                }

            } catch (Exception e) {
                new RequestUtils(this, "log", "POST", "{\"module\": \"LoginActivity\", \"method\": \"buttonLogin.setOnClickListener\", \"error\": \"" + e + "\"}", callbackLog).execute();
            }
        });

        buttonHelp.setOnClickListener(v -> showToast("Если возникли проблемы со входом обратитесь к своему супервайзеру"));

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
    RequestUtils.Callback callbackEntryPerson = (fragment, result) -> {
        try {
            // Получение JSON объекта из ответа сервера
            JSONObject jsonObject = new JSONObject(result);
            int status = jsonObject.getInt("status"); // Получаем статус из ответа

            // status
            // 0 - успешно
            // 1 - email не найден
            // 2 - неверный логин или пароль
            // ~ - ошибка сервера
            if (status == 0){
                DataUtils.saveUserId(this, jsonObject.getInt("id_person")); // Сохраняем ID пользователя
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