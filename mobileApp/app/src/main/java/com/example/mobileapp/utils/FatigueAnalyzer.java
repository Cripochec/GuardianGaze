package com.example.mobileapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

public class FatigueAnalyzer implements ImageAnalysis.Analyzer {
    public interface Listener {
        void onBlink();
        void onHeadTilt();
    }

    private final Context context;
    private final Listener listener;
    private final FaceDetector detector;

    // Для моргания
    private boolean eyesClosed = false;
    private long eyesClosedStart = 0;
    private boolean blinkAlerted = false;

    // Для наклона головы
    private boolean headTilted = false;
    private long headTiltStart = 0;
    private boolean tiltAlerted = false;

    public FatigueAnalyzer(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    processFaces(faces);
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Log.e("FatigueAnalyzer", "Face detection error", e);
                    imageProxy.close();
                });
    }

    private void processFaces(List<Face> faces) {
        if (faces == null || faces.isEmpty()) {
            resetBlink();
            resetTilt();
            return;
        }
        Face face = faces.get(0); // Берём первое лицо

        // --- Моргание ---
        Float leftOpen = face.getLeftEyeOpenProbability();
        Float rightOpen = face.getRightEyeOpenProbability();

        float openCalibrated = DataUtils.getCalibratedOpen(context);
        float closedCalibrated = DataUtils.getCalibratedClosed(context);
        float threshold = closedCalibrated + (openCalibrated - closedCalibrated) * 0.5f; // 0.5 - середина, можно сделать настройку

        boolean isClosed = false;
        if (leftOpen != null && rightOpen != null) {
            isClosed = (leftOpen < threshold && rightOpen < threshold);
        }

        long now = SystemClock.elapsedRealtime();

        if (isClosed) {
            if (!eyesClosed) {
                eyesClosed = true;
                eyesClosedStart = now;
                blinkAlerted = false;
            } else {
                long duration = now - eyesClosedStart;
                if (duration >= DataUtils.FATIGUE_BLINK_DURATION_MS && !blinkAlerted) {
                    blinkAlerted = true;
                    if (listener != null) listener.onBlink();
                }
            }
        } else {
            eyesClosed = false;
            eyesClosedStart = 0;
            blinkAlerted = false;
        }

        // --- Наклон головы ---
        float headTilt = Math.abs(face.getHeadEulerAngleX()); // pitch (вверх/вниз)
        float headYaw = Math.abs(face.getHeadEulerAngleY());  // поворот влево/вправо
        float headRoll = Math.abs(face.getHeadEulerAngleZ()); // наклон вбок

        boolean isTilted = headTilt > DataUtils.FATIGUE_HEAD_TILT_DEGREES
                || headYaw > DataUtils.FATIGUE_HEAD_TILT_DEGREES
                || headRoll > DataUtils.FATIGUE_HEAD_TILT_DEGREES;

        if (isTilted) {
            if (!headTilted) {
                headTilted = true;
                headTiltStart = now;
                tiltAlerted = false;
            } else {
                long duration = now - headTiltStart;
                if (duration >= DataUtils.FATIGUE_HEAD_TILT_DURATION_MS && !tiltAlerted) {
                    tiltAlerted = true;
                    if (listener != null) listener.onHeadTilt();
                }
            }
        } else {
            headTilted = false;
            headTiltStart = 0;
            tiltAlerted = false;
        }
    }

    private void resetBlink() {
        eyesClosed = false;
        eyesClosedStart = 0;
        blinkAlerted = false;
    }

    private void resetTilt() {
        headTilted = false;
        headTiltStart = 0;
        tiltAlerted = false;
    }
}
