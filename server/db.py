import psycopg2
from datetime import datetime, timedelta

from flask import jsonify
from psycopg2 import sql
from psycopg2.extras import NamedTupleCursor

from settings import DB_HOST, DB_DATABASE, DB_USER, DB_PASSWORD

# Получение подключения к базе данных
def get_connection():
    return psycopg2.connect(
        host=DB_HOST,
        database=DB_DATABASE,
        user=DB_USER,
        password=DB_PASSWORD,
        cursor_factory=NamedTupleCursor
    )

# Создание всех необходимых таблиц и главного админа
def create_database():
    table_sql = """

        CREATE TYPE user_role AS ENUM ('admin', 'driver');
        CREATE TYPE importance_level AS ENUM ('высокая', 'средняя', 'низкая');

        CREATE TABLE IF NOT EXISTS users (
            id SERIAL PRIMARY KEY,
            login VARCHAR(100) UNIQUE NOT NULL,
            password VARCHAR(100) NOT NULL,
            role user_role NOT NULL
        );
        
        -- Индексы для таблицы users
        CREATE INDEX idx_users_role ON users(role);
        CREATE INDEX idx_users_login ON users(login); -- Для быстрого поиска по логину

        CREATE TABLE IF NOT EXISTS drivers (
            id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
            first_name VARCHAR(100),
            last_name VARCHAR(100),
            phone VARCHAR(20),
            email VARCHAR(100),
            age INTEGER,
            truck VARCHAR(100),
            assigned_admin_id INTEGER REFERENCES users(id) ON DELETE SET NULL
        );
        
        -- Индексы для таблицы drivers
        CREATE INDEX idx_drivers_admin ON drivers(assigned_admin_id);
        CREATE INDEX idx_drivers_name ON drivers(last_name, first_name); -- Для поиска по ФИО
        CREATE INDEX idx_drivers_phone ON drivers(phone); -- Для быстрого поиска по телефону
        
        CREATE TABLE IF NOT EXISTS importance_levels (
            id SERIAL PRIMARY KEY,
            level importance_level UNIQUE NOT NULL
        );
        
        CREATE TABLE IF NOT EXISTS message_templates (
            id SERIAL PRIMARY KEY,
            code VARCHAR(50) UNIQUE NOT NULL,
            message TEXT NOT NULL
        );
        
        -- Индекс для быстрого поиска шаблонов по коду
        CREATE INDEX idx_templates_code ON message_templates(code);

        CREATE TABLE IF NOT EXISTS notifications (
            id SERIAL PRIMARY KEY,
            id_driver INTEGER REFERENCES drivers(id) ON DELETE CASCADE,
            template_id INTEGER REFERENCES message_templates(id),
            time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            importance_id INTEGER REFERENCES importance_levels(id),
            is_read BOOLEAN DEFAULT FALSE
        );
        
        -- Индексы для таблицы notifications
        CREATE INDEX idx_notifications_driver ON notifications(id_driver);
        CREATE INDEX idx_notifications_importance ON notifications(importance_id);
        CREATE INDEX idx_notifications_time ON notifications(time DESC); -- Для сортировки по новизне
        CREATE INDEX idx_notifications_read_status ON notifications(is_read) WHERE is_read = FALSE; -- Частичный индекс для непрочитанных

        -- Дополнительная оптимизация для частых запросов
        -- Для поиска уведомлений по водителю и важности
        CREATE INDEX idx_notifications_driver_importance ON notifications(id_driver, importance_id);
        
        -- Для выборки уведомлений по шаблону
        CREATE INDEX idx_notifications_template ON notifications(template_id);
            """

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(table_sql)
            conn.commit()

        # Создание главного админа, если не существует
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM users WHERE login = %s AND role = 'admin'", ('admin',))
            if cur.fetchone() is None:
                cur.execute("""
                    INSERT INTO users (login, password, role)
                    VALUES (%s, %s, 'admin')
                """, ('admin', 'admin'))
                conn.commit()
                print("Аккаунт главного админа успешно создан")
            else:
                print("Аккаунт главного админа уже существует")

    print("Таблицы успешно созданы.")


def init_reference_data():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM importance_levels")
            if cur.fetchone()[0] == 0:
                cur.executemany("""
                    INSERT INTO importance_levels (level) VALUES (%s)
                """, [('высокая',), ('средняя',), ('низкая',)])

            cur.execute("SELECT COUNT(*) FROM message_templates")
            if cur.fetchone()[0] == 0:
                cur.executemany("""
                    INSERT INTO message_templates (code, message)
                    VALUES (%s, %s)
                """, [
                    ('yawning', "Обнаружено частое зевание"),
                    ('microsleep', "Обнаружены микро-сны"),
                    ('frequent_blinking', "Обнаружено частое моргание"),
                    ('gaze_deviation', "Обнаружено отклонение взгляда"),
                ])
        conn.commit()


