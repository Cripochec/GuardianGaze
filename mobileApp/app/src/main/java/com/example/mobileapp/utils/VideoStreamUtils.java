package com.example.mobileapp.utils;

import android.util.Log;
import okio.ByteString;
import okhttp3.*;

import java.util.concurrent.TimeUnit;

/**
 * VideoStreamUtils – класс для открытия WebSocket-соединения и отправки JPEG-кадров.
 *
 * Пример использования:
 *   VideoStreamUtils stream = new VideoStreamUtils("ws://<SERVER_IP>:<PORT>/socket.io/?EIO=4&transport=websocket");
 *   stream.connect();
 *   stream.sendFrame(jpegBytes);
 *   ...
 *   stream.disconnect();
 */
public class VideoStreamUtils {
    private static final String TAG = "VideoStreamUtils";

    private final String socketUrl;
    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean isConnected = false;

    public interface Listener {
        void onConnected();
        void onDisconnected();
        void onError(Throwable t);
    }

    private final Listener listener;

    // В конструкторе передаётся URL WebSocket (socket.io) и колбэк
    public VideoStreamUtils(String socketUrl, Listener listener) {
        this.socketUrl = socketUrl;
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // чтобы WebSocket не рвался
                .build();
    }

    /**
     * Запускает WebSocket-соединение.
     */
    public void connect() {
        Request request = new Request.Builder()
                .url(socketUrl)
                .build();
        webSocket = client.newWebSocket(request, new SocketListener());
        // client.dispatcher().executorService() будет поддерживать цикл событий
    }

    /**
     * Отправка кадра (JPEG-байты) через WebSocket
     * Если не подключено, просто игнорирует.
     */
    public void sendFrame(byte[] jpegBytes) {
        if (!isConnected || webSocket == null) return;
        // Socket.IO имеет собственный «заголовок» frames, но здесь мы просто шлём «чистые» байты.
        // На Flask-SocketIO можно принять бинарные данные напрямую.
        webSocket.send(ByteString.of(jpegBytes));
    }

    /**
     * Закрывает WebSocket
     */
    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
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
