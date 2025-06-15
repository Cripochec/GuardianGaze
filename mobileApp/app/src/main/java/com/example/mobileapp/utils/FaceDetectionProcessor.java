package com.example.mobileapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FaceDetectionProcessor {

    public interface Callback {
        void onResult(List<RectF> faceRects, List<PointF> eyes, List<Boolean> eyesOpen);
    }

    private final FaceDetector detector;
    private final Context context;

    private static float lastLeftEyeOpenProbability = 0.8f; // по умолчанию открытый глаз

    public FaceDetectionProcessor(Context ctx) {
        this.context = ctx;
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Важно!
                .build();
        detector = com.google.mlkit.vision.face.FaceDetection.getClient(opts);
    }

    public static float getLastLeftEyeOpenProbability() {
        return lastLeftEyeOpenProbability;
    }

    public void detect(Bitmap bmp, Callback cb) {
        if (bmp == null) return;
        InputImage img = InputImage.fromBitmap(bmp, 0);
        detector.process(img)
                .addOnSuccessListener(faces -> {
                    List<RectF> faceRects = new ArrayList<>();
                    List<PointF> eyes = new ArrayList<>();
                    List<Boolean> eyesOpen = new ArrayList<>();

                    float openCalibrated = DataUtils.getCalibratedOpen(context);
                    float closedCalibrated = DataUtils.getCalibratedClosed(context);
                    float seekValue = DataUtils.getCalibratedClosed(context); // от 0.0 до 1.0
                    float threshold = closedCalibrated + (openCalibrated - closedCalibrated) * seekValue;

                    for (Face face : faces) {
                        faceRects.add(new RectF(face.getBoundingBox()));

                        FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
                        FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

                        if (leftEye != null) {
                            eyes.add(leftEye.getPosition());
                            Float open = face.getLeftEyeOpenProbability();
                            if (open != null) lastLeftEyeOpenProbability = open;
                            eyesOpen.add(open != null && open > threshold);
                        }
                        if (rightEye != null) {
                            eyes.add(rightEye.getPosition());
                            Float open = face.getRightEyeOpenProbability();
                            eyesOpen.add(open != null && open > threshold);
                        }
                    }
                    cb.onResult(faceRects, eyes, eyesOpen);
                })
                .addOnFailureListener(e -> cb.onResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
    }

}
