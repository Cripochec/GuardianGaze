import time
import cv2
import dlib
import numpy as np
import json
import torch
from typing import List, Dict, Union
import os
from collections import deque
from db import add_notification
import torch.nn as nn
import torch.nn.functional as F
import imageio


# === Модель ===
class CNNLSTM(nn.Module):
    def __init__(self, input_size, num_classes):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv1d(input_size, 128, kernel_size=3, padding=1),
            nn.BatchNorm1d(128),
            nn.ReLU(),
            nn.MaxPool1d(2),
            nn.Conv1d(128, 64, kernel_size=3, padding=1),
            nn.BatchNorm1d(64),
            nn.ReLU(),
            nn.MaxPool1d(2),
        )
        self.lstm = nn.LSTM(input_size=64, hidden_size=64, batch_first=True)
        self.fc = nn.Linear(64, num_classes)

    def forward(self, x):
        x = x.permute(0, 2, 1)
        x = self.cnn(x)
        x = x.permute(0, 2, 1)
        _, (h_n, _) = self.lstm(x)
        return self.fc(h_n[-1])

# === Пути ===
DATA_FILE = "static/json_data_message.json"

# === Константы ===
MAX_SEQ_LEN = 35
MIN_FRAME_INTERVAL = 1.0 / 15
ALERT_COOLDOWN_SECONDS = 10
CONFIDENCE_THRESHOLD = 0.8  # минимальная уверенность

# === Dlib ===
predictor_path = "ML/fatigue-detection-yawdd/src/shape_predictor_68_face_landmarks.dat"
face_detector = dlib.get_frontal_face_detector()
landmark_predictor = dlib.shape_predictor(predictor_path)

# === Состояния ===
user_states = {}
last_processed_time = {}
last_alert_time = {}

# === Работа с сообщениями ===
def load_data() -> List[Dict[str, Union[int, str]]]:
    if not os.path.exists(DATA_FILE):
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump([], f)
        return []
    try:
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return []

def save_data(data: List[Dict[str, Union[int, str]]]) -> None:
    try:
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except:
        pass

def add_message(id: int, message: str, importance: str = "средняя") -> None:
    data = load_data()
    data.append({"id": id, "message": message, "importance": importance})
    save_data(data)

# === Обработка кадра ===
def is_confident_prediction(output, threshold=CONFIDENCE_THRESHOLD):
    probs = F.softmax(output, dim=1)
    max_confidence, predicted = torch.max(probs, dim=1)
    return predicted.item(), max_confidence.item() >= threshold

def save_fatigue_frame(frame, user_id, label):
    os.makedirs("confirmed_fatigue_frames", exist_ok=True)
    fname = f"user{user_id}_{label}_{int(time.time())}.png"
    path = os.path.join("confirmed_fatigue_frames", fname)
    imageio.imwrite(path, cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
    print(f"[{user_id}] 💾 Сохранён кадр усталости: {path}")

def process_frame_for_fatigue(frame, user_id, thresholds, model, label_encoder):
    now = time.time()

    if user_id in last_processed_time and now - last_processed_time[user_id] < MIN_FRAME_INTERVAL:
        return
    last_processed_time[user_id] = now

    state = user_states.setdefault(user_id, {
        "sequence": deque(maxlen=MAX_SEQ_LEN),
        "event_times": {
            "yawning": [],
            "microsleep": [],
            "frequent_blinking": []
        },
        "gaze_deviation_start": None,
        "gaze_deviation_alerted": False,
        "last_label": None
    })

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    faces = face_detector(gray)
    if len(faces) == 0:
        return

    face = faces[0]
    shape = landmark_predictor(gray, face)
    coords = np.array([[shape.part(i).x, shape.part(i).y] for i in range(68)], dtype=np.float32)
    state["sequence"].append(coords)

    if len(state["sequence"]) == MAX_SEQ_LEN:
        arr = np.array(state["sequence"]).astype(np.float32)
        if arr.shape[1:] != (68, 2):
            print(f"[{user_id}] ❌ Неверная форма данных: {arr.shape}")
            return

        arr = arr.reshape(MAX_SEQ_LEN, -1)
        arr = (arr - np.mean(arr)) / (np.std(arr) + 1e-5)
        tensor = torch.tensor(arr, dtype=torch.float32).unsqueeze(0).to(next(model.parameters()).device)

        model.eval()
        with torch.no_grad():
            output = model(tensor)
            predicted_idx, confident = is_confident_prediction(output)

            if not confident:
                print(f"[{user_id}] ⚠️ Низкая уверенность, предсказание игнорировано")
                return

            predicted_label = label_encoder.inverse_transform([predicted_idx])[0]

            if predicted_label in ["heavy_eyelids", "long_blinking"]:
                return

            print(f"[{user_id}] ✅ Предсказано: {predicted_label} (уверенно)")

            # Сохраняем кадр, если уверенный microsleep
            if predicted_label == "microsleep":
                save_fatigue_frame(frame, user_id, predicted_label)

            # Подсчёт уникальных событий
            if predicted_label in ["yawning", "microsleep", "frequent_blinking"]:
                if state["last_label"] != predicted_label:
                    state["event_times"].setdefault(predicted_label, []).append(now)
                    state["event_times"][predicted_label] = [
                        t for t in state["event_times"][predicted_label] if now - t <= 30
                    ]
                    if len(state["event_times"][predicted_label]) >= 2:
                        cooldown_key = (user_id, predicted_label)
                        if cooldown_key not in last_alert_time or now - last_alert_time[cooldown_key] > ALERT_COOLDOWN_SECONDS:
                            importance_map = {
                                "yawning": "высокая",
                                "microsleep": "высокая",
                                "frequent_blinking": "средняя"
                            }
                            message_map = {
                                "yawning": "Обнаружено частое зевание",
                                "microsleep": "Обнаружены микро-сны",
                                "frequent_blinking": "Обнаружено частое моргание"
                            }
                            message = message_map[predicted_label]
                            importance = importance_map[predicted_label]
                            add_message(user_id, message, importance)
                            add_notification(message, importance, user_id)
                            last_alert_time[cooldown_key] = now

                state["last_label"] = predicted_label

            elif predicted_label == "gaze_deviation":
                if state["gaze_deviation_start"] is None:
                    state["gaze_deviation_start"] = now
                elif now - state["gaze_deviation_start"] > 5 and not state["gaze_deviation_alerted"]:
                    message = "Обнаружено отклонение взгляда более 5 секунд"
                    add_message(user_id, message, "низкая")
                    add_notification(message, "низкая", user_id)
                    state["gaze_deviation_alerted"] = True
                state["last_label"] = predicted_label
            else:
                state["gaze_deviation_start"] = None
                state["gaze_deviation_alerted"] = False
                state["last_label"] = "normal"

