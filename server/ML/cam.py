import cv2
import dlib

# Загрузка модели
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")


cap = cv2.VideoCapture(1)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    # Преобразуем изображение в оттенки серого
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    # Обнаружение лиц
    faces = detector(gray)
    for face in faces:
        landmarks = predictor(gray, face)

        # Получаем координаты глаз и преобразуем в список точек
        left_eye = [landmarks.part(i) for i in range(36, 42)]  # Левый глаз
        right_eye = [landmarks.part(i) for i in range(42, 48)]  # Правый глаз

        # Рисуем контуры глаз
        for point in left_eye + right_eye:
            cv2.circle(frame, (point.x, point.y), 2, (0, 255, 0), -1)

    cv2.imshow("Driver Monitoring", frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
