# =============================================================================
# 1. IMPORT LIBRARY YANG DIBUTUHKAN
# =============================================================================
import os
import numpy as np
from datetime import datetime
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate

# =============================================================================
# 2. KONFIGURASI APLIKASI FLASK & DATABASE
# =============================================================================

# Inisialisasi aplikasi Flask
app = Flask(__name__)

# Konfigurasi path database. Database akan dibuat di folder yang sama dengan file app.py ini.
basedir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///' + os.path.join(basedir, 'ecg_data.db')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Inisialisasi ekstensi untuk interaksi dengan database
db = SQLAlchemy(app)
migrate = Migrate(app, db)
CORS(app) # Mengizinkan akses API dari domain lain (berguna untuk frontend web)

# =============================================================================
# 3. DEFINISI MODEL DATABASE (STRUKTUR TABEL)
# =============================================================================

# Model untuk tabel 'Device'
# Tabel ini menyimpan informasi tentang setiap perangkat ESP32 yang terdaftar.
class Device(db.Model):
    id = db.Column(db.Integer, primary_key=True) # ID internal database
    mac_address = db.Column(db.String(17), unique=True, nullable=False) # 'KTP' unik dari ESP32
    device_id_str = db.Column(db.String(80), unique=True, nullable=False) # 'NIM' yang kita berikan ke ESP32
    user_id = db.Column(db.String(80), nullable=True, default='default_user') # ID pengguna (bisa diintegrasikan nanti)
    created_at = db.Column(db.DateTime, default=datetime.utcnow) # Waktu perangkat pertama kali didaftarkan
    
    # Membuat relasi ke tabel ECGReading
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

# Model untuk tabel 'ECGReading'
# Tabel ini menyimpan setiap data rekaman EKG yang dikirim oleh perangkat.
class ECGReading(db.Model):
    id = db.Column(db.Integer, primary_key=True) # ID internal database
    timestamp = db.Column(db.DateTime, nullable=False) # Timestamp kapan data direkam (dari ESP32)
    prediction = db.Column(db.String(50), nullable=False) # Hasil prediksi model (misal: "Normal", "AFIB")
    probabilities = db.Column(db.JSON, nullable=True) # Probabilitas dari setiap kelas prediksi
    ecg_beat_data = db.Column(db.JSON, nullable=True) # Data EKG hasil filter untuk analisis beat
    ecg_afib_data = db.Column(db.JSON, nullable=True) # Data EKG hasil filter untuk analisis AFib
    
    # Foreign Key yang menghubungkan rekaman ini ke perangkat tertentu
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)

# =============================================================================
# 4. DEFINISI RUTE API (ENDPOINT)
# =============================================================================

# Rute default untuk mengecek apakah server berjalan
@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG (Mode Tes Komunikasi) berjalan!"})


# --- API UNTUK REGISTRASI PERANGKAT ---
# Endpoint ini dipanggil oleh ESP32 saat pertama kali nyala untuk minta ID.
@app.route('/api/register-device', methods=['POST'])
def register_device():
    """
    Endpoint untuk mendaftarkan perangkat baru atau mengambil ID perangkat yang sudah ada.
    Menerima: JSON dengan key 'mac_address'.
    Mengembalikan: JSON dengan key 'device_id'.
    """
    data = request.get_json()
    mac = data.get('mac_address')
    
    # Validasi input
    if not mac:
        return jsonify({"error": "'mac_address' dibutuhkan"}), 400

    # Cek apakah perangkat dengan MAC address ini sudah ada di database
    device = Device.query.filter_by(mac_address=mac).first()
    
    if device:
        # Jika sudah ada, langsung kembalikan ID yang tersimpan
        print(f"Perangkat {mac} sudah terdaftar. Mengembalikan ID: {device.device_id_str}")
        return jsonify({"device_id": device.device_id_str}), 200
    else:
        # Jika belum ada, buat ID baru, simpan ke database, lalu kembalikan
        count = Device.query.count()
        new_device_id = f"ECG_DEV_{count + 1:03d}" # Membuat ID berurutan, misal: ECG_DEV_001
        
        new_device = Device(mac_address=mac, device_id_str=new_device_id)
        db.session.add(new_device)
        db.session.commit()
        
        print(f"Perangkat baru {mac} berhasil didaftarkan dengan ID: {new_device_id}")
        return jsonify({"device_id": new_device_id}), 201


