import os
import sys
import logging
from logging.handlers import RotatingFileHandler
import time
import numpy as np
from datetime import datetime
from flask import Flask, jsonify, request, g
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flask_bcrypt import Bcrypt
from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity, JWTManager
# from flask_socketio import SocketIO, emit # <-- DIHAPUS
from tensorflow import keras
from keras import layers
from tensorflow.keras.saving import register_keras_serializable
import tensorflow as tf
from dotenv import load_dotenv
from scipy.signal import find_peaks

# =============================================================================
# 1. KONFIGURASI APLIKASI & SEMUA LIBRARY
# =============================================================================
load_dotenv()
app = Flask(__name__)
app.config["JWT_SECRET_KEY"] = os.getenv("JWT_SECRET_KEY", "kunci-rahasia-super-aman-ganti-ini-di-vps")
basedir = os.path.abspath(os.path.dirname(__file__))

# Setup Database
db_dir = os.path.join(basedir, 'data')
if not os.path.exists(db_dir):
    os.makedirs(db_dir)
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL', 'sqlite:///' + os.path.join(db_dir, 'ecg_data.db'))
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Inisialisasi Library
db = SQLAlchemy(app)
migrate = Migrate(app, db)
bcrypt = Bcrypt(app)
jwt = JWTManager(app)
CORS(app, resources={r"/api/*": {"origins": "*"}}) # <-- Nggak perlu /socket.io/ lagi
# socketio = SocketIO(app, cors_allowed_origins="*") # <-- DIHAPUS

# Konstanta
SAMPLING_RATE = 360

# =============================================================================
# 2. SETUP LOGGING (Sama)
# =============================================================================
log_dir = os.path.join(basedir, 'logs')
if not os.path.exists(log_dir): os.makedirs(log_dir)
file_handler = RotatingFileHandler(os.path.join(log_dir, 'app.log'), maxBytes=10240, backupCount=10, encoding='utf-8')
file_handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s: %(message)s [in %(pathname)s:%(lineno)d]'))
file_handler.setLevel(logging.INFO)
app.logger.addHandler(file_handler)
app.logger.setLevel(logging.INFO)
app.logger.info('Aplikasi ECG startup (Mode Ramping, No SocketIO)')

# =============================================================================
# 3. DEFINISI CUSTOM ATTENTION LAYER (Sama)
# =============================================================================
@tf.keras.saving.register_keras_serializable()
class Attention(tf.keras.layers.Layer):
    # ... (Kode class Attention tetap sama, nggak berubah) ...
    def __init__(self, **kwargs): super(Attention, self).__init__(**kwargs); self.W = None; self.b = None
    def build(self, input_shape):
        if not isinstance(input_shape, tf.TensorShape): input_shape = tf.TensorShape(input_shape)
        last_dim = input_shape[-1]; timesteps = input_shape[1]
        self.W = self.add_weight(name="att_weight", shape=(last_dim, 1), initializer="normal", trainable=True)
        self.b = self.add_weight(name="att_bias", shape=(timesteps, 1), initializer="zeros", trainable=True)
        super(Attention, self).build(input_shape)
        app.logger.info("--- Attention build completed. ---")
    def call(self, x):
        if self.W is None or self.b is None: raise ValueError("Attention weights W or b not initialized.")
        et = tf.squeeze(tf.nn.tanh(tf.matmul(x, self.W) + self.b), axis=-1); at = tf.nn.softmax(et)
        at = tf.expand_dims(at, axis=-1); output = x * at
        return tf.reduce_sum(output, axis=1)
    def compute_output_shape(self, input_shape): return tf.TensorShape((input_shape[0], input_shape[-1]))
    def get_config(self): return super(Attention, self).get_config()

# =============================================================================
# 4. LOAD MODEL & FUNGSI (Heart Rate) (Sama)
# =============================================================================
classification_model = None
MODEL_FILENAME = 'beat_classifier_model_SMOTE.keras'
MODEL_PATH = os.getenv('MODEL_PATH', os.path.join(basedir, f'model/{MODEL_FILENAME}'))

