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
from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity, JWTManager, create_refresh_token
from scipy.signal import find_peaks
from dotenv import load_dotenv

import tensorflow as tf

load_dotenv()

app = Flask(__name__)
app.config["JWT_SECRET_KEY"] = os.getenv("JWT_SECRET_KEY", "kunci-rahasia-super-aman-ganti-ini-di-vps")
basedir = os.path.abspath(os.path.dirname(__file__))
# Bikin access token cepet expired (misal 15 menit)
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(minutes=15)
# Bikin refresh token tahan lama (misal 30 hari)
app.config["JWT_REFRESH_TOKEN_EXPIRES"] = timedelta(days=30)

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
app.logger.info('Aplikasi ECG startup')

classification_interpreter = None 
input_details = None
output_details = None
MODEL_FILENAME = 'beat_classifier_model_SMOTE.tflite' 
MODEL_PATH = os.getenv('MODEL_PATH', os.path.join(basedir, f'model/{MODEL_FILENAME}'))
BEAT_LABELS = ['Normal', 'PVC', 'Other'] 

def load_all_models():
    """ Load model TFLite ke memori """
    global classification_interpreter, input_details, output_details
    app.logger.info("="*50)
    app.logger.info(f"Mencoba memuat model TFLite dari: {MODEL_PATH}")
    try:
        if not os.path.exists(MODEL_PATH):
            app.logger.error(f"File model TFLite tidak ditemukan di path: {MODEL_PATH}")
            raise FileNotFoundError(f"File model tidak ditemukan di '{MODEL_PATH}'")

        classification_interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
        classification_interpreter.allocate_tensors()
        input_details = classification_interpreter.get_input_details()
        output_details = classification_interpreter.get_output_details()
        
        app.logger.info(f"✅ Model TFLite ('{MODEL_FILENAME}') berhasil dimuat.")
        app.logger.info(f"  -> Input Shape: {input_details[0]['shape']}")
        app.logger.info(f"  -> Output Shape: {output_details[0]['shape']}")
        
    except Exception as e:
        app.logger.critical(f"❌ FATAL ERROR: Gagal memuat model TFLite. Error: {e}", exc_info=True)
    app.logger.info("="*50)

class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(120), unique=True, nullable=False)
    name = db.Column(db.String(100), nullable=True) 
    password_hash = db.Column(db.String(128), nullable=False)
    devices = db.relationship('Device', backref='owner', lazy=True)
    
    monitoring = db.relationship(
        'MonitoringRelationship',
        foreign_keys='MonitoringRelationship.monitor_id',
        backref='monitor', lazy='dynamic',
        cascade="all, delete-orphan"
    )
    monitored_by = db.relationship(
        'MonitoringRelationship',
        foreign_keys='MonitoringRelationship.patient_id',
        backref='patient', lazy='dynamic',
        cascade="all, delete-orphan"
    )

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
    processed_ecg_data = db.Column(db.JSON, nullable=False) 
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

class MonitoringRelationship(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    monitor_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False) # ID Kerabat
    patient_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False) # ID Pasien
    __table_args__ = (db.UniqueConstraint('monitor_id', 'patient_id', name='_monitor_patient_uc'),)

def preprocess_input(data: list, target_length: int = 1024):
    arr = np.array(data, dtype=np.float32)
    if len(arr) < target_length:
        padding = np.zeros(target_length - len(arr), dtype=np.float32)
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
        peaks, _ = find_peaks(
            signal_1d_normalized, 
            height=0.7,
            prominence=0.4,
            distance=0.5 * SAMPLING_RATE 
        )
        if len(peaks) < 2: 
            app.logger.info(f"HR Calc: Puncak tidak cukup ({len(peaks)} peaks).")
            return None
        
        rr_intervals_samples = np.diff(peaks)
        avg_rr_samples = np.mean(rr_intervals_samples)
        
        if avg_rr_samples < 1e-6: # Hindari bagi dgn nol
             app.logger.warning(f"HR Calc: Avg RR samples terlalu kecil ({avg_rr_samples:.2f}).")
             return None
        
        bpm = (SAMPLING_RATE * 60) / avg_rr_samples   

        if bpm < 40 or bpm > 200:
            app.logger.warning(f"HR Calc: BPM {bpm:.2f} tidak wajar, dibuang.")
            return None
            
        app.logger.info(f"HR Calc: {len(peaks)} peaks. Avg RR: {avg_rr_samples:.2f} samples. BPM: {bpm:.2f}")
        return round(bpm, 2)
    except Exception as e:
        app.logger.error(f"HR Calc Error: {e}", exc_info=True)
        return None

