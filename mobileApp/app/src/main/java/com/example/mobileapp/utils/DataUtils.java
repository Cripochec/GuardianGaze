package com.example.mobileapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class DataUtils {

    // --- IP сервера
    public static final String IP = "192.168.43.199:8000";

    // --- Усталость
    public static final long FATIGUE_BLINK_DURATION_MS = 2000;
    public static final float FATIGUE_HEAD_TILT_DEGREES = 25f;
    public static final long FATIGUE_HEAD_TILT_DURATION_MS = 3000;


    private static final String SHARED_PREF_NAME = "Prefs";
    private static final String KEY_ID = "userId";
    private static final String ENTRY = "entry";

    private static final String VIBRATION = "vibration_enabled";
    private static final String VOLUME = "volume_level";
    private static final String RINGTONE_URI = "ringtone_uri";
    private static final String DARK_MODE = "dark_mode";
    private static final String CAMERA_SIDE = "camera_side";
    private static final String CAMERA_IP = "camera_ip";
    private static final String EYE_OPEN_CALIB = "eye_open_calib";
    private static final String EYE_CLOSED_CALIB = "eye_closed_calib";
    private static final String ML_DETECTION_BLINKING = "ml_detection_blinking";


    // --- Очистка всех данных
    public static void clearAllData(Context context) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.clear().apply();
    }

    // --- ID
    public static void saveUserId(Context context, int userId) {
        getEditor(context).putInt(KEY_ID, userId).apply();
    }

    public static int getUserId(Context context) {
        return getPrefs(context).getInt(KEY_ID, 0);
    }

    // --- Entry
    public static void saveEntry(Context context, boolean entry) {
        getEditor(context).putBoolean(ENTRY, entry).apply();
    }

    public static boolean getEntry(Context context) {
        return getPrefs(context).getBoolean(ENTRY, true);
    }

    // --- Вибрация
    public static void saveVibrationEnabled(Context context, boolean enabled) {
        getEditor(context).putBoolean(VIBRATION, enabled).apply();
    }

    public static boolean isVibrationEnabled(Context context) {
        return getPrefs(context).getBoolean(VIBRATION, true);
    }

    // --- Громкость
    public static void saveVolumeLevel(Context context, int level) {
        getEditor(context).putInt(VOLUME, level).apply();
    }

    public static int getVolumeLevel(Context context) {
        return getPrefs(context).getInt(VOLUME, 5);
    }

    // --- Звук
    public static void saveRingtoneUri(Context context, String uri) {
        getEditor(context).putString(RINGTONE_URI, uri).apply();
    }

    public static String getRingtoneUri(Context context) {
        return getPrefs(context).getString(RINGTONE_URI, RingtoneUriDefault());
    }

    private static String RingtoneUriDefault() {
        return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString();
    }

    // --- Тема
    public static void saveDarkMode(Context context, boolean dark) {
        getEditor(context).putBoolean(DARK_MODE, dark).apply();
    }

    public static boolean isDarkModeEnabled(Context context) {
        return getPrefs(context).getBoolean(DARK_MODE, false);
    }

    // --- Вспомогательные методы
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor getEditor(Context context) {
        return getPrefs(context).edit();
    }

    // Камера слева или справа
    public static void saveCameraSide(Context context, String side) {
        getEditor(context).putString(CAMERA_SIDE, side).apply();
    }
    public static String getCameraSide(Context context) {
        return getPrefs(context).getString(CAMERA_SIDE, "right"); // по умолчанию слева
    }

    // IP-камера
    public static void saveCameraIp(Context context, String ip) {
        getEditor(context).putString(CAMERA_IP, ip).apply();
    }
    public static String getCameraIp(Context context) {
        return getPrefs(context).getString(CAMERA_IP, ""); // по умолчанию пусто
    }

    // Калибровка глаз
    public static void saveCalibratedOpen(Context context, float value) {
        getEditor(context).putFloat(EYE_OPEN_CALIB, value).apply();
    }
    public static float getCalibratedOpen(Context context) {
        return getPrefs(context).getFloat(EYE_OPEN_CALIB, 0.8f);
    }
    public static void saveCalibratedClosed(Context context, float value) {
        getEditor(context).putFloat(EYE_CLOSED_CALIB, value).apply();
    }
    public static float getCalibratedClosed(Context context) {
        return getPrefs(context).getFloat(EYE_CLOSED_CALIB, 0.2f);
    }


    // --- Нейронные сети
    public static void saveDetectionBlinking(Context context, boolean status) {
        getEditor(context).putBoolean(ML_DETECTION_BLINKING, status).apply();
    }

    public static boolean getDetectionBlinking(Context context) {
        return getPrefs(context).getBoolean(ML_DETECTION_BLINKING, false);
    }
}
