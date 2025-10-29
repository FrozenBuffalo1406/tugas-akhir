import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
// Ini buat bikin ikon marker defaultnya muncul
import L from 'leaflet';
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.7.1/dist/images/marker-shadow.png'
});

const DeviceLocation = () => {
  const [position, setPosition] = useState(null);

  useEffect(() => {
    // Cek browser support geolocation
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setPosition([pos.coords.latitude, pos.coords.longitude]);
        },
        (error) => {
          console.error("Error getting location:", error);
          alert('Gagal mendapatkan lokasi. Pastikan izin lokasi diaktifkan.');
        }
      );
    } else {
      alert('Browser lo ga support Geolocation API.');
    }
  }, []);

  if (!position) {
    return <div>Sedang mengambil lokasi...</div>;
  }

  return (
    <div style={{ height: '400px', width: '100%' }}>
      <h2 style={{ textAlign: 'center' }}>Lokasi Perangkat</h2>
      <MapContainer center={position} zoom={13} style={{ height: '100%', width: '100%' }}>
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
        />
        <Marker position={position}>
          <Popup>
            Lokasi lo sekarang!
          </Popup>
        </Marker>
      </MapContainer>
    </div>
  );
};

export default DeviceLocation;