import os
import sys
import logging
from logging.handlers import RotatingFileHandler
import time
import numpy as np
from datetime import datetime, timedelta
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flask_bcrypt import Bcrypt
from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity, JWTManager
from scipy.signal import find_peaks
# from scipy.stats import iqr //buat afib yang mana belum terpakai
from dotenv import load_dotenv
import tensorflow as tf

load_dotenv()

app = Flask(__name__)
app.config["JWT_SECRET_KEY"] = os.getenv("JWT_SECRET_KEY", "kunci-rahasia-super-aman-ganti-ini-di-vps")
basedir = os.path.abspath(os.path.dirname(__file__))

db_dir = os.path.join(basedir, 'data')
if not os.path.exists(db_dir):
    os.makedirs(db_dir)

db_path = os.getenv('DATABASE_PATH', os.path.join(basedir, 'data/ecg_data.db'))
app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
migrate = Migrate(app, db)
bcrypt = Bcrypt(app)
jwt = JWTManager(app)
CORS(app)

SAMPLING_RATE = 360 

log_dir = os.path.join(basedir, 'logs')
if not os.path.exists(log_dir):
    os.makedirs(log_dir)
file_handler = RotatingFileHandler(os.path.join(log_dir, 'app.log'), maxBytes=10240, backupCount=10, encoding='utf-8')
file_handler.setFormatter(logging.Formatter(
    '%(asctime)s %(levelname)s: %(message)s [in %(pathname)s:%(lineno)d]'
))
file_handler.setLevel(logging.INFO)
app.logger.addHandler(file_handler)
app.logger.setLevel(logging.INFO)
app.logger.info('Aplikasi ECG startup (TFLite)')

classification_interpreter = None
input_details = None
output_details = None
MODEL_FILENAME = 'beat_classifier_model_SMOTE.tflite' # <-- GANTI KE .tflite
MODEL_PATH = os.getenv('MODEL_PATH', os.path.join(basedir, f'model/{MODEL_FILENAME}'))
BEAT_LABELS = ['Normal', 'Other', 'PVC'] # [FIX] Sesuaikan urutan ini SAMA PERSIS kayak pas training

def load_all_models():
    global classification_interpreter, input_details, output_details, afib_model
    
    # --- Load Model #1 (TFLite) ---
    app.logger.info("="*50)
    app.logger.info(f"Mencoba memuat model TFLite dari: {MODEL_PATH}")
    try:
        if not os.path.exists(MODEL_PATH):
            app.logger.error(f"File model TFLite tidak ditemukan di path: {MODEL_PATH}")
            return

        # Pake Interpreter dari library TensorFlow LENGKAP (karena kita pake SELECT_TF_OPS)
        classification_interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
        classification_interpreter.allocate_tensors() # Wajib!

        input_details = classification_interpreter.get_input_details()
        output_details = classification_interpreter.get_output_details()
        
        app.logger.info(f"✅ Model TFLite ('{MODEL_FILENAME}') berhasil dimuat.")
        app.logger.info(f"  -> Input Shape: {input_details[0]['shape']}")
        app.logger.info(f"  -> Output Shape: {output_details[0]['shape']}")
        
    except Exception as e:
        app.logger.critical(f"❌ FATAL ERROR: Gagal memuat model TFLite. Error: {e}", exc_info=True)

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    role = db.Column(db.String(20), nullable=False, default='pasien')
    devices = db.relationship('Device', backref='owner', lazy=True)
    monitoring = db.relationship('MonitoringRelationship', foreign_keys='MonitoringRelationship.monitor_id', backref='monitor', lazy='dynamic')
    monitored_by = db.relationship('MonitoringRelationship', foreign_keys='MonitoringRelationship.patient_id', backref='patient', lazy='dynamic')

