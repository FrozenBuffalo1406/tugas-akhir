import os
import sys
import logging
from logging.handlers import RotatingFileHandler
import time
import numpy as np
from datetime import datetime
from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_sqlalchemy import SQLAlchemy
from tensorflow import keras
from keras import layers
from tensorflow.keras.saving import register_keras_serializable
import tensorflow as tf
from keras.layers import Layer

# costum attention layer due to model training & saving
@tf.keras.saving.register_keras_serializable()
class Attention(Layer):
    """Custom Attention Layer yang sudah dimodernisasi & diperkuat."""
    def __init__(self, **kwargs):

        super(Attention, self).__init__(**kwargs)
        self.W = None
        self.b = None

    def build(self, input_shape):
        # Pastikan input_shape valid
        if not isinstance(input_shape, tf.TensorShape):
             input_shape = tf.TensorShape(input_shape)

        last_dim = input_shape[-1]
        timesteps = input_shape[1]

        if last_dim is None or timesteps is None:
             raise ValueError(
                 "Dimensi input untuk Attention layer harus diketahui "
                 f"(timesteps dan features). Diterima: {input_shape}"
             )

        self.W = self.add_weight(
            name="att_weight",
            shape=(last_dim, 1),
            initializer="normal",
            trainable=True
        )
        self.b = self.add_weight(
            name="att_bias",
            shape=(timesteps, 1),
            initializer="zeros",
            trainable=True
        )
        super(Attention, self).build(input_shape)
        print(f"--- Attention build completed. Weights W: {self.W is not None}, b: {self.b is not None} ---") # Debug message

    def call(self, x):
        if self.W is None or self.b is None:
             raise ValueError("Attention weights W or b not initialized. Build method might have failed.")


        et = tf.squeeze(tf.nn.tanh(tf.matmul(x, self.W) + self.b), axis=-1)
        at = tf.nn.softmax(et)
        at = tf.expand_dims(at, axis=-1)
        output = x * at
        return tf.reduce_sum(output, axis=1)

    def compute_output_shape(self, input_shape):
        return tf.TensorShape((input_shape[0], input_shape[-1]))

    def get_config(self):
        config = super(Attention, self).get_config()
        return config


# 3. logging, database, and app configurations

app = Flask(__name__)
basedir = os.path.abspath(os.path.dirname(__file__))
# Gunakan environment variable untuk path database, fallback ke SQLite lokal
# Di Docker, kita akan map volume untuk menyimpan DB di luar kontainer
db_path = os.getenv('DATABASE_PATH', os.path.join(basedir, 'data/ecg_data.db'))
db_dir = os.path.dirname(db_path)
if not os.path.exists(db_dir):
    os.makedirs(db_dir) # Buat folder 'data' jika belum ada

app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)
CORS(app)

# --- Setup Logging ---
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

# --- Variabel & Path Model ---
classification_model = None
MODEL_FILENAME = 'beat_classifier_model_SMOTE.keras'
MODEL_PATH = os.getenv('MODEL_PATH', os.path.join(basedir, f'model/{MODEL_FILENAME}'))

def load_all_models():
    """ Load model klasifikasi ke memori """
    global classification_model
    app.logger.info("="*50)
    app.logger.info(f"Mencoba memuat model dari: {MODEL_PATH}")
    try:
        if not os.path.exists(MODEL_PATH):
             app.logger.error(f"File model tidak ditemukan di path: {MODEL_PATH}")
             raise FileNotFoundError(f"File model tidak ditemukan di '{MODEL_PATH}'")

        classification_model = keras.models.load_model(
            MODEL_PATH,
            custom_objects={'Attention': Attention}
        )
        app.logger.info(f"✅ Model Klasifikasi ('{MODEL_FILENAME}') berhasil dimuat.")
    except Exception as e:
        app.logger.critical(f"❌ FATAL ERROR: Gagal memuat model. Error: {e}", exc_info=True)
        # Jangan sys.exit(1) di sini agar Gunicorn bisa handle error
        # sys.exit(1)
    app.logger.info("="*50)

