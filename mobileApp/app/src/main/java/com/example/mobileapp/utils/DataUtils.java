package com.example.mobileapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public class DataUtils {

    public static final String IP = "http://192.168.43.199:8000/";

    private static final String SHARED_PREF_NAME = "Prefs";
    private static final String KEY_ID = "userId";
    private static final String ENTRY = "entry";

    private static final String VIBRATION = "vibration_enabled";
    private static final String VOLUME = "volume_level";
    private static final String RINGTONE_URI = "ringtone_uri";
    private static final String DARK_MODE = "dark_mode";

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
}