def load_classification_model():
    # ... (Fungsi ini sama, tidak berubah) ...
    global classification_model; app.logger.info("="*50); app.logger.info(f"Mencoba memuat model dari: {MODEL_PATH}")
    try:
        if not os.path.exists(MODEL_PATH): raise FileNotFoundError(f"File model tidak ditemukan di '{MODEL_PATH}'")
        classification_model = keras.models.load_model(MODEL_PATH, custom_objects={'Attention': Attention})
        app.logger.info(f"✅ Model Klasifikasi ('{MODEL_FILENAME}') berhasil dimuat.")
    except Exception as e: app.logger.critical(f"❌ FATAL ERROR: Gagal memuat model. Error: {e}", exc_info=True);
    app.logger.info("="*50)

def preprocess_input(data: list, target_length: int = 1024):
    # ... (Fungsi ini sama, tidak berubah) ...
    arr = np.array(data, dtype=np.float32);
    if len(arr) < target_length: padding = np.zeros(target_length - len(arr)); arr = np.concatenate([arr, padding])
    elif len(arr) > target_length: arr = arr[:target_length]
    mean = np.mean(arr); std = np.std(arr);
    if std == 0: std = 1
    normalized_arr = (arr - mean) / std
    return normalized_arr.reshape(1, target_length, 1), normalized_arr

def calculate_heart_rate(signal_1d_normalized):
    # ... (Fungsi ini sama, tidak berubah) ...
    try:
        peaks, _ = find_peaks(signal_1d_normalized, prominence=0.4, distance=0.3 * SAMPLING_RATE)
        if len(peaks) < 2: return None 
        rr_intervals_samples = np.diff(peaks); avg_rr_samples = np.mean(rr_intervals_samples)
        bpm = (SAMPLING_RATE * 60) / avg_rr_samples
        app.logger.info(f"HR Calc: {len(peaks)} peaks. BPM: {bpm:.2f}")
        return round(bpm, 2)
    except Exception as e: app.logger.error(f"HR Calc Error: {e}"); return None

# =============================================================================
# 5. DEFINISI MODEL DATABASE (Sama)
# =============================================================================
monitoring_relationship = db.Table('monitoring_relationship',
    # ... (Definisi tabel monitoring_relationship tetap sama) ...
    db.Column('id', db.Integer, primary_key=True),
    db.Column('monitor_id', db.Integer, db.ForeignKey('user.id'), nullable=False),
    db.Column('patient_id', db.Integer, db.ForeignKey('user.id'), nullable=False),
    db.UniqueConstraint('monitor_id', 'patient_id', name='_monitor_patient_uc')
)
class User(db.Model):
    # ... (Definisi class User tetap sama) ...
    id = db.Column(db.Integer, primary_key=True); email = db.Column(db.String(120), unique=True, nullable=False)
    name = db.Column(db.String(100), nullable=True); password_hash = db.Column(db.String(128), nullable=False)
    role = db.Column(db.String(20), nullable=False, default='pasien'); devices = db.relationship('Device', backref='owner', lazy=True)
    monitoring = db.relationship('User', secondary=monitoring_relationship, primaryjoin=(monitoring_relationship.c.monitor_id == id), secondaryjoin=(monitoring_relationship.c.patient_id == id), backref=db.backref('monitored_by', lazy='dynamic'), lazy='dynamic')

class Device(db.Model):
    # ... (Definisi class Device tetap sama) ...
    id = db.Column(db.Integer, primary_key=True); mac_address = db.Column(db.String(17), unique=True, nullable=False)
    device_id_str = db.Column(db.String(80), unique=True, nullable=False); device_name = db.Column(db.String(80), nullable=True, default="My ESP32 Device")
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=True); created_at = db.Column(db.DateTime, default=datetime.utcnow)
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

class ECGReading(db.Model):
    # ... (Definisi class ECGReading tetap sama) ...
    id = db.Column(db.Integer, primary_key=True); timestamp = db.Column(db.DateTime, nullable=False)
    prediction = db.Column(db.String(50), nullable=False); heart_rate = db.Column(db.Float, nullable=True)
    probabilities = db.Column(db.JSON, nullable=True); ecg_beat_data = db.Column(db.JSON, nullable=True)
    processed_ecg_data = db.Column(db.JSON, nullable=True); device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

