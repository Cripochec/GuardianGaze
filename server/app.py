from flask import Flask, request, jsonify, Response, session, render_template, redirect, url_for
from flask_cors import CORS, cross_origin
from db import *
from modules import generate_credentials
from flask_socketio import SocketIO, emit
from settings import EMAILS_SUPPORT
from smtp import password_generation, send_email
import threading

app = Flask(__name__)
CORS(app, supports_credentials=True, resources={r"/*": {"origins": "*"}})
app.secret_key = 'supersecret'


# WebSite
@app.route("/")
def index():
    if not session.get("user"):
        return redirect(url_for("login"))
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

        # Проверка логина и пароля по базе
        admins = get_all_admins()
        for admin in admins:
            if admin[1] == login and admin[2] == password:
                session["user"] = login
                session["admin_id"] = admin[0]
                return redirect(url_for("main"))
        error = "Неверный логин или пароль"
    return render_template("login.html", error=error)


@app.route('/main')
def main():
    if 'user' not in session:
        return redirect('/login')

    admin_id = session.get("admin_id")
    drivers = get_drivers_by_admin(admin_id)

    notifications_map = {
        driver.id: get_unread_notifications(driver.id)
        for driver in drivers
    }

    # Сортируем: сначала те, у кого есть уведомления
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

    # Получаем данные водителя
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM drivers WHERE id = %s", (driver_id,))
            driver = cur.fetchone()

    if not driver:
        return "Водитель не найден", 404

    # Получаем все уведомления
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT message, time, importance, is_read
                FROM notifications
                WHERE id_driver = %s
                ORDER BY time DESC
            """, (driver_id,))
            notifications = cur.fetchall()

    # Помечаем непрочитанные как прочитанные
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


    id_drivers = add_driver(login, password, first_name, last_name, phone, email, age, truck)
    admin_id = int(session.get("admin_id"))

    add_admin_driver_relation(admin_id, id_drivers)

    # send_sms_ru(phone, login, password)

    email_thread = threading.Thread(target=send_email, args=(email, "Guardian Gaze, Данные для входа", f"логин: {str(login)}\nпароль: {password}"))
    email_thread.start()

    return redirect(url_for("main"))

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
    update_driver(driver_id, first_name=first_name, last_name=last_name, phone=phone, email=email, age=age, truck=truck)
    return redirect(url_for('main'))

@app.route('/delete_driver/<int:driver_id>', methods=['POST'])
def delete_driver(driver_id):
    if 'user' not in session:
        return redirect(url_for('login'))

    admin_id = int(session.get("admin_id"))   
    delete_admin_driver_relation(admin_id, driver_id) 
    delete_driver_db(driver_id)
    return redirect(url_for('main'))


# PhoneApplication
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

if __name__ == '__main__':
    # create_database()
    # add_admin('admin1', 'admin1')



    # test_driver_id = 10  # замените на реальный ID водителя в вашей БД
    # test_message = "Водитель проявляет признаки усталостиs"
    # test_importance = "высокая"
    #
    # try:
    #     add_notification(test_message, test_importance, test_driver_id)
    #     print("✅ Уведомление успешно добавлено!")
    # except Exception as e:
    #     print("❌ Ошибка при добавлении уведомления:", e)



    app.run(host='0.0.0.0', port=8000, debug=True)


