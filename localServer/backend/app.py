import os
import sys
import time
import numpy as np
from datetime import datetime
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flask_bcrypt import Bcrypt
from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity, JWTManager
from tensorflow import keras

# =============================================================================
# 1. KONFIGURASI APLIKASI, DATABASE, DAN OTENTIKASI
# =============================================================================

app = Flask(__name__)
# Kunci rahasia untuk JWT, di produksi ambil dari environment variable
app.config["JWT_SECRET_KEY"] = os.getenv("JWT_SECRET_KEY", "kunci-super-rahasia-jangan-disebar")
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL', 'sqlite:///ecg_data.db')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
migrate = Migrate(app, db)
bcrypt = Bcrypt(app)
jwt = JWTManager(app)
CORS(app)

# --- Variabel & Path Model ---
denoiser_model = None
classification_model = None
DENOISER_MODEL_PATH = 'model/denoise_model_.h5'
CLASSIFICATION_MODEL_PATH = 'model/classification_model.h5'

def load_all_models():
    """
    Fungsi untuk me-load semua model ke memori saat server start.
    """
    global denoiser_model, classification_model
    print("="*50)
    print("Memuat model ke memori...")
    try:
        if os.path.exists(DENOISER_MODEL_PATH):
            denoiser_model = keras.models.load_model(DENOISER_MODEL_PATH)
            print(f"✅ Model Denoising ('{DENOISER_MODEL_PATH}') berhasil dimuat.")
        else:
            raise FileNotFoundError(f"File model denoising tidak ditemukan di '{DENOISER_MODEL_PATH}'")

        if os.path.exists(CLASSIFICATION_MODEL_PATH):
            classification_model = keras.models.load_model(CLASSIFICATION_MODEL_PATH)
            print(f"✅ Model Klasifikasi ('{CLASSIFICATION_MODEL_PATH}') berhasil dimuat.")
        else:
            raise FileNotFoundError(f"File model klasifikasi tidak ditemukan di '{CLASSIFICATION_MODEL_PATH}'")
    except Exception as e:
        print(f"❌ FATAL ERROR: Gagal memuat model. Error: {e}")
        sys.exit(1)
    print("="*50)

# =============================================================================
# 2. DEFINISI MODEL DATABASE (Lengkap dengan User & Relasi)
# =============================================================================

# Tabel penghubung untuk relasi kerabat-pasien (many-to-many)
monitoring_relationship = db.Table('monitoring_relationship',
    db.Column('monitor_id', db.Integer, db.ForeignKey('user.id'), primary_key=True),
    db.Column('patient_id', db.Integer, db.ForeignKey('user.id'), primary_key=True)
)

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    role = db.Column(db.String(20), nullable=False, default='pasien') # 'pasien' atau 'kerabat'
    devices = db.relationship('Device', backref='owner', lazy=True)
    
    # Relasi untuk kerabat: siapa saja pasien yang dia pantau
    monitoring = db.relationship('User', secondary=monitoring_relationship,
                                 primaryjoin=(monitoring_relationship.c.monitor_id == id),
                                 secondaryjoin=(monitoring_relationship.c.patient_id == id),
                                 backref=db.backref('monitored_by', lazy='dynamic'), lazy='dynamic')

