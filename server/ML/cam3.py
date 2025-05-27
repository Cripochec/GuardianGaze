import cv2
import dlib
import numpy as np

# Инициализация dlib
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")


# Функция для определения зрачка
def get_pupil_position(eye_region, threshold=30):
    gray_eye = cv2.cvtColor(eye_region, cv2.COLOR_BGR2GRAY)
    _, threshold_eye = cv2.threshold(gray_eye, threshold, 255, cv2.THRESH_BINARY_INV)

    contours, _ = cv2.findContours(threshold_eye, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    if contours:
        largest_contour = max(contours, key=cv2.contourArea)
        M = cv2.moments(largest_contour)
        if M["m00"] != 0:
            cx = int(M["m10"] / M["m00"])  # X-координата зрачка
            cy = int(M["m01"] / M["m00"])  # Y-координата зрачка
            return (cx, cy)
    return None


# Захват видео
cap = cv2.VideoCapture(1)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = detector(gray)

    for face in faces:
        landmarks = predictor(gray, face)

        # Координаты глаз
        left_eye_pts = [landmarks.part(i) for i in range(36, 42)]
        right_eye_pts = [landmarks.part(i) for i in range(42, 48)]

        # Вычисление прямоугольников вокруг глаз
        left_x1, left_y1 = min([p.x for p in left_eye_pts]), min([p.y for p in left_eye_pts])
        left_x2, left_y2 = max([p.x for p in left_eye_pts]), max([p.y for p in left_eye_pts])

        right_x1, right_y1 = min([p.x for p in right_eye_pts]), min([p.y for p in right_eye_pts])
        right_x2, right_y2 = max([p.x for p in right_eye_pts]), max([p.y for p in right_eye_pts])

        # Вырезаем области глаз
        left_eye_region = frame[left_y1:left_y2, left_x1:left_x2]
        right_eye_region = frame[right_y1:right_y2, right_x1:right_x2]

        # Определяем положение зрачков
        left_pupil = get_pupil_position(left_eye_region)
        right_pupil = get_pupil_position(right_eye_region)

        # Определяем направление взгляда
        if left_pupil and right_pupil:
            left_ratio = left_pupil[0] / (left_x2 - left_x1)
            right_ratio = right_pupil[0] / (right_x2 - right_x1)

            gaze_ratio = (left_ratio + right_ratio) / 2

            if gaze_ratio < 0.4:
                direction = "Смотрит ВЛЕВО"
            elif gaze_ratio > 0.6:
                direction = "Смотрит ВПРАВО"
            else:
                direction = "Смотрит ПРЯМО"

            print(direction)

        # Отображаем глаза и зрачки
        if left_pupil:
            cv2.circle(left_eye_region, left_pupil, 2, (0, 255, 0), -1)
        if right_pupil:
            cv2.circle(right_eye_region, right_pupil, 2, (0, 255, 0), -1)

    cv2.imshow("Driver Monitoring", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
