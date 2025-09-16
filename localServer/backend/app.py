from flask import Flask, jsonify, request
from flask_cors import CORS
import random # Kita pakai ini untuk simulasi output model
from tensorflow import keras
import numpy as np

app = Flask(__name__)
CORS(app)

print("Memuat model Denoising...")
denoising_model = keras.models.load_model('model/model_denoising.h5') 
print("Memuat model Klasifikasi...")
classification_model = keras.models.load_model('model/model_klasifikasi_aritmia.h5')


# ----------------- FUNGSI MODEL (PLACEHOLDER) -----------------

def denoise_ecg_with_model(raw_ecg_data):
    """
    FUNGSI ASLI untuk proses denoising.
    """
    # 1. Ubah data input ke format yang diterima model (misal: numpy array dengan shape tertentu)
    input_data = np.array(raw_ecg_data).reshape(1, -1, 1) # Contoh reshape
    
    # 2. Jalankan prediksi untuk denoising
    denoised_data = denoising_model.predict(input_data)
    
    # 3. Kembalikan data bersih (mungkin perlu di-flatten lagi)
    return denoised_data.flatten().tolist()

def detect_arrhythmia_with_model(denoised_ecg_data):
    """
    FUNGSI ASLI untuk klasifikasi aritmia.
    """
    classes = ["Normal", "Atrial Fibrillation (AF)", "Premature Ventricular Contractions (PVC)", "Other"]
    
    # 1. Siapkan data untuk model klasifikasi
    input_data = np.array(denoised_ecg_data).reshape(1, -1, 1) # Contoh reshape
    
    # 2. Dapatkan hasil prediksi (biasanya berupa array probabilitas)
    predictions = classification_model.predict(input_data)[0]
    
    # 3. Cari indeks dengan probabilitas tertinggi dan cocokkan dengan nama kelas
    predicted_index = np.argmax(predictions)
    prediction_result = classes[predicted_index]
    
    return prediction_result

# ----------------- ROUTE API -----------------

@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!"})

@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    # ... (kode ambil data) ...
    if not request.is_json:
        return jsonify({"error": "Request harus dalam format JSON dengan header Content-Type application/json"}), 415

    data = request.get_json() # Lebih baik pakai get_json()

    # 2. Setelah aman, baru cek apakah kuncinya ada
    if not data or 'ecg_data' not in data:
        return jsonify({"error": "Kunci 'ecg_data' tidak ditemukan dalam body request"}), 400
    
    # Jika semua pengecekan lolos, baru proses datanya
    raw_data = data['ecg_data']

    # Validasi 1: Cek apakah datanya kosong
    if not raw_data:
        return jsonify({"error": "Data ECG tidak boleh kosong"}), 400

    # Validasi 2: Cek apakah semua item di dalamnya adalah angka
    if not all(isinstance(i, (int, float)) for i in raw_data):
        return jsonify({"error": "Semua data ECG harus berupa angka"}), 400

    # Validasi 3: Cek apakah panjang data masuk akal (misal: minimal 100 data point)
    if len(raw_data) < 100:
        return jsonify({"error": f"Data terlalu pendek ({len(raw_data)} points), minimal 100 points"}), 400

    # Proses denoising dan klasifikasi jika lolos validasi
    denoised = denoise_ecg_with_model(raw_data)
    result = detect_arrhythmia_with_model(denoised)
    return jsonify({
        "denoised_ecg": denoised,
        "classification": result
    }), 200

if __name__ == '__main__':
    app.run(debug=True, port=5000)