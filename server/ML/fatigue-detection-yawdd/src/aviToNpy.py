import os
import cv2

import numpy as np
from concurrent.futures import ThreadPoolExecutor, as_completed
from tqdm import tqdm  # Добавляем прогресс-бар

import dlib
print("CUDA Enabled:", dlib.DLIB_USE_CUDA)         # ожидается True
print("Available GPUs:", dlib.cuda.get_num_devices())  # >= 1


# === Конфигурация ===
PREDICTOR_PATH = "shape_predictor_68_face_landmarks.dat"
DETECTOR_PATH = "mmod_human_face_detector.dat"
INPUT_DIR = "../data/Mirror/Female_mirror"
OUTPUT_DIR = "../data/Mirror/Female_features"

# === Инициализация ===
print("CUDA доступна в dlib:", dlib.DLIB_USE_CUDA)
face_detector = dlib.cnn_face_detection_model_v1(DETECTOR_PATH)
landmark_predictor = dlib.shape_predictor(PREDICTOR_PATH)

LEFT_EYE_IDX = list(range(36, 42))
RIGHT_EYE_IDX = list(range(42, 48))
MOUTH_IDX = list(range(48, 68))

# === Обработка одного видео ===
def extract_features_from_video(video_path):
    cap = cv2.VideoCapture(video_path)
    features_seq = []
    total_frames, found_frames = 0, 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break
        total_frames += 1
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        faces = face_detector(gray, 1)
        if faces:
            face_rect = faces[0].rect
            shape = landmark_predictor(gray, face_rect)
            coords = np.array([[p.x, p.y] for p in shape.parts()])
            feature = []
            for idx in LEFT_EYE_IDX + RIGHT_EYE_IDX + MOUTH_IDX:
                feature.extend(coords[idx])
            features_seq.append(feature)
            found_frames += 1

    cap.release()
    return np.array(features_seq, dtype=np.float32), total_frames, found_frames

# === Обработка всех видео ===
def process_video_file(fname):
    if not fname.endswith(".avi"):
        return None

    video_path = os.path.join(INPUT_DIR, fname)
    out_path = os.path.join(OUTPUT_DIR, fname.replace(".avi", ".npy"))
    if os.path.exists(out_path):
        return None

    features_seq, total, found = extract_features_from_video(video_path)
    if len(features_seq) > 0:
        np.save(out_path, features_seq)
        return f"✅ {fname}: найдено {found}/{total} ({found / total:.1%})"
    else:
        return f"⚠️  {fname}: лицо не найдено ({found}/{total})"

# === Запуск ===
def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    files = os.listdir(INPUT_DIR)
    avi_files = [f for f in files if f.endswith(".avi")]
    print(f"Обработка {len(avi_files)} видео...")

    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {executor.submit(process_video_file, f): f for f in avi_files}
        for fut in tqdm(as_completed(futures), total=len(futures), desc="Процесс обработки"):
            result = fut.result()
            if result:
                print(result)

if __name__ == "__main__":
    main()
