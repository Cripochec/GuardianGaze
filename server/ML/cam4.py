import cv2
import dlib
import numpy as np

# Инициализация
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")

# Под размеры камеры
frame_width = 640
frame_height = 480
heatmap = np.zeros((frame_height, frame_width), dtype=np.float32)

# Храним все точки взгляда
gaze_points = []

def get_pupil_position(eye_region, threshold=30):
    gray_eye = cv2.cvtColor(eye_region, cv2.COLOR_BGR2GRAY)
    _, threshold_eye = cv2.threshold(gray_eye, threshold, 255, cv2.THRESH_BINARY_INV)
    contours, _ = cv2.findContours(threshold_eye, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    if contours:
        largest_contour = max(contours, key=cv2.contourArea)
        M = cv2.moments(largest_contour)
        if M["m00"] != 0:
            cx = int(M["m10"] / M["m00"])
            cy = int(M["m01"] / M["m00"])
            return (cx, cy)
    return None

cap = cv2.VideoCapture(1)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    frame = cv2.resize(frame, (frame_width, frame_height))
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = detector(gray)

    for face in faces:
        landmarks = predictor(gray, face)

        left_eye_pts = [landmarks.part(i) for i in range(36, 42)]
        right_eye_pts = [landmarks.part(i) for i in range(42, 48)]

        left_x1, left_y1 = min([p.x for p in left_eye_pts]), min([p.y for p in left_eye_pts])
        left_x2, left_y2 = max([p.x for p in left_eye_pts]), max([p.y for p in left_eye_pts])
        right_x1, right_y1 = min([p.x for p in right_eye_pts]), min([p.y for p in right_eye_pts])
        right_x2, right_y2 = max([p.x for p in right_eye_pts]), max([p.y for p in right_eye_pts])

        left_eye_region = frame[left_y1:left_y2, left_x1:left_x2]
        right_eye_region = frame[right_y1:right_y2, right_x1:right_x2]

        left_pupil = get_pupil_position(left_eye_region)
        right_pupil = get_pupil_position(right_eye_region)

        if left_pupil and right_pupil:
            left_global = (left_x1 + left_pupil[0], left_y1 + left_pupil[1])
            right_global = (right_x1 + right_pupil[0], right_y1 + right_pupil[1])
            gaze_point = (
                int((left_global[0] + right_global[0]) / 2),
                int((left_global[1] + right_global[1]) / 2)
            )

            # Запоминаем точку
            gaze_points.append(gaze_point)

            # Обновляем тепловую карту
            cv2.circle(heatmap, gaze_point, 8, 1, -1)

        if left_pupil:
            cv2.circle(left_eye_region, left_pupil, 2, (0, 255, 0), -1)
        if right_pupil:
            cv2.circle(right_eye_region, right_pupil, 2, (0, 255, 0), -1)

    # Визуализация линии взгляда
    for i in range(1, len(gaze_points)):
        cv2.line(frame, gaze_points[i - 1], gaze_points[i], (0, 255, 255), 1)

    # Наложение тепловой карты
    heatmap_norm = cv2.normalize(heatmap, None, 0, 255, cv2.NORM_MINMAX)
    heatmap_color = cv2.applyColorMap(heatmap_norm.astype(np.uint8), cv2.COLORMAP_JET)
    overlay = cv2.addWeighted(frame, 0.7, heatmap_color, 0.3, 0)

    cv2.imshow("Gaze Tracking with Map", overlay)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()