class Device(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    mac_address = db.Column(db.String(17), unique=True, nullable=False)
    device_id_str = db.Column(db.String(80), unique=True, nullable=False)
    device_name = db.Column(db.String(100), nullable=True, default="My ECG Device")
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

class ECGReading(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    timestamp = db.Column(db.DateTime, nullable=False)
    prediction = db.Column(db.String(50), nullable=False)
    heart_rate = db.Column(db.Float, nullable=True)
    processed_ecg_data = db.Column(db.JSON, nullable=False) # Simpan data yg diproses
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

class MonitoringRelationship(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    monitor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False) # ID Kerabat
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False) # ID Pasien

def preprocess_input(data: list, target_length: int = 1024):
    arr = np.array(data, dtype=np.float32)
    if len(arr) < target_length:
        padding = np.zeros(target_length - len(arr), dtype=np.float32) # Pastiin float32
        arr = np.concatenate([arr, padding])
    elif len(arr) > target_length:
        arr = arr[:target_length]
    
    mean = np.mean(arr)
    std = np.std(arr)
    if std < 1e-6: std = 1.0 
    
    normalized_arr = (arr - mean) / std
    return normalized_arr.reshape(1, target_length, 1).astype(np.float32)
def calculate_heart_rate(signal_1d_normalized):
    try:
        peaks, _ = find_peaks(signal_1d_normalized, prominence=0.7, distance=0.4 * SAMPLING_RATE)
        
        if len(peaks) < 2: 
            app.logger.info(f"HR Calc: Puncak tidak cukup ({len(peaks)} peaks).")
            return None
        rr_intervals_samples = np.diff(peaks)
        avg_rr_samples = np.mean(rr_intervals_samples)
        bpm = (SAMPLING_RATE * 60) / avg_rr_samples
        if bpm < 40 or bpm > 200:
            app.logger.warning(f"HR Calc: BPM {bpm:.2f} tidak wajar, dibuang.")
            return None
        app.logger.info(f"HR Calc: {len(peaks)} peaks. Avg RR: {avg_rr_samples:.2f} samples. BPM: {bpm:.2f}")
        return round(bpm, 2)
    except Exception as e:
        app.logger.error(f"HR Calc Error: {e}", exc_info=True)
        return None

@app.route("/api/v1")
def index():
    model_1_loaded = (classification_interpreter is not None)
    # model_2_loaded = (afib_model is not None)
    return jsonify({
        "message": "Server Analisis ECG (TFLite) berjalan!", 
        "model_beat_loaded": model_1_loaded,
        # "model_afib_loaded": model_2_loaded
    })
# --- API Otentikasi ---
@app.route('/api/v1/auth/register', methods=['POST'])
def register_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    role = data.get('role', 'pasien') # 'pasien' atau 'kerabat'
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400
    if User.query.filter_by(email=email).first(): return jsonify({"error": "Email sudah terdaftar"}), 409
    
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    new_user = User(email=email, password_hash=hashed_password, role=role)
    db.session.add(new_user)
    db.session.commit()
    app.logger.info(f"User baru terdaftar: {email} (Role: {role})")
    return jsonify({"message": f"User {email} berhasil dibuat"}), 201

@app.route('/api/v1/auth/login', methods=['POST'])
def login_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400

    user = User.query.filter_by(email=email).first()
    if user and bcrypt.check_password_hash(user.password_hash, password):
        access_token = create_access_token(identity=user.id)
        app.logger.info(f"User login berhasil: {email}")
        return jsonify(access_token=access_token, user_id=user.id, role=user.role)
    app.logger.warning(f"Login gagal untuk: {email}")
    return jsonify({"error": "Email atau password salah"}), 401

# --- API Device ---
@app.route('/api/v1/register-device', methods=['POST'])
def register_device():
    """ 
    Endpoint 'kelahiran' device. Dipanggil ESP32.
    Hanya mendaftarkan device ke sistem, tanpa pemilik (user_id = NULL).
    """
    data = request.get_json()
    mac = data.get('mac_address')
    if not mac: return jsonify({"error": "'mac_address' dibutuhkan"}), 400

    device = Device.query.filter_by(mac_address=mac).first()
    if device:
        app.logger.info(f"Device {mac} sudah terdaftar. Mengembalikan ID: {device.device_id_str}")
        return jsonify({"device_id": device.device_id_str}), 200
    else:
        count = Device.query.count()
        new_device_id = f"ECG_DEV_{count + 1:03d}"
        new_device = Device(mac_address=mac, device_id_str=new_device_id, user_id=None)
        db.session.add(new_device)
        db.session.commit()
        app.logger.info(f"Device baru {mac} didaftarkan dengan ID {new_device_id} (tanpa pemilik).")
        return jsonify({"device_id": new_device_id}), 201

@app.route('/api/v1/claim-device', methods=['POST'])
@jwt_required()
def claim_device():
    current_user_id = get_jwt_identity()
    data = request.get_json()
    mac = data.get('mac_address')
    device_id_str = data.get('device_id_str')
    if not mac or not device_id_str: return jsonify({"error": "'mac_address' dan 'device_id_str' dibutuhkan"}), 400

    device = Device.query.filter_by(mac_address=mac, device_id_str=device_id_str).first()
    if not device: return jsonify({"error": "Device tidak ditemukan."}), 404
    
    if device.user_id is not None:
        if device.user_id == current_user_id: return jsonify({"message": "Device ini sudah menjadi milik Anda."}), 200
        else: return jsonify({"error": "Device ini sudah dimiliki oleh akun lain."}), 409
    
    device.user_id = current_user_id
    db.session.commit()
    app.logger.info(f"User {current_user_id} berhasil mengklaim device {device_id_str}")
    return jsonify({"message": f"Device {device_id_str} berhasil diklaim."}), 200

@app.route('/api/v1/unclaim-device', methods=['POST'])
@jwt_required()
def unclaim_device():
    current_user_id = get_jwt_identity()
    data = request.get_json()
    device_id_str = data.get('device_id_str')
    if not device_id_str: return jsonify({"error": "'device_id_str' dibutuhkan"}), 400

    device = Device.query.filter_by(device_id_str=device_id_str, user_id=current_user_id).first()
    if not device: return jsonify({"error": "Device tidak ditemukan atau bukan milik Anda."}), 404
    
    device.user_id = None
    db.session.commit()
    app.logger.info(f"User {current_user_id} melepaskan kepemilikan device {device_id_str}.")
    return jsonify({"message": f"Kepemilikan device {device_id_str} berhasil dilepaskan."}), 200

@app.route('/api/v1/analyze-ecg', methods=['POST'])
def analyze_ecg():
    data = request.get_json()
    if not data or 'ecg_beat_data' not in data or 'device_id' not in data:
        return jsonify({"error": "Request body harus berisi 'ecg_beat_data' dan 'device_id'"}), 400

    device_id_str = data['device_id']
    ecg_beat = data['ecg_beat_data']
    timestamp_str = data.get('timestamp')
    app.logger.info(f"Menerima data dari device: {device_id_str} ({len(ecg_beat)} points).")

    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device: return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404

    if classification_interpreter is None or input_details is None or output_details is None:
        app.logger.error("Model TFLite belum dimuat!")
        return jsonify({"error": "Model inferensi sedang tidak tersedia."}), 503

    beat_prediction_result = "N/A"
    heart_rate = None
    prediction_probabilities = []

    try:
        processed_input = preprocess_input(ecg_beat, target_length=1024)

        app.logger.info(f"Memulai inferensi TFLite untuk {device_id_str}...")
        start_time = time.time()

        classification_interpreter.set_tensor(input_details[0]['index'], processed_input)
        classification_interpreter.invoke()
        prediction_probabilities = classification_interpreter.get_tensor(output_details[0]['index'])[0]
        
        duration = (time.time() - start_time) * 1000
        app.logger.info(f"Inferensi {device_id_str} selesai dalam {duration:.2f} ms.")

        predicted_index = np.argmax(prediction_probabilities)
        beat_prediction_result = BEAT_LABELS[predicted_index]

        heart_rate = calculate_heart_rate(processed_input.flatten()) 

        try:
            # Hapus +07:00 dulu, baru parse
            if "+" in timestamp_str:
                timestamp_str = timestamp_str.split("+")[0]
            parsed_timestamp = datetime.fromisoformat(timestamp_str)
        except (ValueError, TypeError, AttributeError):
            app.logger.warning(f"Timestamp tidak valid dari {device_id_str}: {timestamp_str}. Menggunakan waktu server.")
            parsed_timestamp = datetime.utcnow()
            
        new_reading = ECGReading(
            timestamp=parsed_timestamp,
            prediction=beat_prediction_result,
            heart_rate=heart_rate,
            processed_ecg_data=processed_input.flatten().tolist(), 
            device_id=device.id
        )
        db.session.add(new_reading)
        db.session.commit()

        app.logger.info(f"Data {device_id_str} disimpan. Prediksi: {beat_prediction_result}, HR: {heart_rate}")
        return jsonify({
            "status": "success",
            "prediction": beat_prediction_result,
            "heartRate": heart_rate,
            "probabilities": prediction_probabilities.tolist()
        })

    except Exception as e:
        db.session.rollback()
        app.logger.error(f"ERROR saat memproses data dari {device_id_str}: {e}", exc_info=True)
        return jsonify({"error": f"Kesalahan internal: {str(e)}"}), 500

# --- API Dashboard & History (Sesuai Kerangka JSON) ---
@app.route('/api/v1/profile', methods=['GET'])
@jwt_required()
def get_profile():
    current_user_id = get_jwt_identity()
    user = User.query.get(current_user_id)
    if not user: return jsonify({"error": "User tidak ditemukan"}), 404
    
    # Cari kerabat yang memantau user ini
    monitors = user.monitored_by.all()
    correlatives_list = [
        {"id": m.monitor.id, "email": m.monitor.email, "role": m.monitor.role} for m in monitors
    ]    
    app.logger.info(f"User {user.id} memantau {len(correlatives_list)} orang.")
    
    return jsonify({
        "user": {
            "id": user.id,
            "email": user.email,
            "role": user.role
        },
        "correlatives": correlatives_list
    })

@app.route('/api/v1/dashboard', methods=['GET'])
@jwt_required()
def get_dashboard():
    current_user_id = get_jwt_identity()
    user = User.query.get(current_user_id)
    if not user: return jsonify({"error": "User tidak ditemukan"}), 404

    response_data = []

    # 1. Ambil data diri sendiri
    my_devices = user.devices
    for device in my_devices:
        latest_reading = ECGReading.query.filter_by(device_id=device.id).order_by(ECGReading.timestamp.desc()).first()
        if latest_reading:
            response_data.append({
                "type": "self",
                "user_id": user.id,
                "user_email": user.email,
                "device_name": device.device_name,
                "heartRate": latest_reading.heart_rate,
                "prediction": latest_reading.prediction,
                "timestamp": latest_reading.timestamp.isoformat() + "Z"
            })

    # 2. Ambil data kerabat yang dipantau
    monitoring_list = user.monitoring.all()
    for relationship in monitoring_list:
        patient = relationship.patient
        patient_devices = patient.devices
        for device in patient_devices:
            latest_reading = ECGReading.query.filter_by(device_id=device.id).order_by(ECGReading.timestamp.desc()).first()
            if latest_reading:
                response_data.append({
                    "type": "correlative",
                    "user_id": patient.id,
                    "user_email": patient.email,
                    "device_name": device.device_name,
                    "heartRate": latest_reading.heart_rate,
                    "prediction": latest_reading.prediction,
                    "timestamp": latest_reading.timestamp.isoformat() + "Z"
                })

    return jsonify({"data": response_data})

@app.route('/api/v1/history', methods=['GET'])
@jwt_required()
def get_history():
    current_user_id = get_jwt_identity()
    
    user_id_to_check = request.args.get('userId')
    if not user_id_to_check: return jsonify({"error": "Parameter 'userId' dibutuhkan"}), 400
    
    try: user_id_to_check = int(user_id_to_check)
    except ValueError: return jsonify({"error": "Parameter 'userId' harus berupa angka"}), 400

    user_to_check = User.query.get(user_id_to_check)
    if not user_to_check: return jsonify({"error": "User yang diminta tidak ditemukan"}), 404

    can_view = False
    if current_user_id == user_id_to_check: 
        can_view = True
    else: 
        relationship = MonitoringRelationship.query.filter_by(
            monitor_id=current_user_id, 
            patient_id=user_id_to_check
        ).first()
        if relationship: can_view = True
            
    if not can_view:
        return jsonify({"error": "Anda tidak punya izin untuk melihat data histori ini"}), 403

    query = db.session.query(ECGReading).join(Device).filter(Device.user_id == user_id_to_check)
    
    filter_day = request.args.get('filterDay')
    filter_class = request.args.get('filterClass')
    
    if filter_day:
        try:
            day_start = datetime.fromisoformat(filter_day).replace(hour=0, minute=0, second=0, microsecond=0)
            day_end = day_start + timedelta(days=1)
            query = query.filter(ECGReading.timestamp >= day_start, ECGReading.timestamp < day_end)
        except:
            pass 
            
    if filter_class:
        query = query.filter(ECGReading.prediction == filter_class)
        
    sort_order = request.args.get('sort', 'desc')
    if sort_order == 'asc':
        query = query.order_by(ECGReading.timestamp.asc())
    else:
        query = query.order_by(ECGReading.timestamp.desc())

    page = request.args.get('page', 1, type=int)
    per_page = 20
    pagination = query.paginate(page=page, per_page=per_page, error_out=False)
    
    readings = pagination.items
    
    return jsonify({
        "data": [
            {
                "id": r.id,
                "timestamp": r.timestamp.isoformat() + "Z",
                "classification": r.prediction,
                "heartRate": r.heart_rate
            } for r in readings
        ],
        "pagination": {
            "currentPage": pagination.page,
            "totalPages": pagination.pages,
            "totalItems": pagination.total
        }
    })

@app.route('/api/v1/correlatives/add', methods=['POST'])
@jwt_required()
def add_correlative():
    current_user_id = get_jwt_identity() # Ini ID si Kerabat
    data = request.get_json()
    scanned_code = data.get('scannedCode') # Ini adalah ID si Pasien
    if not scanned_code: return jsonify({"error": "'scannedCode' dibutuhkan"}), 400

    try: patient_id = int(scanned_code)
    except ValueError: return jsonify({"error": "Kode QR tidak valid"}), 400
        
    patient = User.query.get(patient_id)
    if not patient or patient.role != 'pasien':
        return jsonify({"error": "Kode QR tidak valid atau user bukan pasien"}), 404
        
    if patient.id == current_user_id:
        return jsonify({"error": "Anda tidak bisa memantau diri sendiri"}), 400

    existing = MonitoringRelationship.query.filter_by(monitor_id=current_user_id, patient_id=patient.id).first()
    if existing:
        return jsonify({"message": f"Anda sudah memantau {patient.email}"}), 200
        
    new_relationship = MonitoringRelationship(monitor_id=current_user_id, patient_id=patient.id)
    db.session.add(new_relationship)
    db.session.commit()
    
    app.logger.info(f"Hubungan baru dibuat: User {current_user_id} memantau User {patient.id}")
    return jsonify({"status": "success", "message": f"Kerabat '{patient.email}' berhasil ditambahkan."}), 201

@app.cli.command("init-db")
def init_db_command():
    with app.app_context():
        _db_path = app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')
        _db_dir = os.path.dirname(_db_path)
        if not os.path.exists(_db_dir):
            os.makedirs(_db_dir)
        db.create_all()
    print(f"Database berhasil diinisialisasi di {app.config['SQLALCHEMY_DATABASE_URI']}")

with app.app_context():
    load_all_models()

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(host='0.0.0.0', port=8080, debug=True)