def get_dynamic_role(user):
    is_pasien = db.session.query(Device.id).filter(Device.user_id == user.id).first() is not None
    is_kerabat = db.session.query(MonitoringRelationship.id).filter(MonitoringRelationship.monitor_id == user.id).first() is not None
    
    if is_pasien and is_kerabat:
        return 'pasien_kerabat'
    elif is_pasien:
        return 'pasien'
    elif is_kerabat:
        return 'kerabat'
    else:
        return 'undetermined'


@app.route("/api/v1")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!", "model_loaded": (classification_interpreter is not None)})

@app.route('/api/v1/auth/register', methods=['POST'])
def register_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    name = data.get('name', email.split('@')[0] if email else 'User')
    
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400
    if User.query.filter_by(email=email).first(): return jsonify({"error": "Email sudah terdaftar"}), 409
    
    hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')
    new_user = User(email=email, name=name, password_hash=hashed_password)
    db.session.add(new_user)
    db.session.commit()
    app.logger.info(f"User baru terdaftar: {email} (Nama: {name})")
    return jsonify({"message": f"User {email} berhasil dibuat"}), 201

@app.route('/api/v1/auth/login', methods=['POST'])
def login_user():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    if not email or not password: return jsonify({"error": "Email dan password dibutuhkan"}), 400

    user = User.query.filter_by(email=email).first()
    if user and bcrypt.check_password_hash(user.password_hash, password):
        access_token = create_access_token(identity=str(user.id))
        
        refresh_token = create_refresh_token(identity=str(user.id)) # <--- BARU

        role = get_dynamic_role(user)
        app.logger.info(f"User login berhasil: {email}")

        return jsonify(
            access_token=access_token, 
            refresh_token=refresh_token, # <--- BARU
            user_id=user.id, 
            role=role,
            name=user.name
        )
    app.logger.warning(f"Login gagal untuk: {email}")
    return jsonify({"error": "Email atau password salah"}), 401

@app.route('/api/v1/auth/refresh', methods=['POST'])
@jwt_required(refresh=True) # <-- Ini kunci pentingnya
def refresh_token():
    """
    Endpoint ini cuma bisa diakses pake REFRESH TOKEN.
    Kalo sukses, dia ngasih ACCESS TOKEN baru.
    """
    identity = int(get_jwt_identity())
    new_access_token = create_access_token(identity=identity)
    app.logger.info(f"Token di-refresh untuk user {identity}")
    return jsonify(access_token=new_access_token), 200



@app.route('/api/v1/register-device', methods=['POST'])
def register_device():
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
    current_user_id = int(get_jwt_identity())
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
    app.logger.info(f"User {current_user_id} berhasil mengklaim device {device_id_str}.")
    return jsonify({"message": f"Device {device_id_str} berhasil diklaim."}), 200

