from flask import request, session, redirect, url_for
import json
import os
import threading
import time
from collections import deque
from typing import List, Dict, Union, Optional
import ast

import cv2
import joblib
import numpy as np
import torch
from flask import Flask, Response, render_template
from flask import request, session, redirect, url_for
from flask_cors import CORS
from flask_sock import Sock

from db import *
from fatigue_detector import CNNLSTM
from fatigue_detector import process_frame_for_fatigue
from modules import generate_credentials
from settings import EMAILS_SUPPORT
from smtp import send_email

import queue
from threading import Thread

app = Flask(__name__)
CORS(app, supports_credentials=True, resources={r"/*": {"origins": "*"}})
app.secret_key = 'supersecret'
sock = Sock(app)
latest_frames = {}
client_params = {}
notify_clients = set()
frame_queues = {} 

DATA_FILE = "static/json_data_message.json"

# Загрузка обученной модели при старте
model = CNNLSTM(input_size=136, num_classes=5).to("cuda")
model.load_state_dict(torch.load("ML/fatigue-detection-yawdd/src/best_model.pt", map_location="cuda"))
model.eval()

label_encoder = joblib.load("ML/fatigue-detection-yawdd/src/label_encoder.joblib")

def schedule_notifications_cleanup():
    # Периодическая очистка уведомлений
    while True:
        clear_all_notifications()
        time.sleep(12 * 60 * 60)

def start_worker(user_id, thresholds):
    # Запуск потока обработки кадров для user_id
    if user_id in frame_queues:
        return
    q = queue.Queue(maxsize=1)
    frame_queues[user_id] = q

    def worker():
        while True:
            try:
                frame = q.get(timeout=10)
                process_frame_for_fatigue(frame, user_id, thresholds, model, label_encoder)
            except queue.Empty:
                break

    t = Thread(target=worker, daemon=True)
    t.start()

# WebSocket для уведомлений
@sock.route('/ws_notify')
def ws_notify(ws):
    notify_clients.add(ws)
    try:
        while True:
            msg = ws.receive()
            if msg is None:
                break
    finally:
        notify_clients.discard(ws)

def broadcast_notification(data):
    import json
    dead = set()
    for ws in notify_clients:
        try:
            ws.send(json.dumps(data))
        except Exception:
            dead.add(ws)
    for ws in dead:
        notify_clients.discard(ws)

# Веб-интерфейс
@app.route("/")
def index():
    if not session.get("user"):
        return redirect(url_for("login"))
    if not session.get("agreement_accepted"):
        return redirect(url_for("user_agreement"))
    return redirect(url_for("main"))

@app.route('/favicon.ico')
def favicon():
    return redirect(url_for('static', filename='images/lock.ico'))

@app.route("/login", methods=["GET", "POST"])
def login():
    error = None
    if request.method == "POST":
        login = request.form.get("login")
        password = request.form.get("password")
        admins = get_all_admins()
        for admin in admins:
            if admin[1] == login and admin[2] == password:
                session["user"] = login
                session["admin_id"] = admin[0]
                session.pop("agreement_accepted", None)  
                return redirect(url_for("user_agreement")) 
        error = "Неверный логин или пароль"
    return render_template("login.html", error=error)

@app.route("/user_agreement", methods=["GET", "POST"])
def user_agreement():
    if not session.get("user"):
        return redirect(url_for("login"))
    error = None
    agreement_path = os.path.join(app.root_path, "static", "user_agreement.txt")
    with open(agreement_path, encoding="utf-8") as f:
        agreement_text = f.read()
    if request.method == "POST":
        choice = request.form.get("agreement")
        if choice == "yes":
            session["agreement_accepted"] = True
            return redirect(url_for("main"))
        else:
            error = "Вы должны принять пользовательское соглашение для продолжения."
    return render_template("user_agreement.html", agreement_text=agreement_text, error=error)

@app.route('/main')
def main():
    if 'user' not in session:
        return redirect('/login')
    if not session.get("agreement_accepted"):
        return redirect(url_for("user_agreement"))
    admin_id = session.get("admin_id")
    drivers = get_drivers_by_admin(admin_id)
    notifications_map = {
        driver.id: get_unread_notifications(driver.id)
        for driver in drivers
    }
    sorted_drivers = sorted(
        drivers,
        key=lambda d: len(notifications_map.get(d.id, [])) == 0
    )
    return render_template(
        "main.html",
        user=session.get("user"),
        admin_id=admin_id,
        drivers=sorted_drivers,
        notifications_map=notifications_map
    )

@app.route('/accounts')
def accounts():
    if session.get("admin_id") != 1:
        return redirect(url_for("main"))
    admins = get_all_admins()
    return render_template("accounts.html", user=session.get("user"), admins=admins)