# --- API UTAMA UNTUK ANALISIS EKG ---
# Endpoint ini dipanggil oleh ESP32 setiap kali buffer data penuh.
@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    """
    Endpoint utama untuk menerima data EKG, melakukan analisis (saat ini dummy),
    dan menyimpannya ke database.
    Menerima: JSON dengan key 'device_id', 'timestamp', 'ecg_beat_data', 'ecg_afib_data'.
    Mengembalikan: JSON berisi status dan hasil prediksi (tes).
    """
    data = request.get_json()
    
    # Validasi input, pastikan data yang dikirim lengkap
    if not data or 'ecg_beat_data' not in data or 'device_id' not in data:
        return jsonify({"error": "Request body harus berisi 'ecg_beat_data' dan 'device_id'"}), 400
    
    # Ekstrak data dari JSON
    device_id_str = data['device_id']
    ecg_beat = data['ecg_beat_data']
    ecg_afib = data.get('ecg_afib_data') # Opsional, pakai .get()
    timestamp_str = data.get('timestamp')

    print(f"Menerima data dari device: {device_id_str} dengan {len(ecg_beat)} data points.")

    # Cari perangkat di database berdasarkan device_id_str yang dikirim
    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device:
        return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404
        
    try:
        # --- PROSES MODEL MACHINE LEARNING (SEMENTARA DIGANTI DATA DUMMY) ---
        # Di sinilah nanti kamu akan memanggil model TensorFlow/PyTorch-mu.
        # Contoh:
        #   denoised_signal = denoiser_model.predict(np.array(ecg_beat))
        #   prediction_result, probabilities = classification_model.predict(denoised_signal)
        
        # Data Dummy untuk tujuan tes komunikasi
        prediction_result = "Normal (Tes)"
        prediction_probabilities = {"Normal": 1.0, "AF": 0.0, "PVC": 0.0}
        
        # --- SIMPAN HASIL KE DATABASE ---
        
        # Konversi string timestamp dari ESP32 menjadi objek datetime Python
        # ESP32 mengirim format ISO 8601 dengan offset +07:00, kita perlu sesuaikan
        # Untuk sementara, kita hapus offsetnya agar bisa disimpan di SQLite
        parsed_timestamp = datetime.fromisoformat(timestamp_str.replace("+07:00", ""))
        
        new_reading = ECGReading(
            timestamp=parsed_timestamp,
            prediction=prediction_result,
            probabilities=prediction_probabilities,
            ecg_beat_data=ecg_beat, # Simpan data yang relevan untuk dianalisis ulang jika perlu
            ecg_afib_data=ecg_afib,
            device_id=device.id # Hubungkan rekaman ini dengan ID perangkat yang benar
        )
        db.session.add(new_reading)
        db.session.commit()
        
        print(f"Data dari {device_id_str} berhasil disimpan dengan prediksi (tes): {prediction_result}")
        return jsonify({"status": "success", "prediction_test": prediction_result})

    except Exception as e:
        # Tangani jika ada error saat pemrosesan atau penyimpanan
        db.session.rollback() # Batalkan perubahan database jika ada error
        print(f"ERROR saat memproses data: {e}")
        return jsonify({"error": f"Terjadi kesalahan internal: {str(e)}"}), 500

# =============================================================================
# 5. BLOK EKSEKUSI UTAMA
# =============================================================================
if __name__ == '__main__':
    # Blok ini akan dijalankan saat kamu menjalankan `python app.py`
    with app.app_context():
        # Perintah ini akan membuat file database (ecg_data.db) dan semua tabel
        # di dalamnya jika belum ada.
        db.create_all()
        
    # Menjalankan aplikasi Flask
    # app.run(host='0.0.0.0', port=5000, debug=True)
    app.run(host='0.0.0.0', port=8080, debug=True)