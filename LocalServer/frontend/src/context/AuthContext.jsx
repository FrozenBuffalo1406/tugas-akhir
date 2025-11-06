import React, { createContext, useState, useContext, useMemo, useEffect, useCallback } from 'react';
import { apiService } from '../api/apiService';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [token, setToken_] = useState(localStorage.getItem('jwt_token'));

  // 1. Kita stabilin fungsi setToken pake useCallback
  const setToken = useCallback((newToken) => {
    if (newToken) {
      localStorage.setItem('jwt_token', newToken);
    } else {
      localStorage.removeItem('jwt_token');
    }
    setToken_(newToken);
  }, []); // <-- Array kosong, fungsi ini nggak pernah berubah

  // Efek buat update header Axios (bergantung sama token)
  useEffect(() => {
    if (token) {
      apiService.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    } else {
      delete apiService.defaults.headers.common['Authorization'];
    }
  }, [token]);

  // 2. Kita stabilin fungsi login (bergantung sama setToken)
  const login = useCallback(async (email, password) => {
    const response = await apiService.post('/api/auth/login', { email, password });
    setToken(response.data.access_token);
  }, [setToken]);

  // 3. Kita stabilin fungsi register (nggak bergantung sama apa-apa)
  const register = useCallback(async (email, password) => {
    return apiService.post('/api/auth/register', { email, password, role: 'kerabat' });
  }, []); // <-- Array kosong, fungsi ini nggak pernah berubah

  // 4. Kita stabilin fungsi logout (bergantung sama setToken)
  const logout = useCallback(() => {
    setToken(null);
  }, [setToken]);

  // 5. SEKARANG, kita bisa masukin semua ke 'daftar pantau' useMemo
  const authValue = useMemo(() => ({
    token,
    login,
    register,
    logout,
  }), [token, login, register, logout]); // <-- Sekarang udah lengkap

  return (
    <AuthContext.Provider value={authValue}>
      {children}
    </AuthContext.Provider>
  );
};

// Bikin custom hook biar gampang dipanggil di komponen lain
export const useAuth = () => {
  return useContext(AuthContext);
};