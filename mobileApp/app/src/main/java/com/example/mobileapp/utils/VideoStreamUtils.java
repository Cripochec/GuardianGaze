package com.example.mobileapp.utils;

import static com.example.mobileapp.utils.DataUtils.getUserId;

import android.content.Context;
import android.util.Log;
import okio.ByteString;
import okhttp3.*;

//VideoStreamUtils – класс для открытия WebSocket-соединения и отправки JPEG-кадров.
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class VideoStreamUtils {
    private static final String TAG = "VideoStreamUtils";

    private final String socketUrl;
    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnected = false;
    private final Context context;
    private boolean paramsSent = false;


    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(Throwable t);
    }

    private final Listener listener;

    // В конструкторе передаётся URL WebSocket (socket.io) и колбэк
    public VideoStreamUtils(Context context, String socketUrl, Listener listener) {
        this.context = context;
        this.socketUrl = socketUrl;
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    // Запускает WebSocket-соединение.
    public void connect() {
        Request request = new Request.Builder()
                .url(socketUrl)
                .build();
        webSocket = client.newWebSocket(request, new SocketListener());
//         client.dispatcher().executorService() будет поддерживать цикл событий
    }

    // Отправка кадра (JPEG-байты) через WebSocket. Если не подключено, просто игнорирует.
    public void sendFrame(byte[] jpegBytes) {
        if (!isConnected || webSocket == null) return;

        // Отправляем параметры только один раз за сессию
        if (!paramsSent) {
            int userId = getUserId(context);
            float open = DataUtils.getCalibratedOpen(context);
            float closed = DataUtils.getCalibratedClosed(context);

            Locale locale = Locale.US;
            String json = String.format(locale, "{\"user_id\":%d,\"open\":%f,\"closed\":%f}", userId, open, closed);

            webSocket.send(json);
            paramsSent = true;
        }

        webSocket.send(ByteString.of(jpegBytes));
    }

    // Закрывает WebSocket
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
        paramsSent = false;
    }

    // Геттер подключения
    public boolean isConnected() {
        return isConnected;
    }


    private class SocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            Log.i(TAG, "WebSocket opened");
            isConnected = true;
            if (listener != null) listener.onConnected();
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            // Сервер может отправить какие-то ответы (JSON), если нужно
            Log.i(TAG, "Received message: " + text);
        }

        @Override
        public void onMessage(WebSocket ws, ByteString bytes) {
            Log.i(TAG, "Received binary message, length=" + bytes.size());
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            Log.e(TAG, "WebSocket failure", t);
            isConnected = false;
            if (listener != null) listener.onError(t);
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            Log.i(TAG, "WebSocket closing: " + code + " / " + reason);
            ws.close(1000, null);
            isConnected = false;
            if (listener != null) listener.onDisconnected();
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            Log.i(TAG, "WebSocket closed: " + code + " / " + reason);
            isConnected = false;
            if (listener != null) listener.onDisconnected();
        }
    }
}
