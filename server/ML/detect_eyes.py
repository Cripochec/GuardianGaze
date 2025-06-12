import cv2
import mediapipe as mp
import numpy as np
import json

# Загрузка настроек
with open("settings.json", "r") as f:
    config = json.load(f)

CAMERA_SIDE = config.get("camera_side", "left")
EAR_THRESHOLD = config.get("eye_aspect_ratio_threshold", 0.25)
LONG_BLINK_FRAMES = config.get("long_blink_duration_frames", 10)

mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(refine_landmarks=True)
drawing = mp.solutions.drawing_utils

# Координаты глаз (mediapipe)
LEFT_EYE_IDX = [33, 160, 158, 133, 153, 144]
RIGHT_EYE_IDX = [362, 385, 387, 263, 373, 380]

def euclidean(p1, p2):
    return np.linalg.norm(np.array(p1) - np.array(p2))

def eye_aspect_ratio(eye_landmarks):
    A = euclidean(eye_landmarks[1], eye_landmarks[5])
    B = euclidean(eye_landmarks[2], eye_landmarks[4])
    C = euclidean(eye_landmarks[0], eye_landmarks[3])
    return (A + B) / (2.0 * C)

def extract_eye_landmarks(face_landmarks, image_shape, indices):
    h, w = image_shape[:2]
    return [(int(face_landmarks.landmark[i].x * w), int(face_landmarks.landmark[i].y * h)) for i in indices]

# Для blink детекции
blink_counter = 0
long_blink_detected = False

# Камера
cap = cv2.VideoCapture(0)

print("Запуск. Нажмите Q для выхода")

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = face_mesh.process(frame_rgb)

    if results.multi_face_landmarks:
        for face_landmarks in results.multi_face_landmarks:
            if CAMERA_SIDE == "left":
                eye_indices = RIGHT_EYE_IDX
            else:
                eye_indices = LEFT_EYE_IDX

            eye_points = extract_eye_landmarks(face_landmarks, frame.shape, eye_indices)
            ear = eye_aspect_ratio(eye_points)

            # Визуализация глаз
            for p in eye_points:
                cv2.circle(frame, p, 2, (0, 255, 0), -1)

            # Моргание
            if ear < EAR_THRESHOLD:
                blink_counter += 1
            else:
                if blink_counter > LONG_BLINK_FRAMES:
                    long_blink_detected = True
                    cv2.putText(frame, "⚠️ Долгий морг!", (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 2)
                blink_counter = 0

            cv2.putText(frame, f"EAR: {ear:.2f}", (20, 450), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 1)

    cv2.imshow("Глаза и моргания", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
