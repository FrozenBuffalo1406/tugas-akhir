import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { Container, Box, Typography, TextField, Button, Alert } from '@mui/material';

function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      await login(email, password);
      navigate('/'); // Arahkan ke dashboard setelah login
    } catch (err) {
      setError(err.response?.data?.error || 'Login gagal');
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      const response = await register(email, password);
      setMessage(response.data.message || 'Registrasi sukses! Silakan login.');
    } catch (err) {
      setError(err.response?.data?.error || 'Registrasi gagal');
    }
  };

  return (
    <Container component="main" maxWidth="xs">
      <Box sx={{ marginTop: 8, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Typography component="h1" variant="h5">
          Admin/Kerabat Login
        </Typography>
        <Box component="form" onSubmit={handleLogin} noValidate sx={{ mt: 1 }}>
          {error && <Alert severity="error" sx={{ width: '100%' }}>{error}</Alert>}
          {message && <Alert severity="success" sx={{ width: '100%' }}>{message}</Alert>}
          <TextField
            margin="normal" required fullWidth id="email" label="Email Address" name="email"
            autoComplete="email" autoFocus value={email} onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            margin="normal" required fullWidth name="password" label="Password" type="password"
            id="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" fullWidth variant="contained" sx={{ mt: 3, mb: 1 }}>
            Login
          </Button>
          <Button type="button" fullWidth variant="outlined" sx={{ mb: 2 }} onClick={handleRegister}>
            Register Akun Kerabat
          </Button>
        </Box>
      </Box>
    </Container>
  );
}

export default LoginPage;

