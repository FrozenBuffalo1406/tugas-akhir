import os
import sys
import numpy as np
from flask import Flask, jsonify, request
from flask_cors import CORS
from tensorflow import keras


app = Flask(__name__)
CORS(app)

# --- Variabel Global untuk Model ---
# Kita set `None` dulu, nanti diisi di fungsi `load_all_models`
denoiser_model = None
classification_model = None

# --- Path ke File Model ---
# Pastikan file-file ini ada di dalam folder 'backend/' lo
DENOISER_MODEL_PATH = 'model/denoise_model_.h5'
CLASSIFICATION_MODEL_PATH = 'model/classification_model.h5' # Ganti dengan nama file model klasifikasi lo

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
        print("Server akan berhenti. Pastikan path model sudah benar dan file tidak rusak.")
        sys.exit(1) # Hentikan aplikasi jika model gagal di-load
        
    print("="*50)

# =============================================================================
# 2. FUNGSI UTILITY & PREPROCESSING
# =============================================================================

def preprocess_input(data: list, target_length: int) -> np.ndarray:
    """
    Menyiapkan data mentah dari request agar siap digunakan oleh model.
    """
    # Konversi ke numpy array
    arr = np.array(data, dtype=np.float32)

    # Padding atau trimming agar panjangnya sesuai
    if len(arr) < target_length:
        padding = np.zeros(target_length - len(arr))
        arr = np.concatenate([arr, padding])
    elif len(arr) > target_length:
        arr = arr[:target_length]
    
    # Normalisasi (sama seperti saat training)
    mean = np.mean(arr)
    std = np.std(arr)
    if std == 0: std = 1
    normalized_arr = (arr - mean) / std

    # Reshape untuk input model (batch_size, steps, channels)
    return normalized_arr.reshape(1, target_length, 1), mean, std

# =============================================================================
# 3. ROUTE API
# =============================================================================

@app.route("/")
def index():
    return jsonify({"message": "Server Analisis ECG berjalan!", "models_loaded": True})

@app.route('/api/analyze-ecg', methods=['POST'])
def analyze_ecg():
    # --- Validasi Awal ---
    if not request.is_json:
        return jsonify({"error": "Request harus dalam format JSON"}), 415
    
    data = request.get_json()
    if not data or 'ecg_data' not in data:
        return jsonify({"error": "Kunci 'ecg_data' tidak ditemukan"}), 400
    
    raw_ecg = data['ecg_data']
    if not isinstance(raw_ecg, list) or len(raw_ecg) < 100:
        return jsonify({"error": "Data ECG harus berupa list dengan minimal 100 data point"}), 400

    try:
        # Mulai hitung waktu total
        start_time_total = time.time()
        
        # =====================================================================
        # TAHAP 1: PREPROCESSING & DENOISING
        # =====================================================================
        print("1. Memulai proses denoising...")
        start_time_denoise = time.time()

        # Preprocess data untuk model denoiser (panjang input 1024)
        denoiser_input, mean, std = preprocess_input(raw_ecg, 1024)
        
        # Prediksi sinyal bersih
        denoised_signal_normalized = denoiser_model.predict(denoiser_input).flatten()
        
        # Denormalisasi untuk mendapatkan sinyal asli kembali
        denoised_signal = (denoised_signal_normalized * std) + mean
        
        end_time_denoise = time.time()
        duration_denoise = (end_time_denoise - start_time_denoise) * 1000 # dalam milidetik

        # =====================================================================
        # TAHAP 2: DETEKSI ARITMIA
        # =====================================================================
        print("2. Memulai proses klasifikasi...")
        start_time_classify = time.time()

        # Preprocess sinyal yang SUDAH BERSIH untuk model klasifikasi (misal panjang input 1000)
        # Kita gunakan denoised_signal.tolist() sebagai input
        classifier_input, _, _ = preprocess_input(denoised_signal.tolist(), 1000)
        
        # Prediksi kelas aritmia
        prediction_probabilities = classification_model.predict(classifier_input)[0]
        
        # Ambil kelas dengan probabilitas tertinggi
        predicted_index = np.argmax(prediction_probabilities)
        arrhythmia_classes = ['Normal', 'AF', 'PVC', 'Other'] # Sesuaikan dengan kelas model lo
        prediction_result = arrhythmia_classes[predicted_index]
        
        end_time_classify = time.time()
        duration_classify = (end_time_classify - start_time_classify) * 1000 # dalam milidetik

        # =====================================================================
        # TAHAP 3: PERSIAPAN & PENGIRIMAN RESPON
        # =====================================================================
        end_time_total = time.time()
        duration_total = (end_time_total - start_time_total) * 1000 # dalam milidetik
        
        print(f"3. Hasil Prediksi: {prediction_result}")
        print(f"   - Durasi Denoising: {duration_denoise:.2f} ms")
        print(f"   - Durasi Klasifikasi: {duration_classify:.2f} ms")
        print(f"   - Durasi Total: {duration_total:.2f} ms")

        # Kirim respons lengkap ke frontend
        return jsonify({
            "prediction": prediction_result,
            "denoised_data": denoised_signal.tolist(),
            "probabilities": prediction_probabilities.tolist(),
            "status": "success",
            "processing_time_ms": {
                "denoising": round(duration_denoise, 2),
                "classification": round(duration_classify, 2),
                "total": round(duration_total, 2)
            }
        })

    except Exception as e:
        print(f"ERROR saat prediksi: {e}")
        return jsonify({"error": "Terjadi kesalahan internal saat memproses data ECG."}), 500


# =============================================================================
# 4. JALANKAN APLIKASI
# =============================================================================

if __name__ == '__main__':
    # Load semua model terlebih dahulu sebelum server menerima request
    load_all_models()
    # Jalankan server Flask
    app.run(debug=True, port=5000)