package com.example.mobileapp.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.Collections;

public class CameraHelper {

    private final Context context;
    private final TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private final HandlerThread backgroundThread;
    private final Handler backgroundHandler;
    private final OverlayView overlayView;
    private final FaceDetectionProcessor faceProcessor;

    private long lastDetectionTime = 0;
    private static final long DETECTION_INTERVAL_MS = 200; // раз в 200 мс
    private boolean isCameraStarted = false;


    public CameraHelper(Context context, TextureView textureView, OverlayView overlayView) {
        this.context = context;
        this.textureView = textureView;
        this.overlayView = overlayView;
        this.faceProcessor = new FaceDetectionProcessor(context);
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                startCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                stopCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                long now = System.currentTimeMillis();
                if (now - lastDetectionTime < DETECTION_INTERVAL_MS) return;
                lastDetectionTime = now;
                Bitmap bitmap = textureView.getBitmap();
                if (bitmap != null) {
                    faceProcessor.detect(bitmap, (faces, eyes, eyesOpen) -> {
                        overlayView.update(faces, eyes, eyesOpen);
                    });
                } else {
                    Log.w("CameraHelper", "Bitmap is null");
                }
            }
        });
    }

    public void startCamera() {
        if (isCameraStarted) return;

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("CameraHelper", "CAMERA permission not granted");
            return;
        }

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[1]; // Фронтальная — [1] если [0] это тыловая, проверь
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession();
                    isCameraStarted = true;
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                    Log.e("CameraHelper", "Camera error: " + error);
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e("CameraHelper", "CameraAccessException", e);
        }
    }



    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) {
                Log.e("CameraHelper", "SurfaceTexture is null");
                return;
            }

            texture.setDefaultBufferSize(640, 480); // Разрешение превью

            Surface surface = new Surface(texture);
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e("CameraHelper", "CameraAccessException: ", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("CameraHelper", "Configuration failed");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e("CameraHelper", "createCameraPreviewSession error: ", e);
        }
    }

    public void stopCamera() {
        ProcessCameraProvider.getInstance(context).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(context).get();
                cameraProvider.unbindAll();
                isCameraStarted = false;
            } catch (Exception e) {
                Log.e("CameraHelper", "Ошибка остановки камеры", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

}
