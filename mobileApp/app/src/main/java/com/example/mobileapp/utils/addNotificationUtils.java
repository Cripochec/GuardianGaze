package com.example.mobileapp.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import com.example.mobileapp.utils.RequestUtils;

import org.json.JSONObject;

public class addNotificationUtils {

    @SuppressLint("MissingPermission")
    public static void notifyFatigue(Context context, String message, VideoStreamUtils videoStream, RequestUtils.Callback callbackSendNotification, RequestUtils.Callback callbackLog) {
        // Вибрация
        if (DataUtils.isVibrationEnabled(context)) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        // Звук
        try {
            String ringtoneUriString = DataUtils.getRingtoneUri(context);
            Uri notificationUri = Uri.parse(ringtoneUriString);
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, notificationUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            int volumeLevel = DataUtils.getVolumeLevel(context); // от 0 до 10
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

        // Отправка на сервер
        if (videoStream != null && videoStream.isConnected() && !message.equals("Система готова к отслеживанию")) {
            try {
                JSONObject loginData = new JSONObject();
                loginData.put("message", message);
                loginData.put("driver_id", DataUtils.getUserId(context));

                new RequestUtils(context, "send_notification", "POST", loginData.toString(), callbackSendNotification).execute();
            } catch (Exception e) {
                Log.e("NotificationUtils", "addNotification" + e);
                new RequestUtils(context, "log", "POST", "{\"module\": \"NotificationUtils\", \"method\": \"addNotification\", \"error\": \"" + e + "\"}", callbackLog).execute();
            }
        }
    }
}