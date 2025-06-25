import cv2
import dlib
import numpy as np
import os
import time
import csv
from datetime import datetime

# URL камеры (замените на свой IP и порт)
url = "http://192.168.3.13:8080/video"  # для IP Webcam

# === Настройки ===
SAVE_DIR = "../data/fatigue_dataset"
LABEL_FILE = os.path.join(SAVE_DIR, "labels.csv")
RECORD_DURATION = 5  # в секундах

LABEL_KEYS = {
    ord('q'): "yawning",
    ord('w'): "normal",
    ord('r'): "frequent_blinking",
    ord('y'): "gaze_deviation",
    ord('u'): "microsleep",
}

# === Dlib модель ===
PREDICTOR_PATH = "shape_predictor_68_face_landmarks.dat"
detector = dlib.get_frontal_face_detector()
predictor = dlib.shape_predictor(PREDICTOR_PATH)

# === Папка ===
os.makedirs(SAVE_DIR, exist_ok=True)

# === Извлечение признаков и визуализация ===
def extract_and_draw_landmarks(frame, gray):
    faces = detector(gray, 1)
    if len(faces) == 0:
        return None, frame
    shape = predictor(gray, faces[0])
    coords = np.array([[p.x, p.y] for p in shape.parts()])
    for (x, y) in coords:
        cv2.circle(frame, (x, y), 2, (0, 255, 0), -1)
    return coords, frame

# === Сохранение метки ===
def append_label(file_path, label):
    with open(LABEL_FILE, mode='a', newline='') as f:
        writer = csv.writer(f)
        writer.writerow([file_path, label])

# === Получение статистики по меткам ===
def get_label_stats():
    stats = {}
    if not os.path.exists(LABEL_FILE):
        return stats
    with open(LABEL_FILE, newline='') as f:
        reader = csv.reader(f)
        next(reader)  # skip header
        for _, label in reader:
            stats[label] = stats.get(label, 0) + 1
    return stats

# === Основной цикл ===
def main():
    # cap = cv2.VideoCapture(url)
    cap = cv2.VideoCapture(1)

    print("Нажмите клавишу для начала записи:")
    for k, v in LABEL_KEYS.items():
        print(f"  {chr(k)} - {v}")

    if not os.path.exists(LABEL_FILE):
        with open(LABEL_FILE, 'w', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(["file", "label"])

    recording = False
    label = None
    collected = []
    start_time = None

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        landmarks, vis_frame = extract_and_draw_landmarks(frame.copy(), gray)

        if recording:
            elapsed = time.time() - start_time
            cv2.putText(vis_frame, f"Recording: {label} {elapsed:.1f}s", (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, (0,0,255), 2)
            progress_length = int((elapsed / RECORD_DURATION) * 300)
            cv2.rectangle(vis_frame, (10, 60), (10 + progress_length, 80), (0, 0, 255), -1)
            if landmarks is not None:
                collected.append(landmarks)
            if elapsed > RECORD_DURATION:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                filename = f"{label}_{timestamp}.npy"
                save_path = os.path.join(SAVE_DIR, filename)
                np.save(save_path, np.array(collected))
                append_label(filename, label)
                print(f"\n✅ Сохранено: {save_path} ({len(collected)} кадров)")
                collected.clear()
                recording = False

        # Статистика
        stats = get_label_stats()
        y0 = 100
        for i, (lbl, count) in enumerate(sorted(stats.items())):
            cv2.putText(vis_frame, f"{lbl}: {count}", (10, y0 + i * 20), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255,255,255), 1)

        cv2.imshow("Capture", vis_frame)
        key = cv2.waitKey(1) & 0xFF

        if key == 27:
            break
        elif key in LABEL_KEYS and not recording:
            label = LABEL_KEYS[key]
            print(f"▶️ Запись: {label} ({RECORD_DURATION} секунд)")
            recording = True
            collected.clear()
            start_time = time.time()

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
