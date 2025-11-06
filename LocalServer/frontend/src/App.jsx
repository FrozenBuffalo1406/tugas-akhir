import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';

// Komponen buat ngelindungin halaman dashboard
function ProtectedRoute({ children }) {
  const { token } = useAuth();
  const location = useLocation();

  if (!token) {
    // Kalo belom login, tendang ke halaman login
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return children;
}

// Komponen buat halaman login (biar nggak bisa diakses kalo udah login)
function PublicRoute({ children }) {
  const { token } = useAuth();
  if (token) {
    // Kalo udah login, tendang ke dashboard
    return <Navigate to="/" replace />;
  }
  return children;
}

function App() {
  return (
    // Bungkus semua pake AuthProvider
    <AuthProvider>
      {/* Bungkus semua pake BrowserRouter */}
      <BrowserRouter>
        <Routes>
          {/* Rute buat Halaman Login */}
          <Route
            path="/login"
            element={
              <PublicRoute>
                <LoginPage />
              </PublicRoute>
            }
          />
          {/* Rute buat Halaman Dashboard Utama */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          {/* Kalo ada yang nyasar, balikin ke halaman utama */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;

