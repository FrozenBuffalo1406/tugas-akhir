import React from 'react';
import './App.css';
import HeartRateChart from './HeartRateChart'; // Asumsi file ada di folder yang sama
import UserInfo from './UserInfo'; // Asumsi file ada di folder yang sama
import DeviceLocation from './DeviceLocation'; // Asumsi file ada di folder yang sama

function App() {
  // Data pengguna dan perangkat (bisa diganti dengan data dinamis dari API)
  const userData = { name: 'Eko' };
  const deviceData = { name: 'Smartwatch V.1' };

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <h1>Dashboard Kesehatan Personal 🩺</h1>
      </header>
      
      <div className="dashboard-content">
        {/* Fitur 2: Nama Pengguna & Perangkat */}
        <section className="info-section">
          <UserInfo user={userData} device={deviceData} />
        </section>

        {/* Fitur 1: Detak Jantung dengan radial bergelombang */}
        <section className="chart-section">
          <HeartRateChart />
        </section>

        {/* Fitur 3: Lokasi Perangkat */}
        <section className="location-section">
          <DeviceLocation />
        </section>
        
      </div>

      {/* Catatan: Kode Analisis Sinyal ECG dari App.js lo yang sebelumnya bisa 
          dibuat di komponen terpisah, misalnya `ECGAnalyzer.js` lalu dipanggil di sini 
          jika lo ingin tetap menampilkannya di dashboard. */}
    </div>
  );
}

export default App;