@app.route('/api/v1/unclaim-device', methods=['POST'])
@jwt_required()
def unclaim_device():
    current_user_id = int(get_jwt_identity())
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

    flatline_count = 0
    for point in ecg_beat:
        if point <= 10 or point >= 4090: flatline_count += 1
    if (flatline_count / len(ecg_beat)) > 0.8:
        app.logger.warning(f"Data from {device_id_str} ditolak: Sinyal flatline.")
        return jsonify({"error": "Data EKG tidak valid (sinyal flatline/elektroda terlepas)"}), 400

    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device: return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404

    if classification_interpreter is None:
        app.logger.error("Model TFLite (Beat) belum dimuat!")
        return jsonify({"error": "Model inferensi sedang tidak tersedia."}), 503

    try:
        processed_input = preprocess_input(ecg_beat, target_length=1024)

        app.logger.info(f"Memulai inferensi TFLite untuk {device_id_str}...")
        start_time = time.time()
        classification_interpreter.set_tensor(input_details[0]['index'], processed_input)
        classification_interpreter.invoke()
        prediction_probabilities = classification_interpreter.get_tensor(output_details[0]['index'])[0]
        
        duration = (time.time() - start_time) * 1000
        app.logger.info(f"Inferensi TFLite {device_id_str} selesai dalam {duration:.2f} ms.")
        
        predicted_index = np.argmax(prediction_probabilities)
        arrhythmia_classes = BEAT_LABELS 
        prediction_result = arrhythmia_classes[predicted_index]
        heart_rate = calculate_heart_rate(processed_input.flatten()) 

        try:
            if timestamp_str and "+" in timestamp_str: timestamp_str = timestamp_str.split("+")[0]
            parsed_timestamp = datetime.fromisoformat(timestamp_str)
        except (ValueError, TypeError, AttributeError):
            app.logger.warning(f"Timestamp tidak valid dari {device_id_str}: {timestamp_str}. Menggunakan waktu server.")
            parsed_timestamp = datetime.utcnow()

        new_reading = ECGReading(
            timestamp=parsed_timestamp,
            prediction=prediction_result,
            heart_rate=heart_rate,
            processed_ecg_data=processed_input.flatten().tolist(), 
            device_id=device.id
        )
        db.session.add(new_reading)
        db.session.commit()

        app.logger.info(f"Data {device_id_str} disimpan. Prediksi: {prediction_result}, HR: {heart_rate}")
        return jsonify({
            "status": "success",
            "prediction": prediction_result,
            "heartRate": heart_rate,
            "probabilities": prediction_probabilities.tolist()
        })
    except Exception as e:
        db.session.rollback()
        app.logger.error(f"ERROR saat memproses data dari {device_id_str}: {e}", exc_info=True)
        return jsonify({"error": f"Kesalahan internal: {str(e)}"}), 500

@app.route('/api/v1/profile', methods=['GET'])
@jwt_required()
def get_profile():
    current_user_id = int(get_jwt_identity())
    user = User.query.get(current_user_id)
    if not user: return jsonify({"error": "User tidak ditemukan"}), 404
    
    role = get_dynamic_role(user)
    
    monitors = user.monitored_by.all()
    correlatives_list = [
        {"id": m.monitor.id, "email": m.monitor.email, "name": m.monitor.name} for m in monitors
    ]
    
    patients = user.monitoring.all()
    patients_list = [
        {"id": p.patient.id, "email": p.patient.email, "name": p.patient.name} for p in patients
    ]
    
    return jsonify({
        "user": {
            "id": user.id,
            "email": user.email,
            "name": user.name,
            "role": role
        },
        "correlatives_who_monitor_me": correlatives_list,
        "patients_i_monitor": patients_list
    })

@app.route('/api/v1/dashboard', methods=['GET'])
@jwt_required()
def get_dashboard():
    current_user_id = int(get_jwt_identity())
    user = User.query.get(current_user_id)
    if not user: return jsonify({"error": "User tidak ditemukan"}), 404

    response_data = []

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
    current_user_id = int(get_jwt_identity())
    
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
        relationship = MonitoringRelationship.query.filter_by(monitor_id=current_user_id, patient_id=user_id_to_check).first()
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
        except: pass 
            
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
            {"id": r.id, "timestamp": r.timestamp.isoformat() + "Z", "classification": r.prediction, "heartRate": r.heart_rate} 
            for r in readings
        ],
        "pagination": {"currentPage": pagination.page, "totalPages": pagination.pages, "totalItems": pagination.total}
    })

