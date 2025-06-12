from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.header import Header
import smtplib
import threading
import random
import string
from settings import SMTP_PASSWORD

smtp_server = 'smtp.yandex.ru'
smtp_port = 587
smtp_username = 'Fikys203@yandex.ru'
smtp_password = SMTP_PASSWORD


def start_smtp_server():
    try:
        server = smtplib.SMTP(smtp_server, smtp_port, timeout=10)
        server.set_debuglevel(0)  # debug output
        server.starttls()
        server.login(smtp_username, smtp_password)
        return server
    except Exception as ex:
        print(f"ERROR: {ex}")
        return None


def send_email(to_email, subject, message):
    try:
        server = start_smtp_server()
        if server is not None:
            msg = MIMEMultipart()
            msg['From'] = smtp_username
            msg['To'] = to_email
            msg['Subject'] = Header(subject, 'utf-8')  # Указываем кодировку темы письма

            # Добавляем тело письма с кодировкой utf-8
            msg.attach(MIMEText(f'<div style="text-align: center;">{message}</div>', 'html', 'utf-8'))

            # Отправляем сообщение в байтовом формате
            server.sendmail(smtp_username, to_email, msg.as_bytes())
            server.quit()
            return 0
        else:
            print("ERROR: No connection to SMTP server.")
            return 1
    except Exception as ex:
        print(f"ERROR: {ex}")
        return 2


def password_generation(length=8):
    characters = string.ascii_letters + string.digits
    password = ''.join(random.choice(characters) for _ in range(length))
    return password
