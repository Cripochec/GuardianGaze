import random
import string
from settings import SMSRU_API_ID
import requests


def generate_credentials():
    login = ''.join(random.choices(string.ascii_letters + string.digits, k=6))
    password = ''.join(random.choices(string.ascii_letters + string.digits, k=8))
    return login, password

