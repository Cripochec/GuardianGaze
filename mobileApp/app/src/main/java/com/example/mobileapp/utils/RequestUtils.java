package com.example.mobileapp.utils;

import android.app.Activity;
import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RequestUtils {
    private final OkHttpClient client = new OkHttpClient();
    private final WeakReference<Context> contextRef;
    private final String requestLine;
    private final String method;
    private final String data;
    private final List<File> files;
    private final Callback callback;

    // Конструктор для обычных запросов (без файлов)
    public RequestUtils(Context context, String requestLine, String method, String data, Callback callback) {
        this.contextRef = new WeakReference<>(context);
        this.requestLine = requestLine;
        this.method = method;
        this.data = data;
        this.files = null;
        this.callback = callback;
    }

    // Конструктор для запросов с файлами
    public RequestUtils(Context context, String requestLine, String method, String data, List<File> files, Callback callback) {
        this.contextRef = new WeakReference<>(context);
        this.requestLine = requestLine;
        this.method = method;
        this.data = data;
        this.files = files;
        this.callback = callback;
    }

    public void execute() {
        String URL_SERVER = "http://" + DataUtils.IP + "/" + requestLine;

        Request.Builder requestBuilder = new Request.Builder().url(URL_SERVER);

        RequestBody requestBody;
        if (files != null && !files.isEmpty()) {
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            if (data != null && !data.isEmpty()) {
                multipartBuilder.addFormDataPart("json", data);
            }

            for (File file : files) {
                RequestBody fileBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
                multipartBuilder.addFormDataPart("photo", file.getName(), fileBody);
            }

            requestBody = multipartBuilder.build();
            requestBuilder.method(method, requestBody);
        } else {
            requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), data);
            requestBuilder.method(method.equals("GET") ? method : method, method.equals("GET") ? null : requestBody);
            requestBuilder.addHeader("Content-Type", "application/json");
        }

        Request request = requestBuilder.build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleResponse("ERROR " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    handleResponse(response.body().string());
                } else {
                    handleResponse("ERROR " + response.code() + " " + response.message());
                }
            }
        });
    }

    private void handleResponse(String result) {
        Context context = contextRef.get();
        if (context != null && callback != null) {
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(() -> callback.onResponse(context, result));
            } else {
                // Если это не Activity (например, Service), вызываем callback напрямую
                callback.onResponse(context, result);
            }
        }
    }

    public interface Callback {
        void onResponse(Context context, String result);
    }
}
