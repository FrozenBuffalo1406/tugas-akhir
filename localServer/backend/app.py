# =============================================================================
# 1. IMPORT LIBRARY YANG DIBUTUHKAN
# =============================================================================
import os
import sys
import numpy as np
from datetime import datetime
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
# from flask_migrate import Migrate # [PERBAIKAN] Dinonaktifkan untuk setup database yang lebih simpel
# from flask_bcrypt import Bcrypt # Dinonaktifkan untuk tes
# from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity, JWTManager # Dinonaktifkan untuk tes
# from tensorflow import keras # Dinonaktifkan karena model belum dipakai

# =============================================================================
# 2. KONFIGURASI APLIKASI, DATABASE, DAN OTENTIKASI
# =============================================================================

app = Flask(__name__)
basedir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///' + os.path.join(basedir, 'ecg_data.db')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
# migrate = Migrate(app, db) # [PERBAIKAN] Dinonaktifkan untuk setup database yang lebih simpel
CORS(app)

# --- Variabel & Path Model (Dinonaktifkan) ---
# denoiser_model = None
# classification_model = None
# DENOISER_MODEL_PATH = 'model/denoise_model_.h5'
# CLASSIFICATION_MODEL_PATH = 'model/classification_model.h5'

def load_all_models():
    """
    Fungsi ini dinonaktifkan sementara karena model belum siap.
    """
    print("="*50)
    print("MODE TES: Proses load model dilewati.")
    print("="*50)
    # global denoiser_model, classification_model
    # print("Memuat model ke memori...")
    # try:
    #     if os.path.exists(DENOISER_MODEL_PATH):
    #         denoiser_model = keras.models.load_model(DENOISER_MODEL_PATH)
    #         print(f"✅ Model Denoising ('{DENOISER_MODEL_PATH}') berhasil dimuat.")
    #     else:
    #         raise FileNotFoundError(f"File model denoising tidak ditemukan di '{DENOISER_MODEL_PATH}'")

    #     if os.path.exists(CLASSIFICATION_MODEL_PATH):
    #         classification_model = keras.models.load_model(CLASSIFICATION_MODEL_PATH)
    #         print(f"✅ Model Klasifikasi ('{CLASSIFICATION_MODEL_PATH}') berhasil dimuat.")
    #     else:
    #         raise FileNotFoundError(f"File model klasifikasi tidak ditemukan di '{CLASSIFICATION_MODEL_PATH}'")
    # except Exception as e:
    #     print(f"❌ FATAL ERROR: Gagal memuat model. Error: {e}")
    #     sys.exit(1)
    # print("="*50)

# =============================================================================
# 3. DEFINISI MODEL DATABASE (STRUKTUR TABEL)
# =============================================================================

class Device(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    mac_address = db.Column(db.String(17), unique=True, nullable=False) # [PERBAIKAN] Tambah kolom mac_address
    device_id_str = db.Column(db.String(80), unique=True, nullable=False)
    user_id = db.Column(db.String(80), nullable=True, default='default_user') # user_id dibuat opsional
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

class ECGReading(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    timestamp = db.Column(db.DateTime, nullable=False)
    prediction = db.Column(db.String(50), nullable=False)
    probabilities = db.Column(db.JSON, nullable=True)
    # [PERBAIKAN] Ganti nama kolom agar sesuai dengan data dari ESP32
    ecg_beat_data = db.Column(db.JSON, nullable=True)
    ecg_afib_data = db.Column(db.JSON, nullable=True)
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

# =============================================================================
# 4. API SERVICES
# =============================================================================

@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!"})


# [PERBAIKAN] Endpoint ini diubah total untuk menangani registrasi via MAC Address
@app.route('/api/register-device', methods=['POST'])
def register_device():
    """
    Endpoint untuk mendaftarkan perangkat baru berdasarkan MAC Address.
    Jika MAC sudah ada, kembalikan ID yang ada. Jika baru, buat ID baru.
    """
    data = request.get_json()
    mac = data.get('mac_address')
    if not mac:
        return jsonify({"error": "'mac_address' dibutuhkan"}), 400

    device = Device.query.filter_by(mac_address=mac).first()
    
    if device:
        # Perangkat sudah ada, kembalikan ID-nya
        print(f"Perangkat {mac} sudah terdaftar. Mengembalikan ID: {device.device_id_str}")
        return jsonify({"device_id": device.device_id_str}), 200
    else:
        # Perangkat baru, buat ID baru dan simpan
        count = Device.query.count()
        new_device_id = f"ECG_DEV_{count + 1:03d}"
        
        new_device = Device(mac_address=mac, device_id_str=new_device_id)
        db.session.add(new_device)
        db.session.commit()
        
        print(f"Perangkat baru {mac} berhasil didaftarkan dengan ID: {new_device_id}")
        return jsonify({"device_id": new_device_id}), 201


# [PERBAIKAN] Endpoint ini diubah untuk menerima format data baru dari ESP32
@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    data = request.get_json()
    # Validasi input baru
    if not data or 'ecg_beat_data' not in data or 'device_id' not in data:
        return jsonify({"error": "Request body harus berisi 'ecg_beat_data' dan 'device_id'"}), 400
    
    # Ekstrak data baru
    device_id_str = data['device_id']
    ecg_beat = data['ecg_beat_data']
    ecg_afib = data.get('ecg_afib_data') # Opsional
    timestamp_str = data.get('timestamp')

    print(f"Menerima data dari device: {device_id_str} dengan {len(ecg_beat)} data points.")

    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device:
        return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404
        
    try:
        # --- Proses Model ML Dinonaktifkan ---
        prediction_result = "Normal (Tes)"
        prediction_probabilities = {"Normal": 1.0, "AF": 0.0, "PVC": 0.0}
        
        # --- Simpan ke Database ---
        # Konversi timestamp dengan menghapus offset zona waktu
        parsed_timestamp = datetime.fromisoformat(timestamp_str.replace("+07:00", ""))
        
        new_reading = ECGReading(
            timestamp=parsed_timestamp,
            prediction=prediction_result,
            probabilities=prediction_probabilities,
            ecg_beat_data=ecg_beat, # Simpan data baru
            ecg_afib_data=ecg_afib, # Simpan data baru
            device_id=device.id
        )
        db.session.add(new_reading)
        db.session.commit()
        
        print(f"Data dari {device_id_str} berhasil disimpan dengan prediksi (tes): {prediction_result}")
        return jsonify({"status": "success", "prediction_test": prediction_result})

    except Exception as e:
        db.session.rollback()
        print(f"ERROR saat memproses data: {e}")
        return jsonify({"error": f"Terjadi kesalahan internal: {str(e)}"}), 500

# =============================================================================
# 5. BLOK EKSEKUSI UTAMA
# =============================================================================
if __name__ == '__main__':
    from waitress import serve 
    with app.app_context():
        db.create_all()
        # load_all_models() # Dinonaktifkan untuk tes
        
    # [PERBAIKAN] Menjalankan server menggunakan Waitress
    print(f"Menjalankan server Waitress di http://192.168.1.88:8080")
    serve(app, host='0.0.0.0', port=8080)