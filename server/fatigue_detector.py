import time
import cv2
import mediapipe as mp
import numpy as np
import json
from typing import List, Dict, Union
import os
from db import add_notification
from collections import deque

user_sequences = {}  # user_id -> deque из 35 кадров
SEQUENCE_LENGTH = 35


# Файл для хранения новых уведомлений
DATA_FILE = "static/json_data_message.json"

# === MediaPipe FaceMesh (один раз) ===
mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    static_image_mode=False,
    max_num_faces=1,
    refine_landmarks=True,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5
)

# Состояния пользователей
user_states = {}
last_processed_time = {}
PROCESS_INTERVAL = 1.0 / 5  # 5 FPS

# Индексы век
LEFT_EYE_TOP = 159
LEFT_EYE_BOTTOM = 145
RIGHT_EYE_TOP = 386
RIGHT_EYE_BOTTOM = 374

# Пороговые значения наклона головы
HEAD_TILT_PITCH_THRESHOLD = 80   # вверх/вниз
HEAD_TILT_YAW_THRESHOLD = 80     # влево/вправо
HEAD_TILT_DURATION = 3.0         # секунд

# Ключевые точки для оценки наклона головы (нос, глаза)
# Используем для упрощённой head pose estimation
INDEX_NOSE = 1
INDEX_LEFT_EYE = 33
INDEX_RIGHT_EYE = 263
INDEX_CHIN = 152
INDEX_MOUTH_LEFT = 61
INDEX_MOUTH_RIGHT = 291


def load_data() -> List[Dict[str, Union[int, str]]]:
    """Загружает данные из файла JSON"""
    try:
        if not os.path.exists(DATA_FILE):
            # Создаем файл с пустым списком, если его нет
            with open(DATA_FILE, 'w', encoding='utf-8') as f:
                json.dump([], f)
            return []

        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
            if not isinstance(data, list):  # Проверяем, что данные - это список
                raise json.JSONDecodeError("Invalid JSON format", doc=DATA_FILE, pos=0)
            return data
    except (json.JSONDecodeError, IOError) as e:
        print(f"Ошибка загрузки данных: {e}")
        return []  # Всегда возвращаем список, даже при ошибке

def save_data(data: List[Dict[str, Union[int, str]]]) -> None:
    """Сохраняет данные в файл JSON"""
    try:
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except IOError as e:
        print(f"Ошибка сохранения данных: {e}")

def add_message(id: int, message: str, importance: str = "средняя") -> None:
    """Добавляет новое сообщение"""
    data = load_data()
    if data is None:  # Дополнительная проверка на всякий случай
        data = []
    data.append({
        "id": id,
        "message": message,
        "importance": importance
    })
    save_data(data)


def get_head_pose_angles(landmarks, iw, ih):
    image_points = np.array([
        [landmarks[INDEX_NOSE].x * iw, landmarks[INDEX_NOSE].y * ih],      # Nose tip
        [landmarks[INDEX_CHIN].x * iw, landmarks[INDEX_CHIN].y * ih],      # Chin
        [landmarks[INDEX_LEFT_EYE].x * iw, landmarks[INDEX_LEFT_EYE].y * ih],  # Left eye
        [landmarks[INDEX_RIGHT_EYE].x * iw, landmarks[INDEX_RIGHT_EYE].y * ih], # Right eye
        [landmarks[61].x * iw, landmarks[61].y * ih],  # Left mouth corner
        [landmarks[291].x * iw, landmarks[291].y * ih]  # Right mouth corner
    ], dtype='double')

    model_points = np.array([
        [0.0, 0.0, 0.0],          # Nose tip
        [0.0, -63.0, -12.0],      # Chin
        [-33.0, 32.0, -26.0],     # Left eye
        [33.0, 32.0, -26.0],      # Right eye
        [-40.0, -35.0, -20.0],    # Left mouth corner
        [40.0, -35.0, -20.0]      # Right mouth corner
    ])

    focal_length = iw
    center = (iw / 2, ih / 2)
    camera_matrix = np.array([
        [focal_length, 0, center[0]],
        [0, focal_length, center[1]],
        [0, 0, 1]
    ], dtype='double')

    dist_coeffs = np.zeros((4, 1))

    success, rotation_vector, _ = cv2.solvePnP(
        model_points, image_points, camera_matrix, dist_coeffs, flags=cv2.SOLVEPNP_ITERATIVE
    )

    if not success:
        return 0.0, 0.0

    rmat, _ = cv2.Rodrigues(rotation_vector)
    pitch = np.degrees(np.arctan2(-rmat[2][1], rmat[2][2]))
    yaw = np.degrees(np.arctan2(-rmat[1][0], rmat[0][0]))
    return pitch, yaw