def drop_all_tables():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                DROP TABLE IF EXISTS notifications CASCADE;
                DROP TABLE IF EXISTS drivers CASCADE;
                DROP TABLE IF EXISTS users CASCADE;
                DROP TABLE IF EXISTS importance_levels CASCADE;
                DROP TABLE IF EXISTS message_templates CASCADE;
            """)
            conn.commit()
            print("[Очистка] Все таблицы успешно удалены.")


# --- Операции с администраторами ---

def add_admin(login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO users (login, password, role)
                VALUES (%s, %s, 'admin')
                RETURNING id
            """, (login, password))
            return cur.fetchone()[0]


def update_admin(admin_id, login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE users SET login = %s, password = %s
                WHERE id = %s AND role = 'admin'
            """, (login, password, admin_id))
            conn.commit()


def delete_admin_by_id(admin_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            # Удаляем всех водителей, закреплённых за этим админом
            cur.execute("SELECT id FROM drivers WHERE assigned_admin_id = %s", (admin_id,))
            for row in cur.fetchall():
                driver_id = row[0]
                cur.execute("DELETE FROM notifications WHERE id_driver = %s", (driver_id,))
                cur.execute("DELETE FROM drivers WHERE id = %s", (driver_id,))
                cur.execute("DELETE FROM users WHERE id = %s", (driver_id,))

            cur.execute("DELETE FROM users WHERE id = %s AND role = 'admin'", (admin_id,))
            conn.commit()


def get_all_admins():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT id, login FROM users WHERE role = 'admin'")
            return cur.fetchall()

def get_admin_by_driver(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT assigned_admin_id FROM drivers WHERE id = %s", (driver_id,))
            row = cur.fetchone()
            if row:
                return cur.fetchall()
            else:
                print("get_admin_by_driver, Водитель не найден")

# --- Операции с водителями ---

def add_driver(login, password, first_name=None, last_name=None, phone=None, email=None, age=None, truck=None, assigned_admin_id=None):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO users (login, password, role)
                VALUES (%s, %s, 'driver') RETURNING id
            """, (login, password))
            user_id = cur.fetchone()[0]

            cur.execute("""
                INSERT INTO drivers (id, first_name, last_name, phone, email, age, truck, assigned_admin_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, (user_id, first_name, last_name, phone, email, age, truck, assigned_admin_id))
            conn.commit()
            return user_id


def update_driver(driver_id, **kwargs):
    if not kwargs:
        return
    with get_connection() as conn:
        with conn.cursor() as cur:
            fields = ', '.join([f"{key} = %s" for key in kwargs])
            values = list(kwargs.values()) + [driver_id]
            query = f"UPDATE drivers SET {fields} WHERE id = %s"
            cur.execute(query, values)
            conn.commit()


def delete_driver_db(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM notifications WHERE id_driver = %s", (driver_id,))
            cur.execute("DELETE FROM drivers WHERE id = %s", (driver_id,))
            cur.execute("DELETE FROM users WHERE id = %s AND role = 'driver'", (driver_id,))
            conn.commit()


def get_drivers_by_admin(admin_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT d.*, u.login
                FROM drivers d
                JOIN users u ON d.id = u.id
                WHERE d.assigned_admin_id = %s
            """, (admin_id,))
            return cur.fetchall()


def check_driver_credentials(login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id FROM users
                WHERE login = %s AND password = %s AND role = 'driver'
            """, (login, password))
            result = cur.fetchone()
            if result:
                return {"status": 0, "driver_id": result[0]}
            return {"status": 1}

# --- Операции с уведомлениями ---

def get_unread_notifications(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT mt.message, n.time, il.level
                FROM notifications n
                JOIN importance_levels il ON n.importance_id = il.id
                JOIN message_templates mt ON n.template_id = mt.id
                WHERE n.id_driver = %s AND n.is_read = FALSE
                ORDER BY n.time DESC
            """, (driver_id,))
            return cur.fetchall()


def add_notification(template_code, importance_level, driver_id):
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM message_templates WHERE code = %s", (template_code,))
                template_row = cur.fetchone()
                if not template_row:
                    raise ValueError("Неизвестный шаблон сообщения")

                template_id = template_row[0]

                cur.execute("SELECT id FROM importance_levels WHERE level = %s", (importance_level,))
                importance_row = cur.fetchone()
                if not importance_row:
                    raise ValueError("Неизвестный уровень важности")

                importance_id = importance_row[0]

                cur.execute("""
                    INSERT INTO notifications (id_driver, template_id, importance_id)
                    VALUES (%s, %s, %s)
                """, (driver_id, template_id, importance_id))
                conn.commit()
                return True
    except Exception as e:
        print(f"Database error: {e}")
        return False


def mark_notifications_as_read(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE notifications
                SET is_read = TRUE
                WHERE id_driver = %s AND is_read = FALSE
            """, (driver_id,))
            conn.commit()


def clear_all_notifications():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM notifications")
            conn.commit()
            print("[Очистка] Все уведомления удалены.")
