from flask import Flask, request, jsonify, Response, session, render_template, redirect, url_for
from flask_cors import CORS, cross_origin
from db import *
from modules import generate_credentials

app = Flask(__name__)
CORS(app, supports_credentials=True, resources={r"/*": {"origins": "*"}})
app.secret_key = 'supersecret'


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

@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route('/main')
def main():
    if 'user' not in session:
        return redirect('/login')
    admin_id = session.get("admin_id")
    drivers = get_drivers_by_admin(admin_id)
    return render_template("main.html", user=session.get("user"), admin_id=admin_id, drivers=drivers)


@app.route('/accounts')
def accounts():
    if session.get("admin_id") != 1:
        return redirect(url_for("main"))
    admins = get_all_admins()
    return render_template("accounts.html", user=session.get("user"), admins=admins)


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


# edit_driver
# delete_driver


if __name__ == '__main__':
    # create_database()
    # add_admin('admin1', 'admin1')



    # test_driver_id = 5  # замените на реальный ID водителя в вашей БД
    # test_message = "Водитель проявляет признаки усталостиs"
    # test_importance = "высокая"
    #
    # try:
    #     add_notification(test_message, test_importance, test_driver_id)
    #     print("✅ Уведомление успешно добавлено!")
    # except Exception as e:
    #     print("❌ Ошибка при добавлении уведомления:", e)


    app.run(debug=True)