class Device(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_id_str = db.Column(db.String(80), unique=True, nullable=False)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

class ECGReading(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    timestamp = db.Column(db.DateTime, nullable=False, default=datetime.utcnow)
    prediction = db.Column(db.String(50), nullable=False)
    probabilities = db.Column(db.JSON, nullable=True)
    denoised_data = db.Column(db.JSON, nullable=False)
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

def preprocess_input(data: list, target_length: int):
    """
    Menyiapkan data mentah dari request agar siap digunakan oleh model.
    """
    arr = np.array(data, dtype=np.float32)
    if len(arr) < target_length:
        padding = np.zeros(target_length - len(arr))
        arr = np.concatenate([arr, padding])
    elif len(arr) > target_length:
        arr = arr[:target_length]
    mean = np.mean(arr)
    std = np.std(arr)
    if std == 0: std = 1
    normalized_arr = (arr - mean) / std
    return normalized_arr.reshape(1, target_length, 1), mean, std

# =============================================================================
# 4. API SERVICES
# =============================================================================

@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!", "models_loaded": True})

# --- API UNTUK AUTENTIKASI ---
@app.route('/api/auth/register', methods=['POST'])
def register_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    role = data.get('role', 'pasien')

    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400
    if User.query.filter_by(email=email).first(): return jsonify({"error": "Email sudah terdaftar"}), 409

    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    new_user = User(email=email, password_hash=hashed_password, role=role)
    db.session.add(new_user)
    db.session.commit()
    return jsonify({"message": f"User {email} berhasil dibuat"}), 201

@app.route('/api/auth/login', methods=['POST'])
def login_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400

    user = User.query.filter_by(email=email).first()
    if user and bcrypt.check_password_hash(user.password_hash, password):
        access_token = create_access_token(identity=user.id)
        return jsonify(access_token=access_token, role=user.role, email=user.email)
    return jsonify({"error": "Email atau password salah"}), 401

# --- API UNTUK MANAJEMEN PERANGKAT ---
@app.route('/api/register-device', methods=['POST'])
@jwt_required()
def register_device():
    current_user_id = get_jwt_identity()
    data = request.get_json()
    device_id_str = data.get('device_id_str')
    if not device_id_str: return jsonify({"error": "'device_id_str' dibutuhkan"}), 400

    existing_device = Device.query.filter_by(device_id_str=device_id_str).first()
    if existing_device:
        existing_device.user_id = current_user_id
        db.session.commit()
        return jsonify({"message": f"Device {device_id_str} berhasil di-update ke user Anda."}), 200
    else:
        new_device = Device(device_id_str=device_id_str, user_id=current_user_id)
        db.session.add(new_device)
        db.session.commit()
        return jsonify({"message": f"Device {device_id_str} berhasil didaftarkan."}), 201

# --- API UTAMA UNTUK ANALISIS ---
@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    data = request.get_json()
    if not data or 'ecg_buffer' not in data or 'device_id' not in data:
        return jsonify({"error": "Request body harus berisi 'ecg_buffer' dan 'device_id'"}), 400
    
    raw_ecg = data['ecg_buffer']
    device_id_str = data['device_id']
    timestamp_str = data.get('timestamp', datetime.utcnow().isoformat())

    try:
        denoiser_input, mean, std = preprocess_input(raw_ecg, 1024)
        denoised_signal_normalized = denoiser_model.predict(denoiser_input).flatten()
        denoised_signal = (denoised_signal_normalized * std) + mean
        
        classifier_input, _, _ = preprocess_input(denoised_signal.tolist(), 1000)
        prediction_probabilities = classification_model.predict(classifier_input)[0]
        
        predicted_index = np.argmax(prediction_probabilities)
        arrhythmia_classes = ['Normal', 'AF', 'PVC', 'Other']
        prediction_result = arrhythmia_classes[predicted_index]
        
        device = Device.query.filter_by(device_id_str=device_id_str).first()
        if not device:
            return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404
        
        new_reading = ECGReading(
            timestamp=datetime.fromisoformat(timestamp_str.replace("Z", "+00:00")),
            prediction=prediction_result,
            probabilities=prediction_probabilities.tolist(),
            denoised_data=denoised_signal.tolist(),
            device_id=device.id
        )
        db.session.add(new_reading)
        db.session.commit()
        
        return jsonify({"status": "success", "prediction": prediction_result})
    except Exception as e:
        app.logger.error(f"Error saat prediksi: {e}", exc_info=True)
        return jsonify({"error": "Terjadi kesalahan internal saat memproses data ECG."}), 500

# --- API UNTUK APLIKASI KLIEN (PASIEN/KERABAT/ADMIN) ---
@app.route('/api/my-devices', methods=['GET'])
@jwt_required()
def get_my_devices():
    current_user_id = get_jwt_identity()
    devices = Device.query.filter_by(user_id=current_user_id).all()
    return jsonify([{'id': d.id, 'device_id_str': d.device_id_str} for d in devices])

@app.route('/api/my-readings', methods=['GET'])
@jwt_required()
def get_my_readings():
    current_user_id = get_jwt_identity()
    user = User.query.get(current_user_id)
    # Ambil semua data dari semua device milik user ini
    all_readings = []
    for device in user.devices:
        readings = ECGReading.query.filter_by(device_id=device.id).order_by(ECGReading.timestamp.desc()).limit(50).all()
        for r in readings:
            all_readings.append({'id': r.id, 'timestamp': r.timestamp.isoformat(), 'prediction': r.prediction, 'device': device.device_id_str})
    return jsonify(all_readings)


if __name__ == '__main__':
    with app.app_context():
        # Buat tabel database jika belum ada (berguna untuk development)
        db.create_all()
        # Load model saat start
        load_all_models()
    app.run(debug=True, port=5000)