def process_frame_for_fatigue(frame, user_id, thresholds):
    now = time.time()
    if user_id in last_processed_time:
        if now - last_processed_time[user_id] < PROCESS_INTERVAL:
            return
    last_processed_time[user_id] = now

    if user_id not in user_states:
        user_states[user_id] = {
            "blink_start": None,
            "blink_detected": False,
            "head_tilt_start": None,
            "head_tilt_detected": False,
        }

    state = user_states[user_id]

    # === Масштабирование порогов глаз
    raw_open = thresholds["open"]
    raw_closed = thresholds["closed"]
    max_diff = raw_open - raw_closed
    if max_diff < 0.01:
        max_diff = 0.01

    SCALE = 0.01 / max_diff
    open_thresh = raw_open * SCALE
    closed_thresh = raw_closed * SCALE

    # === Детекция лица
    results = face_mesh.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    if not results.multi_face_landmarks:
        return

    landmarks = results.multi_face_landmarks[0].landmark
    ih, iw = frame.shape[:2]

    # === Расчёт открытия глаз
    def get_openness(top_idx, bottom_idx):
        top = landmarks[top_idx]
        bottom = landmarks[bottom_idx]
        y_top = top.y * ih
        y_bottom = bottom.y * ih
        return abs(y_bottom - y_top) / ih, (top, bottom)

    left_openness, (left_top, left_bottom) = get_openness(LEFT_EYE_TOP, LEFT_EYE_BOTTOM)
    right_openness, (right_top, right_bottom) = get_openness(RIGHT_EYE_TOP, RIGHT_EYE_BOTTOM)
    eye_openness = (left_openness + right_openness) / 2

    print(f"[{user_id}] Eye openness: {eye_openness:.3f} (open={open_thresh:.3f}, closed={closed_thresh:.3f})")

    # === Детекция моргания
    if not state["blink_detected"] and eye_openness < closed_thresh:
        print(f"[{user_id}] Blink started")
        state["blink_start"] = now
        state["blink_detected"] = True

    elif state["blink_detected"] and eye_openness > open_thresh:
        duration = now - state["blink_start"]
        state["blink_detected"] = False
        print(f"[{user_id}] Blink duration: {duration:.2f} sec")
        if duration > 0.8:
            print(f"[ALERT] [{user_id}] Долгое моргание!")
            add_message(user_id, "Долгое моргание!", "высокая")
            add_notification("Долгое моргание!", "высокая", user_id)


    # === Расчёт наклона головы
    pitch, yaw = get_head_pose_angles(landmarks, iw, ih)
    print(f"[{user_id}] Pitch: {pitch:.1f}°, Yaw: {yaw:.1f}°")

    if not state["head_tilt_detected"] and (abs(pitch) > HEAD_TILT_PITCH_THRESHOLD or abs(yaw) > HEAD_TILT_YAW_THRESHOLD):
        state["head_tilt_start"] = now
        state["head_tilt_detected"] = True
    elif state["head_tilt_detected"]:
        if abs(pitch) > HEAD_TILT_PITCH_THRESHOLD or abs(yaw) > HEAD_TILT_YAW_THRESHOLD:
            duration = now - state["head_tilt_start"]
            if duration > HEAD_TILT_DURATION:
                print(f"[ALERT] [{user_id}] Наклон головы более {HEAD_TILT_DURATION} сек! (pitch={pitch:.1f}, yaw={yaw:.1f})")
                add_message(user_id, f"Наклон головы более {HEAD_TILT_DURATION} сек!", "низкая")
                add_notification(f"Наклон головы более {HEAD_TILT_DURATION} сек!", "низкая", user_id)

        else:
            state["head_tilt_detected"] = False

    # === Расчёт усталости с помощью нейронной сети
    # model = GazeLSTMClassifier()
    # model.load_state_dict(torch.load("models/gaze_classifier.pt"))
    # model.eval()
    #
    # if len(state["gaze_sequence"]) == 30:
    #     gaze_tensor = torch.tensor([list(state["gaze_sequence"])], dtype=torch.float32)
    #     with torch.no_grad():
    #         prediction = model(gaze_tensor).argmax(dim=1).item()
    #         if prediction == 1:
    #             print(f"[{user_id}] Залипание взгляда!")
    #         elif prediction == 2:
    #             print(f"[{user_id}] Хаотичное движение взгляда!")