@app.route('/api/v1/reading/<int:reading_id>', methods=['GET'])
@jwt_required()
def get_reading_detail(reading_id):
    # --- [FIX] Ubah identity jadi INTEGER ---
    current_user_id = int(get_jwt_identity())
    # --------------------------------------
    
    app.logger.info(f"User {current_user_id} meminta detail untuk reading ID: {reading_id}")
    
    reading = ECGReading.query.get(reading_id)
    
    if not reading:
        return jsonify({"error": "Data bacaan tidak ditemukan"}), 404

    # --- INI BAGIAN PENTING: OTORISASI ---
    # Kita harus cek apakah user ini boleh liat data ini
    try:
        device = reading.device
        if not device:
             # Ini harusnya gak terjadi, tapi buat jaga-jaga
             app.logger.error(f"Reading {reading_id} tidak punya device? Aneh.")
             return jsonify({"error": "Data device korup"}), 500
             
        patient_id = device.user_id
        
        # 1. Cek apakah user adalah si pasien sendiri
        if current_user_id == patient_id:
            pass # Boleh liat
        else:
            # 2. Cek apakah user adalah kerabat yang memantau
            relationship = MonitoringRelationship.query.filter_by(
                monitor_id=current_user_id, 
                patient_id=patient_id
            ).first()
            
            if not relationship:
                # Kalo bukan pasien & bukan kerabat, usir!
                app.logger.warning(f"User {current_user_id} mencoba akses {reading_id} milik {patient_id} secara ilegal.")
                return jsonify({"error": "Anda tidak punya izin untuk melihat data ini"}), 403

    except Exception as e:
        app.logger.error(f"Error saat cek otorisasi reading: {e}", exc_info=True)
        return jsonify({"error": "Kesalahan internal saat verifikasi izin"}), 500
    
    # --- Kalo lolos, kirim datanya ---
    app.logger.info(f"User {current_user_id} berhasil akses {reading_id}.")
    return jsonify({
        "id": reading.id,
        "timestamp": reading.timestamp.isoformat() + "Z",
        "classification": reading.prediction,
        "heartRate": reading.heart_rate,
        "ecg_data": reading.processed_ecg_data # <-- INI DIA DATANYA
    })

@app.route('/api/v1/correlatives/add', methods=['POST'])
@jwt_required()
def add_correlative():
    current_user_id = int(get_jwt_identity())
    data = request.get_json()
    scanned_code = data.get('scannedCode')
    if not scanned_code: return jsonify({"error": "'scannedCode' dibutuhkan"}), 400

    try: patient_id = int(scanned_code)
    except ValueError: return jsonify({"error": "Kode QR tidak valid"}), 400
        
    patient = User.query.get(patient_id)
    if not patient: return jsonify({"error": "Kode QR tidak valid atau user bukan pasien"}), 404
    
    is_pasien = db.session.query(Device.id).filter(Device.user_id == patient.id).first() is not None
    if not is_pasien:
         return jsonify({"error": "Anda hanya bisa memantau user yang memiliki device (pasien)"}), 400
        
    if patient.id == current_user_id:
        return jsonify({"error": "Anda tidak bisa memantau diri sendiri"}), 400

    existing = MonitoringRelationship.query.filter_by(monitor_id=current_user_id, patient_id=patient.id).first()
    if existing:
        return jsonify({"message": f"Anda sudah memantau {patient.name}"}), 200
        
    new_relationship = MonitoringRelationship(monitor_id=current_user_id, patient_id=patient.id)
    db.session.add(new_relationship)
    db.session.commit()
    app.logger.info(f"Hubungan baru dibuat: User {current_user_id} memantau User {patient.id}")
    return jsonify({"status": "success", "message": f"Kerabat '{patient.name}' berhasil ditambahkan."}), 201

@app.route('/api/v1/correlatives/remove', methods=['DELETE'])
@jwt_required()
def remove_correlative():
    current_user_id = int(get_jwt_identity())
    data = request.get_json()
    
    relationship_to_remove = None
    action_type = None

    if 'patient_id' in data:
        try: patient_id_to_remove = int(data['patient_id'])
        except ValueError: return jsonify({"error": "patient_id tidak valid"}), 400
        
        relationship_to_remove = MonitoringRelationship.query.filter_by(
            monitor_id=current_user_id, 
            patient_id=patient_id_to_remove
        ).first()
        action_type = "berhenti memantau"

    elif 'monitor_id' in data:
        try: monitor_id_to_remove = int(data['monitor_id'])
        except ValueError: return jsonify({"error": "monitor_id tidak valid"}), 400
            
        relationship_to_remove = MonitoringRelationship.query.filter_by(
            monitor_id=monitor_id_to_remove, 
            patient_id=current_user_id
        ).first()
        action_type = "mencabut izin"
        
    else:
        return jsonify({"error": "Harus menyertakan 'patient_id' (untuk kerabat) atau 'monitor_id' (untuk pasien)"}), 400

    if relationship_to_remove:
        db.session.delete(relationship_to_remove)
        db.session.commit()
        app.logger.info(f"User {current_user_id} berhasil {action_type} user lain.")
        return jsonify({"status": "success", "message": "Hubungan kerabat berhasil dihapus."}), 200
    else:
        return jsonify({"error": "Hubungan kerabat tidak ditemukan."}), 404

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
