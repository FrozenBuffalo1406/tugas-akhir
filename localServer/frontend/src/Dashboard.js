import React from 'react';
import HeartRateChart from './HeartRateChart';
import UserInfo from './UserInfo';
import DeviceLocation from './DeviceLocation';
import './Dashboard.css'; // Catatan: bikin file CSS sendiri buat styling

const Dashboard = () => {
  // Data dummy buat UserInfo
  const userData = { name: 'Eko' };
  const deviceData = { name: 'Smartwatch V.1' };

  return (
    <div className="dashboard-container">
      <h1>Dashboard Kesehatan Pribadi 🩺</h1>
      
      <div className="dashboard-section">
        <UserInfo user={userData} device={deviceData} />
      </div>

      <div className="dashboard-section">
        <HeartRateChart />
      </div>

      <div className="dashboard-section">
        <DeviceLocation />
      </div>
    </div>
  );
};

export default Dashboard;