# 4. table database models
class Device(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    mac_address = db.Column(db.String(17), unique=True, nullable=False)
    device_id_str = db.Column(db.String(80), unique=True, nullable=False)
    user_id = db.Column(db.String(80), nullable=True, default='default_user')
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    readings = db.relationship('ECGReading', backref='device', lazy=True, cascade="all, delete-orphan")

class ECGReading(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    timestamp = db.Column(db.DateTime, nullable=False)
    prediction = db.Column(db.String(50), nullable=False)
    probabilities = db.Column(db.JSON, nullable=True)
    ecg_beat_data = db.Column(db.JSON, nullable=True)
    ecg_afib_data = db.Column(db.JSON, nullable=True)
    processed_ecg_data = db.Column(db.JSON, nullable=True)
    device_id = db.Column(db.Integer, db.ForeignKey('device.id'), nullable=False)


# 5. util and preprocessing functions
def preprocess_input(data: list, target_length: int = 1024):
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
    return normalized_arr.reshape(1, target_length, 1)


# 6. API Routes
@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!", "model_loaded": (classification_model is not None)})

@app.route('/api/register-device', methods=['POST'])
def register_device():
    data = request.get_json()
    mac = data.get('mac_address')
    if not mac: return jsonify({"error": "'mac_address' dibutuhkan"}), 400

    device = Device.query.filter_by(mac_address=mac).first()
    if device:
        app.logger.info(f"Perangkat {mac} sudah terdaftar. Mengembalikan ID: {device.device_id_str}")
        return jsonify({"device_id": device.device_id_str}), 200
    else:
        count = Device.query.count()
        new_device_id = f"ECG_DEV_{count + 1:03d}"
        new_device = Device(mac_address=mac, device_id_str=new_device_id)
        db.session.add(new_device)
        db.session.commit()
        app.logger.info(f"Perangkat baru {mac} didaftarkan dengan ID: {new_device_id}")
        return jsonify({"device_id": new_device_id}), 201

@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    data = request.get_json()
    if not data or 'ecg_beat_data' not in data or 'device_id' not in data:
        return jsonify({"error": "Request body harus berisi 'ecg_beat_data' dan 'device_id'"}), 400

    device_id_str = data['device_id']
    ecg_beat = data['ecg_beat_data']
    ecg_afib = data.get('ecg_afib_data')
    timestamp_str = data.get('timestamp')

    app.logger.info(f"Menerima data dari device: {device_id_str} ({len(ecg_beat)} points).")

    device = Device.query.filter_by(device_id_str=device_id_str).first()
    if not device: return jsonify({"error": f"Device ID '{device_id_str}' belum terdaftar."}), 404

    if classification_model is None:
        app.logger.error("Model klasifikasi belum dimuat!")
        # Coba load lagi jika gagal di awal
        load_all_models()
        if classification_model is None:
             return jsonify({"error": "Model inferensi sedang tidak tersedia."}), 503

    try:
        processed_input = preprocess_input(ecg_beat)

        app.logger.info(f"Memulai inferensi untuk {device_id_str}...")
        start_time = time.time()
        prediction_probabilities = classification_model.predict(processed_input)[0]
        duration = (time.time() - start_time) * 1000
        app.logger.info(f"Inferensi {device_id_str} selesai dalam {duration:.2f} ms.")

        predicted_index = np.argmax(prediction_probabilities)
        arrhythmia_classes = ['Normal_Beat', 'Other', 'PVC'] 
        prediction_result = arrhythmia_classes[predicted_index]

        try:
            parsed_timestamp = datetime.fromisoformat(timestamp_str.replace("+07:00", ""))
        except (ValueError, TypeError):
            app.logger.warning(f"Timestamp tidak valid dari {device_id_str}: {timestamp_str}. Menggunakan waktu server.")
            parsed_timestamp = datetime.utcnow()

        new_reading = ECGReading(
            timestamp=parsed_timestamp,
            prediction=prediction_result,
            probabilities=prediction_probabilities.tolist(),
            ecg_beat_data=ecg_beat,
            ecg_afib_data=ecg_afib,
            processed_ecg_data=processed_input.flatten().tolist(),
            device_id=device.id
        )
        db.session.add(new_reading)
        db.session.commit()

        app.logger.info(f"Data {device_id_str} disimpan. Prediksi: {prediction_result}")
        return jsonify({
            "status": "success",
            "prediction": prediction_result,
            "probabilities": prediction_probabilities.tolist()
        })

    except Exception as e:
        db.session.rollback()
        app.logger.error(f"ERROR saat memproses data dari {device_id_str}: {e}", exc_info=True)
        return jsonify({"error": f"Kesalahan internal: {str(e)}"}), 500

# 7. app management commands
@app.cli.command("init-db")
def init_db_command():
    """Membuat tabel-tabel database dari awal."""
    with app.app_context():
        # Buat folder 'data' jika belum ada (khusus untuk init-db)
        _db_path = app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')
        _db_dir = os.path.dirname(_db_path)
        if not os.path.exists(_db_dir):
            os.makedirs(_db_dir)
        db.create_all()
    print(f"Database berhasil diinisialisasi di {app.config['SQLALCHEMY_DATABASE_URI']}")

# 8. INISIASI MODEL SAAT STARTUP
# Panggil load_all_models() di sini agar Gunicorn juga menjalankannya
# Gunakan app.app_context() jika load model butuh akses ke konfigurasi app
with app.app_context():
    load_all_models()

# 9. BLOK EKSEKUSI (Hanya untuk Development Lokal, Gunicorn tidak pakai ini)
if __name__ == '__main__':
    with app.app_context():
        db.create_all() # Buat DB jika belum ada saat development
    app.run(host='0.0.0.0', port=8080, debug=True)