# =============================================================================
# 6. API SERVICES (Hapus 'socketio.emit')
# =============================================================================
@app.route("/api/v1")
def index():
    # ... (Sama) ...
    return jsonify({"message": "Server Analisis ECG v1 berjalan!", "model_loaded": (classification_model is not None)})

@jwt.user_lookup_loader
def user_lookup_callback(_jwt_header, jwt_data):
    # ... (Sama) ...
    identity = jwt_data["sub"]; user = db.session.get(User, identity); g.user = user; return user

@app.route('/api/v1/auth/register', methods=['POST'])
def register_user():
    # ... (Fungsi ini sama, tidak berubah) ...
    data = request.get_json(); email = data.get('email'); password = data.get('password'); name = data.get('name', email); role = data.get('role', 'pasien')
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400
    if User.query.filter_by(email=email).first(): return jsonify({"error": "Email sudah terdaftar"}), 409
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8'); new_user = User(email=email, name=name, password_hash=hashed_password, role=role)
    db.session.add(new_user); db.session.commit(); app.logger.info(f"User baru terdaftar: {email} (Role: {role})")
    return jsonify({"message": f"User {email} berhasil dibuat"}), 201

@app.route('/api/v1/auth/login', methods=['POST'])
def login_user():
    # ... (Fungsi ini sama, tidak berubah) ...
    data = request.get_json(); email = data.get('email'); password = data.get('password')
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400
    user = User.query.filter_by(email=email).first()
    if user and bcrypt.check_password_hash(user.password_hash, password):
        access_token = create_access_token(identity=user.id)
        app.logger.info(f"User login berhasil: {email}"); return jsonify(access_token=access_token)
    app.logger.warning(f"Login gagal untuk: {email}"); return jsonify({"error": "Email atau password salah"}), 401

@app.route('/api/v1/register-device', methods=['POST'])
def register_device():
    # ... (Fungsi ini sama, tidak berubah) ...
    data = request.get_json(); mac = data.get('mac_address')
    if not mac: return jsonify({"error": "'mac_address' dibutuhkan"}), 400
    device = Device.query.filter_by(mac_address=mac).first()
    if device:
        app.logger.info(f"Device {mac} sudah terdaftar. Mengembalikan ID: {device.device_id_str}"); return jsonify({"device_id": device.device_id_str}), 200
    else:
        count = Device.query.count(); new_device_id = f"ECG_DEV_{count + 1:03d}"
        new_device = Device(mac_address=mac, device_id_str=new_device_id, user_id=None)
        db.session.add(new_device); db.session.commit()
        app.logger.info(f"Device baru {mac} didaftarkan dengan ID {new_device_id} (tanpa pemilik)."); return jsonify({"device_id": new_device_id}), 201

@app.route('/api/v1/claim-device', methods=['POST'])
@jwt_required()
def claim_device():
    # ... (Fungsi ini sama, tidak berubah) ...
    current_user_id = get_jwt_identity(); data = request.get_json(); mac = data.get('mac_address'); device_id_str = data.get('device_id_str')
    if not mac or not device_id_str: return jsonify({"error": "'mac_address' dan 'device_id_str' dibutuhkan"}), 400
    device = Device.query.filter_by(mac_address=mac, device_id_str=device_id_str).first()
    if not device: return jsonify({"error": "Device tidak ditemukan"}), 404
    if device.user_id is not None:
        if device.user_id == current_user_id: return jsonify({"message": "Device ini sudah menjadi milik Anda."}), 200
        else: return jsonify({"error": "Device ini sudah dimiliki oleh akun lain."}), 409
    device.user_id = current_user_id; db.session.commit()
    app.logger.info(f"User {current_user_id} berhasil mengklaim device {device_id_str}"); return jsonify({"message": f"Device {device_id_str} berhasil diklaim."}), 200
    