@app.route('/driver/<int:driver_id>')
def driver_profile(driver_id):
    if 'user' not in session:
        return redirect('/login')
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM drivers WHERE id = %s", (driver_id,))
            driver = cur.fetchone()
    if not driver:
        return "Водитель не найден", 404
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT message, time, importance, is_read
                FROM notifications
                WHERE id_driver = %s
                ORDER BY time DESC
            """, (driver_id,))
            notifications = cur.fetchall()
    mark_notifications_as_read(driver_id)
    return render_template(
        "driver_profile.html",
        driver=driver,
        notifications=notifications
    )

@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))

@app.route('/mark_read/<int:driver_id>', methods=['POST'])
def mark_read(driver_id):
    mark_notifications_as_read(driver_id)
    return ('', 204)

@app.route('/admins', methods=['POST'])
def create_admin():
    login = request.form.get('login')
    password = request.form.get('password')
    if not login or not password:
        return redirect(url_for('accounts'))
    add_admin(login, password)
    return redirect(url_for('accounts'))

@app.route('/edit_admin/<int:admin_id>', methods=['POST'])
def edit_admin(admin_id):
    login = request.form.get('login')
    password = request.form.get('password')
    update_admin(admin_id, login, password)
    return redirect(url_for('accounts'))

@app.route('/delete_admin/<int:admin_id>', methods=['POST'])
def delete_admin(admin_id):
    if admin_id != 1:
        delete_admin_by_id(admin_id)
    return redirect(url_for('accounts'))

@app.route("/drivers_list", methods=["GET"])
def drivers_list():
    admin_id = int(session.get("admin_id"))
    drivers = get_drivers_by_admin(admin_id)
    return render_template("main.html", cars=drivers)

@app.route("/add_drivers", methods=["POST"])
def add_drivers():
    login, password = generate_credentials()
    first_name = request.form["first_name"]
    last_name = request.form["last_name"]
    phone = request.form["phone"]
    email = request.form["email"]
    age = int(request.form["age"])
    truck = request.form["truck"]
    admin_id = int(session.get("admin_id"))
    add_driver(login, password, first_name, last_name, phone, email, age, truck, admin_id)
    email_thread = threading.Thread(target=send_email, args=(email, "Guardian Gaze, Данные для входа", f"логин: {str(login)}\nпароль: {password}"))
    email_thread.start()
    return redirect(url_for("main"))

# отсюда продолжаю делать

@app.route('/edit_driver/<int:driver_id>', methods=['POST'])
def edit_driver(driver_id):
    if 'user' not in session:
        return redirect(url_for('login'))
    first_name = request.form.get('first_name')
    last_name = request.form.get('last_name')
    phone = request.form.get('phone')
    email = request.form.get('email')
    age = request.form.get('age')
    truck = request.form.get('truck')

    assigned_admin_id = get_admin_by_driver(driver_id)
    # if !assigned_admin_id:
    #     return

    update_driver(driver_id, first_name=first_name, last_name=last_name, phone=phone, email=email, age=age, truck=truck, assigned_admin_id=assigned_admin_id)
    return redirect(url_for('main'))

@app.route('/delete_driver/<int:driver_id>', methods=['POST'])
def delete_driver(driver_id):
    if 'user' not in session:
        return redirect(url_for('login'))
    admin_id = int(session.get("admin_id"))   
    # delete_admin_driver_relation(admin_id, driver_id)
    delete_driver_db(driver_id)
    return redirect(url_for('main'))

# Работа с уведомлениями (JSON)
def load_data() -> List[Dict[str, Union[int, str]]]:
    try:
        if not os.path.exists(DATA_FILE):
            with open(DATA_FILE, 'w', encoding='utf-8') as f:
                json.dump([], f)
            return []
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
            if not isinstance(data, list):
                raise json.JSONDecodeError("Invalid JSON format", doc=DATA_FILE, pos=0)
            return data
    except (json.JSONDecodeError, IOError) as e:
        print(f"Ошибка загрузки данных: {e}")
        return []

def save_data(data: List[Dict[str, Union[int, str]]]) -> None:
    try:
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except IOError as e:
        print(f"Ошибка сохранения данных: {e}")

def get_messages_by_id(id: int, importance_filter: Optional[str] = None) -> List[Dict[str, Union[int, str]]]:
    data = load_data()
    if importance_filter:
        return [item for item in data if item["id"] == id and item["importance"] == importance_filter]
    return [item for item in data if item["id"] == id]

def delete_messages_by_id(id: int, importance_filter: Optional[str] = None) -> None:
    data = load_data()
    if importance_filter:
        new_data = [item for item in data if not (item["id"] == id and item["importance"] == importance_filter)]
    else:
        new_data = [item for item in data if item["id"] != id]
    save_data(new_data)

def get_importance(message):
    if message == "Долгий наклон головы":
        return "низкая"
    elif message == "Долгое закрытие глаз":
        return "высокая"
    else:
        return "средняя"

@app.route('/authorize_driver', methods=['POST'])
def authorize_driver():
    try:
        data = request.get_json()
        login = data['login']
        password = data['password']
        info = check_driver_credentials(login, password)
        if info['status'] == 0:
            return jsonify({"status": 0, "driver_id": info['driver_id']})
        else:
            return jsonify({"status": 1})
    except Exception as e:
        print(f"ERROR: {e}")
        return jsonify({"status": 2})

@app.route('/support_message', methods=['POST'])
def support_message():
    try:
        data = request.get_json()
        text = data['text']
        driver_id = data['driver_id']
        email_thread = threading.Thread(target=send_email, args=(
            EMAILS_SUPPORT, "Сообщение поддержки Guardian Gaze", f"ID водителя: {driver_id}\nСообщение: {text}"))
        email_thread.start()
        return jsonify({"status": 0})
    except Exception as e:
        print(f"ERROR: {e}")
        return jsonify({"status": 1})

@app.route('/send_notification', methods=['POST'])
def send_notification():
    try:
        data = request.get_json()
        message = data['message']
        driver_id = data['driver_id']
        importance = get_importance(message)
        if add_notification(message, importance, driver_id):
            broadcast_notification({
                "driver_id": driver_id,
                "message": message,
                "importance": importance
            })
            return jsonify({"status": 0})
        else:
            return jsonify({"status": 1})
    except Exception as e:
        print(f"ERROR: {e}")
        return jsonify({"status": 2})

@app.route('/send_notification_list', methods=['POST'])
def send_notification_list():
    try:
        data = request.get_json()
        driver_id = data['driver_id']
        message_list = data['message_list']
        if isinstance(message_list, str):
            try:
                message_list = ast.literal_eval(message_list)
            except Exception as e:
                print("Ошибка преобразования строки в список:", e)
                return jsonify({"status": 2, "error": "Некорректный формат message_list"})
        success = True
        for message in message_list:
            importance = get_importance(message)
            if not add_notification(message, importance, driver_id):
                success = False
            else:
                broadcast_notification({
                    "driver_id": driver_id,
                    "message": message,
                    "importance": importance
                })
        return jsonify({"status": 0 if success else 1})
    except Exception as e:
        print(f"ERROR: {e}")
        return jsonify({"status": 2})

@app.route('/api/get_new_notifications/<int:driver_id>')
def get_new_notifications(driver_id):
    try:
        notifications = get_messages_by_id(driver_id)
        delete_messages_by_id(driver_id)
        return jsonify({"status": 0, "notifications": notifications})
    except Exception as e:
        print(e)
        return jsonify({"status": 1})

# Страница видеопотока
@app.route("/video_feed/<int:driver_id>")
def video_feed_page(driver_id):
    return render_template("video_feed.html", driver_id=driver_id)

# MJPEG-поток для видеостраницы
@app.route("/video_stream/<int:driver_id>")
def video_stream(driver_id):
    def generate():
        prev_time = time.time()
        while True:
            frame = latest_frames.get(driver_id)
            if frame is not None:
                current_time = time.time()
                prev_time = current_time
                _, jpeg = cv2.imencode('.jpg', frame)
                frame_bytes = jpeg.tobytes()
                yield (b'--frame\r\n'
                       b'Content-Type: image/jpeg\r\n\r\n' + frame_bytes + b'\r\n\r\n')
            else:
                time.sleep(0.01)
    return Response(generate(), mimetype='multipart/x-mixed-replace; boundary=frame')

# WebSocket для получения кадров от мобильного приложения
@sock.route('/ws')
def ws_handler(ws):
    user_id = None
    try:
        # Получаем параметры от клиента
        raw = ws.receive()
        try:
            params = json.loads(raw)
            user_id = params["user_id"]
            open_val = params["open"]
            closed_val = params["closed"]
            print(f"Подключился user_id={user_id}, open={open_val}, closed={closed_val}")
            client_params[user_id] = {"open": open_val, "closed": closed_val}
        except Exception as e:
            print("Ожидался JSON с параметрами, получено:", raw)
            ws.close()
            return
        # Основной цикл приёма бинарных JPEG-кадров
        while True:
            data = ws.receive()
            if data is None:
                break
            if isinstance(data, str):
                print(f"[user_id={user_id}] Получено текстовое сообщение во время передачи кадров — пропущено.")
                continue
            np_array = np.frombuffer(data, np.uint8)
            frame = cv2.imdecode(np_array, cv2.IMREAD_COLOR)
            if frame is not None:
                frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
                latest_frames[user_id] = frame
                thresholds = client_params.get(user_id)
                if thresholds:
                    start_worker(user_id, thresholds)
                    q = frame_queues[user_id]
                    if not q.full():
                        q.put(frame)
                    else:
                        try:
                            q.get_nowait()
                            q.put_nowait(frame)
                        except queue.Full:
                            pass
    except Exception as e:
        print(f"[user_id={user_id}] Ошибка: {e}")
    finally:
        if user_id in latest_frames:
            del latest_frames[user_id]
        if user_id in client_params:
            del client_params[user_id]
        if user_id in frame_queues:
            del frame_queues[user_id]

if __name__ == '__main__':
    create_database()
    init_reference_data()

    clear_all_notifications()
    cleanup_thread = threading.Thread(target=schedule_notifications_cleanup, daemon=True)
    cleanup_thread.start()
    app.run(host='0.0.0.0', port=8000, debug=True)




