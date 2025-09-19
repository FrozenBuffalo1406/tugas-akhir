import React from 'react';

const UserInfo = ({ user, device }) => {
  return (
    <div style={{ textAlign: 'center', margin: '20px 0' }}>
      <h2>Halo, {user.name}!</h2>
      <p>Perangkat yang terhubung: <strong>{device.name}</strong></p>
    </div>
  );
};

export default UserInfo;