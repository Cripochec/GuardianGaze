from flask import Flask, request, jsonify, Response, session, render_template, redirect, url_for
from json import load
from main import crop_vehicle, load_color_model, predict_color, detect_license_plate, predict_brand
import time
from ultralytics import YOLO
import easyocr
import config
import cv2
from db import get_all_admins, get_crossings, add_car, get_cars_by_admin

app = Flask(__name__)
app.secret_key = "your_secret_key"

# --- API вызовы ---


@app.route("/main")
def main():
    if "user" not in session:
        return redirect(url_for("login"))
    # Проверка настроена ли камера
    camera_configured = bool(session.get("camera_type") or session.get("camera_url") or session.get("camera_id"))
    # Фильтры crossings
    start_date = request.args.get("start_date")
    end_date = request.args.get("end_date")
    start_time = request.args.get("start_time")
    end_time = request.args.get("end_time")
    crossings = get_crossings(start_date, end_date, start_time, end_time)
    return render_template(
        "main.html",
        camera_configured=camera_configured,
        crossings=crossings
    )


@app.route("/camera_roi", methods=["GET", "POST"])
def camera_roi():
    # Сбросить прошлые настройки ROI при входе на страницу
    if request.method == "GET":
        session.pop("roi", None)
    if request.method == "POST":
        try:
            x = int(request.form.get("x", 0))
            y = int(request.form.get("y", 0))
            w = int(request.form.get("w", 0))
            h = int(request.form.get("h", 0))
            # Проверка на неотрицательные и ненулевые значения
            if w <= 0 or h <= 0:
                raise ValueError("Некорректная область ROI")
            session["roi"] = {"x": x, "y": y, "w": w, "h": h}
            return redirect(url_for("main"))
        except (ValueError, TypeError):
            # Можно добавить flash или error для пользователя
            return render_template("camera_roi.html",
                                   error="Некорректная область ROI. Пожалуйста, выберите область заново.")
    return render_template("camera_roi.html")


@app.route("/cars_list", methods=["GET"])
def cars_list():
    admin_id = int(session.get("admin_id"))
    cars = get_cars_by_admin(admin_id)
    return render_template("cars_list.html", cars=cars)


@app.route("/add_car", methods=["POST"])
def add_car_route():
    brand = request.form["brand"]
    model = request.form["model"]
    color = request.form["color"]
    plate = request.form["plate"]
    owner = request.form["owner"]
    admin_id = int(session.get("admin_id"))
    add_car(brand, model, color, plate, "", admin_id, owner)
    return redirect(url_for("cars_list"))


@app.route("/predict", methods=["POST"])
def predict():
    """
    Ожидает файл изображения (form-data, ключ 'image').
    Возвращает предсказания цвета, бренда и госномера.
    """
    if "image" not in request.files:
        return jsonify({"error": "No image uploaded"}), 400
    file = request.files["image"]
    img_path = "temp_upload.jpg"
    file.save(img_path)

    cropped_image = crop_vehicle(img_path, visualize=False, yolo_model=yolo_model)
    if not cropped_image:
        return jsonify({"error": "Vehicle not found"}), 404

    predicted_color, color_conf = predict_color(
        pil_image=cropped_image,
        model=model,
        transform=transform,
        class_names=class_names,
        device=device,
        visualize=False
    )
    predicted_brand, brand_conf = predict_brand(
        pil_image=cropped_image,
        model=brand_model,
        transform=brand_transform,
        class_names=brand_names,
        device=brand_device,
        visualize=False
    )
    license_plate = detect_license_plate(
        cropped_image,
        use_gpu=config.USE_GPU,
        visualize=False,
        reader=ocr_reader
    )

    return jsonify({
        "color": predicted_color,
        "color_confidence": color_conf,
        "brand": predicted_brand,
        "brand_confidence": brand_conf,
        "license_plate": license_plate
    })


if __name__ == '__main__':
    app.run(host="0.0.0.0", port=5000, debug=True)
