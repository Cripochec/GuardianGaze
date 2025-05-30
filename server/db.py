import psycopg2
from datetime import datetime, timedelta
from psycopg2 import sql

# Безопасное подключение к БД
def get_connection():
    return psycopg2.connect(
        host="2.59.43.200",
        database="default_db",
        user="gen_user",
        password="D}NoG1rR,q*xe\\"
    )

# ------------------ СОЗДАНИЕ ТАБЛИЦ ------------------

def create_database():
    table_sql = """
    CREATE TABLE IF NOT EXISTS admins (
        id SERIAL PRIMARY KEY,
        login VARCHAR(100) UNIQUE NOT NULL,
        password VARCHAR(100) NOT NULL
    );

    CREATE TABLE IF NOT EXISTS drivers (
        id SERIAL PRIMARY KEY,
        login VARCHAR(100) UNIQUE NOT NULL,
        password VARCHAR(100) NOT NULL,
        first_name VARCHAR(100),
        last_name VARCHAR(100),
        phone VARCHAR(20),
        email VARCHAR(100),
        age INTEGER,
        truck VARCHAR(100)
    );
    
    CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    importance VARCHAR(10) CHECK (importance IN ('высокая', 'средняя', 'низкая')),
    id_driver INTEGER REFERENCES drivers(id),
    is_read BOOLEAN DEFAULT FALSE
    );

    CREATE TABLE IF NOT EXISTS admin_driver_relation (
        id SERIAL PRIMARY KEY,
        id_admin INTEGER REFERENCES admins(id),
        id_driver INTEGER REFERENCES drivers(id),
        UNIQUE (id_admin, id_driver)
    );
    """

    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(table_sql)
            conn.commit()

        # Создание главного админа, если не существует
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM admins WHERE login = %s", ('admin',))
            if cur.fetchone() is None:
                add_admin('admin', 'admin')
                print("Аккаунт главного админа успешно создан")
            else:
                print("Аккаунт главного админа уже существует")

    print("Таблицы успешно созданы.")

# ------------------ АДМИНЫ ------------------

def add_admin(login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("INSERT INTO admins (login, password) VALUES (%s, %s)", (login, password))
            conn.commit()

def update_admin(admin_id, login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE admins SET login = %s, password = %s WHERE id = %s
            """, (login, password, admin_id))
            conn.commit()

def delete_admin_by_id(admin_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM admins WHERE id = %s", (admin_id,))
            conn.commit()

def get_all_admins():
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM admins")
            return cur.fetchall()

# ------------------ ВОДИТЕЛИ ------------------

def add_driver(login, password, first_name=None, last_name=None, phone=None, email=None, age=None, truck=None):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO drivers (login, password, first_name, last_name, phone, email, age, truck)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                RETURNING id
            """, (login, password, first_name, last_name, phone, email, age, truck))
            driver_id = cur.fetchone()[0]
            conn.commit()
            return driver_id

def delete_driver_db(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("DELETE FROM drivers WHERE id = %s", (driver_id,))
            conn.commit()

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

def get_drivers_by_admin(admin_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT d.*
                FROM drivers d
                INNER JOIN admin_driver_relation adr ON d.id = adr.id_driver
                WHERE adr.id_admin = %s
            """, (admin_id,))
            return cur.fetchall()

# ------------------ СВЯЗЬ АДМИН-ВОДИТЕЛЬ ------------------

def add_admin_driver_relation(id_admin, id_driver):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO admin_driver_relation (id_admin, id_driver)
                VALUES (%s, %s)
                ON CONFLICT (id_admin, id_driver) DO NOTHING
            """, (id_admin, id_driver))
            conn.commit()

def delete_admin_driver_relation(id_admin, id_driver):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                DELETE FROM admin_driver_relation
                WHERE id_admin = %s AND id_driver = %s
            """, (id_admin, id_driver))
            conn.commit()

def update_admin_driver_relation(relation_id, id_admin=None, id_driver=None):
    updates = []
    values = []

    if id_admin is not None:
        updates.append("id_admin = %s")
        values.append(id_admin)
    if id_driver is not None:
        updates.append("id_driver = %s")
        values.append(id_driver)

    if not updates:
        return

    values.append(relation_id)
    with get_connection() as conn:
        with conn.cursor() as cur:
            query = f"UPDATE admin_driver_relation SET {', '.join(updates)} WHERE id = %s"
            cur.execute(query, values)
            conn.commit()





# ------------------ УВЕДОМЛЕНИЯ ------------------

def get_unread_notifications(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT message, time, importance
                FROM notifications
                WHERE id_driver = %s AND is_read = FALSE
                ORDER BY time DESC
            """, (driver_id,))
            return cur.fetchall()

def add_notification(message, importance, driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                INSERT INTO notifications (message, importance, id_driver)
                VALUES (%s, %s, %s)
            """, (message, importance, driver_id))
            conn.commit()

def mark_notifications_as_read(driver_id):
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                UPDATE notifications
                SET is_read = TRUE
                WHERE id_driver = %s
            """, (driver_id,))
            conn.commit()
