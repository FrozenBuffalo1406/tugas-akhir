import React, { useState } from 'react';
import './App.css';

function App() {
  // State untuk menyimpan data input dari textarea
  const [ecgInput, setEcgInput] = useState('0.1, 0.2, 0.5, 1.2, 0.4, -0.3, 0.1');
  // State untuk menyimpan hasil prediksi dari backend
  const [result, setResult] = useState(null);
  // State untuk menunjukkan status loading saat menunggu respons API
  const [isLoading, setIsLoading] = useState(false);
  // State untuk menyimpan pesan error
  const [error, setError] = useState('');

  const handleAnalyzeClick = () => {
    // Reset state sebelum request baru
    setResult(null);
    setError('');
    setIsLoading(true);

    // 1. Ubah string dari textarea menjadi array angka
    const ecgDataArray = ecgInput.split(',').map(Number).filter(n => !isNaN(n));

    if (ecgDataArray.length === 0) {
      setError('Format data tidak valid. Harap masukkan angka yang dipisahkan koma.');
      setIsLoading(false);
      return;
    }

    // 2. Kirim data ke API backend menggunakan fetch
    fetch('http://127.0.0.1:5000/api/analyze-ecg', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ecg_data: ecgDataArray }),
    })
      .then(response => {
        if (!response.ok) {
          throw new Error('Network response was not ok');
        }
        return response.json();
      })
      .then(data => {
        // 3. Jika berhasil, simpan hasilnya di state 'result'
        setResult(data.prediction);
      })
      .catch(err => {
        // 4. Jika gagal, simpan pesan error di state 'error'
        setError('Gagal terhubung ke server atau terjadi masalah.');
        console.error("Fetch error:", err);
      })
      .finally(() => {
        // 5. Apapun hasilnya (sukses/gagal), set loading menjadi false
        setIsLoading(false);
      });
  };

  return (
    <div className="App">
      <header className="App-header">
        <h1>Analisis Sinyal ECG 🩺</h1>
        <p>Masukkan data ECG single-lead (dipisahkan oleh koma).</p>
        
        <textarea
          className="ecg-textarea"
          rows="5"
          value={ecgInput}
          onChange={(e) => setEcgInput(e.target.value)}
          placeholder="Contoh: 0.1, 0.2, 0.5, 1.2, 0.4, ..."
        />

        <button 
          className="analyze-button" 
          onClick={handleAnalyzeClick} 
          disabled={isLoading}
        >
          {isLoading ? 'Menganalisis...' : 'Analisis Sekarang'}
        </button>

        {/* Area untuk menampilkan hasil */}
        {error && <p className="error-message">{error}</p>}
        
        {result && (
          <div className="result-container">
            <h2>Hasil Deteksi:</h2>
            <p className="prediction">{result}</p>
          </div>
        )}
      </header>
    </div>
  );
}

export default App;