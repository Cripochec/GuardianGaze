import cv2
import dlib
import time
import numpy as np

# Инициализация dlib
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")

# Функция для вычисления EAR (Eye Aspect Ratio)
def calculate_ear(eye):
    A = np.linalg.norm(np.array([eye[1].x, eye[1].y]) - np.array([eye[5].x, eye[5].y]))
    B = np.linalg.norm(np.array([eye[2].x, eye[2].y]) - np.array([eye[4].x, eye[4].y]))
    C = np.linalg.norm(np.array([eye[0].x, eye[0].y]) - np.array([eye[3].x, eye[3].y]))
    ear = (A + B) / (2.0 * C)
    return ear

# Параметры моргания
EAR_THRESHOLD = 0.2  # Порог, ниже которого считается морганием
BLINK_DURATION = 0.2  # Длительность в секундах для долгого моргания

blink_start_time = None  # Время начала моргания

# Захват видео с камеры
cap = cv2.VideoCapture(1)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = detector(gray)

    for face in faces:
        landmarks = predictor(gray, face)

        # Получаем координаты глаз
        left_eye = [landmarks.part(i) for i in range(36, 42)]
        right_eye = [landmarks.part(i) for i in range(42, 48)]

        # Вычисляем EAR для обоих глаз
        left_ear = calculate_ear(left_eye)
        right_ear = calculate_ear(right_eye)

        # Средний EAR
        ear = (left_ear + right_ear) / 2.0

        # Проверяем моргание
        if ear < EAR_THRESHOLD:
            if blink_start_time is None:
                blink_start_time = time.time()
        else:
            if blink_start_time is not None:
                blink_duration = time.time() - blink_start_time
                if blink_duration > BLINK_DURATION:
                    print("0")  # Долгое моргание
                else:
                    print("1")  # Обычное моргание
                blink_start_time = None  # Сброс

    cv2.imshow("Driver Monitoring", frame)
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