@app.route('/api/v1/unclaim-device', methods=['POST'])
@jwt_required()
def unclaim_device():
    # ... (Fungsi ini sama, tidak berubah) ...
    current_user_id = get_jwt_identity(); data = request.get_json(); device_id_str = data.get('device_id_str')
    if not device_id_str: return jsonify({"error": "'device_id_str' dibutuhkan"}), 400
    device = Device.query.filter_by(device_id_str=device_id_str, user_id=current_user_id).first()
    if not device: return jsonify({"error": "Device tidak ditemukan atau bukan milik Anda."}), 404
    device.user_id = None; db.session.commit()
    app.logger.info(f"User {current_user_id} melepaskan kepemilikan device {device_id_str}."); return jsonify({"message": f"Kepemilikan device {device_id_str} berhasil dilepaskan."}), 200

# --- API ANALISIS (Hapus 'socketio.emit') ---
@app.route('/api/v1/analyze-ecg', methods=['POST'])
def analyze_ecg():
    # ... (Validasi & cek device sama) ...
    data = request.get_json(); device_id_str = data['device_id']; ecg_beat = data['ecg_beat_data']
    ecg_afib = data.get('ecg_afib_data'); timestamp_str = data.get('timestamp')
    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device: return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404
    if classification_model is None: return jsonify({"error": "Model inferensi sedang tidak tersedia."}), 503

    try:
        # ... (Proses Model & HR Calc sama) ...
        processed_input_3d, processed_input_1d = preprocess_input(ecg_beat)
        heart_rate_bpm = calculate_heart_rate(processed_input_1d)
        prediction_probabilities = classification_model.predict(processed_input_3d)[0]
        predicted_index = np.argmax(prediction_probabilities); arrhythmia_classes = ['Normal', 'Other', 'PVC']
        prediction_result = arrhythmia_classes[predicted_index]

        try: parsed_timestamp = datetime.fromisoformat(timestamp_str.replace("+07:00", ""))
        except: parsed_timestamp = datetime.utcnow()

        new_reading = ECGReading(
            timestamp=parsed_timestamp, prediction=prediction_result, heart_rate=heart_rate_bpm,
            probabilities=prediction_probabilities.tolist(), ecg_beat_data=ecg_beat,
            processed_ecg_data=processed_input_1d.tolist(), device_id=device.id
        )
        db.session.add(new_reading); db.session.commit()

        # --- TEMBAKAN REAL-TIME (SOCKET.IO) ---
        # data_to_emit = { ... }
        # socketio.emit('new_ecg_data', data_to_emit) # <-- BARIS INI DIHAPUS
        
        app.logger.info(f"Data {device_id_str} disimpan. Prediksi: {prediction_result}")
        
        return jsonify({"status": "success", "prediction": prediction_result, "heartRate": heart_rate_bpm})

    except Exception as e:
        db.session.rollback(); app.logger.error(f"ERROR saat memproses data: {e}", exc_info=True)
        return jsonify({"error": f"Kesalahan internal: {str(e)}"}), 500

# --- API KLIEN (Mobile App) SESUAI JSON SPEK (Sama) ---

@app.route('/api/v1/profile', methods=['GET'])
@jwt_required()
def get_profile():
    # ... (Fungsi ini sama, tidak berubah) ...
    user = g.user; correlatives_list = []
    for patient in user.monitoring: correlatives_list.append({'id': patient.id, 'name': patient.name})
    return jsonify({"user": {"id": user.id, "name": user.name, "email": user.email}, "correlatives": correlatives_list})

@app.route('/api/v1/dashboard', methods=['GET'])
@jwt_required()
def get_dashboard():
    # ... (Fungsi ini sama, tidak berubah) ...
    user = g.user; dashboard_data = []
    my_devices = user.devices
    if my_devices:
        latest_reading_self = ECGReading.query.filter(ECGReading.device_id.in_([d.id for d in my_devices])).order_by(ECGReading.timestamp.desc()).first()
        if latest_reading_self:
            dashboard_data.append({"type": "self", "name": my_devices[0].device_name, "heartRate": latest_reading_self.heart_rate, "classification": latest_reading_self.prediction, "timestamp": latest_reading_self.timestamp.isoformat()})
    for patient in user.monitoring:
        latest_reading_correlative = None
        if patient.devices:
            latest_reading_correlative = ECGReading.query.filter(ECGReading.device_id.in_([d.id for d in patient.devices])).order_by(ECGReading.timestamp.desc()).first()
        if latest_reading_correlative:
            dashboard_data.append({"type": "correlative", "name": patient.name, "heartRate": latest_reading_correlative.heart_rate, "classification": latest_reading_correlative.prediction, "timestamp": latest_reading_correlative.timestamp.isoformat()})
    return jsonify({"data": dashboard_data})

