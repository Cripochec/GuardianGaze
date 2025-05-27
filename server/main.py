import tkinter as tk
from tkinter import messagebox
import threading
import cv2
import dlib
import numpy as np
from PIL import Image, ImageTk
import time

# Инициализация моделей
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor("shape_predictor_68_face_landmarks.dat")

# Функции для работы с камерами
def run_cam1():
    cap = cv2.VideoCapture(1)
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = detector(gray)

        for face in faces:
            landmarks = predictor(gray, face)
            left_eye = [landmarks.part(i) for i in range(36, 42)]
            right_eye = [landmarks.part(i) for i in range(42, 48)]
            for point in left_eye + right_eye:
                cv2.circle(frame, (point.x, point.y), 2, (0, 255, 0), -1)

        # Отображение изображения в Tkinter
        cv2image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = Image.fromarray(cv2image)
        img_tk = ImageTk.PhotoImage(image=img)
        label_img.configure(image=img_tk)
        label_img.image = img_tk
        window.update()

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()

def run_cam2():
    cap = cv2.VideoCapture(1)
    blink_start_time = None
    EAR_THRESHOLD = 0.2
    BLINK_DURATION = 0.2

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        faces = detector(gray)

        for face in faces:
            landmarks = predictor(gray, face)
            left_eye = [landmarks.part(i) for i in range(36, 42)]
            right_eye = [landmarks.part(i) for i in range(42, 48)]

            left_ear = calculate_ear(left_eye)
            right_ear = calculate_ear(right_eye)
            ear = (left_ear + right_ear) / 2.0

            if ear < EAR_THRESHOLD:
                if blink_start_time is None:
                    blink_start_time = time.time()
            else:
                if blink_start_time is not None:
                    blink_duration = time.time() - blink_start_time
                    if blink_duration > BLINK_DURATION:
                        update_status_label("Долгое моргание!", "green")
                    else:
                        update_status_label("Обычное моргание", "green")
                    blink_start_time = None

        cv2image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = Image.fromarray(cv2image)
        img_tk = ImageTk.PhotoImage(image=img)
        label_img.configure(image=img_tk)
        label_img.image = img_tk
        window.update()

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()

def run_cam3():
    cap = cv2.VideoCapture(1)

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break

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
                left_ratio = left_pupil[0] / (left_x2 - left_x1)
                right_ratio = right_pupil[0] / (right_x2 - right_x1)

                gaze_ratio = (left_ratio + right_ratio) / 2
                if gaze_ratio < 0.4:
                    update_status_label("Смотрит ВЛЕВО", "green")
                elif gaze_ratio > 0.6:
                    update_status_label("Смотрит ВПРАВО", "green")
                else:
                    update_status_label("Смотрит ПРЯМО", "green")

        cv2image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        img = Image.fromarray(cv2image)
        img_tk = ImageTk.PhotoImage(image=img)
        label_img.configure(image=img_tk)
        label_img.image = img_tk
        window.update()

        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()

def calculate_ear(eye):
    A = np.linalg.norm(np.array([eye[1].x, eye[1].y]) - np.array([eye[5].x, eye[5].y]))
    B = np.linalg.norm(np.array([eye[2].x, eye[2].y]) - np.array([eye[4].x, eye[4].y]))
    C = np.linalg.norm(np.array([eye[0].x, eye[0].y]) - np.array([eye[3].x, eye[3].y]))
    ear = (A + B) / (2.0 * C)
    return ear

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

def update_status_label(message, color):
    status_label.config(text=message, fg=color)

# Графический интерфейс
window = tk.Tk()
window.title("Monitoring Application")

label_img = tk.Label(window)
label_img.pack()

button_cam1 = tk.Button(window, text="Запуск Cam1", command=lambda: threading.Thread(target=run_cam1).start())
button_cam1.pack()

button_cam2 = tk.Button(window, text="Запуск Cam2", command=lambda: threading.Thread(target=run_cam2).start())
button_cam2.pack()

button_cam3 = tk.Button(window, text="Запуск Cam3", command=lambda: threading.Thread(target=run_cam3).start())
button_cam3.pack()

# Метка для отображения уведомлений внизу экрана
status_label = tk.Label(window, text="", font=("Arial", 12), fg="green", anchor="s")
status_label.pack(side="bottom", fill="x")

window.mainloop()