@app.route('/api/v1/history', methods=['GET'])
@jwt_required()
def get_history():
    # ... (Fungsi ini sama, tidak berubah) ...
    user = g.user; user_id_to_view = request.args.get('userId', user.id, type=int); filter_class = request.args.get('filterClass', None, type=str)
    sort_order = request.args.get('sort', 'desc', type=str); page = request.args.get('page', 1, type=int); per_page = 20
    is_self = (user.id == user_id_to_view); is_allowed_correlative = user.monitoring.filter_by(id=user_id_to_view).first() is not None
    if not is_self and not is_allowed_correlative: return jsonify({"error": "Anda tidak punya izin untuk melihat data ini."}), 403
    target_user = User.query.get_or_404(user_id_to_view); target_device_ids = [d.id for d in target_user.devices]
    if not target_device_ids: return jsonify({"data": [], "pagination": {"currentPage": 1, "totalPages": 0, "totalItems": 0}})
    query = ECGReading.query.filter(ECGReading.device_id.in_(target_device_ids))
    if filter_class: query = query.filter(ECGReading.prediction == filter_class)
    if sort_order == 'asc': query = query.order_by(ECGReading.timestamp.asc())
    else: query = query.order_by(ECGReading.timestamp.desc())
    pagination = query.paginate(page=page, per_page=per_page, error_out=False); readings = pagination.items; data_list = []
    for r in readings: data_list.append({"id": r.id, "timestamp": r.timestamp.isoformat(), "classification": r.prediction, "heartRate": r.heart_rate})
    return jsonify({"data": data_list, "pagination": {"currentPage": pagination.page, "totalPages": pagination.pages, "totalItems": pagination.total}})

@app.route('/api/v1/correlatives/add', methods=['POST'])
@jwt_required()
def add_correlative():
    # ... (Fungsi ini sama, tidak berubah) ...
    user = g.user; data = request.get_json(); scanned_code = data.get('scannedCode')
    if not scanned_code: return jsonify({"error": "Invite code dibutuhkan"}), 400
    try:
        patient_id = int(scanned_code); patient_to_add = User.query.get(patient_id)
        if not patient_to_add: return jsonify({"error": "Kode invite tidak valid."}), 404
        if patient_to_add.role != 'pasien': return jsonify({"error": "Anda hanya bisa menambahkan 'pasien'."}), 400
        user.monitoring.append(patient_to_add); db.session.commit()
        return jsonify({"status": "success", "message": f"Kerabat '{patient_to_add.name}' berhasil ditambahkan."})
    except Exception as e:
        db.session.rollback(); app.logger.error(f"Gagal add correlative: {e}", exc_info=True)
        return jsonify({"error": "Kode invite tidak valid."}), 400

# =============================================================================
# 7. PERINTAH KHUSUS UNTUK MANAJEMEN APLIKASI (CLI) (Sama)
# =============================================================================
@app.cli.command("init-db")
def init_db_command():
    # ... (Fungsi ini sama, tidak berubah) ...
    with app.app_context(): db.create_all()
    print(f"Database berhasil diinisialisasi di {app.config['SQLALCHEMY_DATABASE_URI']}")

# =============================================================================
# 8. INISIASI MODEL SAAT STARTUP (Sama)
# =============================================================================
with app.app_context():
    load_classification_model()

# =============================================================================
# 9. BLOK EKSEKUSI (Balik ke `app.run` standar)
# =============================================================================
if __name__ == '__main__':
    with app.app_context():
        db.create_all() 
    app.logger.info("Menjalankan server development lokal...")
    # --- DIUBAH: Balik ke app.run standar ---
    app.run(host='0.0.0.0', port=8080, debug=True)