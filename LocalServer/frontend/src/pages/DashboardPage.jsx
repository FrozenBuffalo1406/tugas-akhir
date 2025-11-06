import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import { getMyDevices, getReadingsForDevice, getReadingById } from '../api/apiService.js';
import { connectSocket, getSocket, disconnectSocket } from '../api/socketService.js';
import {
  Box, AppBar, Toolbar, Typography, Drawer, List, ListItem, ListItemButton,
  ListItemText, Button, Paper, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, CircularProgress
} from '@mui/material';
import EcgChart from '../components/EcgChart.jsx';

const drawerWidth = 240;

function DashboardPage() {
  const { logout } = useAuth();
  const [devices, setDevices] = useState([]);
  const [selectedDevice, setSelectedDevice] = useState(null);
  const [readings, setReadings] = useState([]);
  const [selectedReading, setSelectedReading] = useState(null); // Buat nampilin grafik
  const [loading, setLoading] = useState(false);

  // Setup Socket.IO
  useEffect(() => {
    connectSocket();
    const socket = getSocket();

    // Dengerin 'event' dari server
    // Pastiin nama event-nya 'new_ecg_data' (sesuai di app.py)
    socket.on('new_ecg_data', (newReading) => {
      console.log('DAPET DATA BARU REALTIME:', newReading);
      
      // Cek apakah data baru ini untuk device yang lagi kita liat
      // PENTING: Pastiin backend ngirim 'device_id_str' di event-nya
      if (selectedDevice && newReading.device_id_str === selectedDevice.device_id_str) {
        // Kalo iya, tambahin datanya ke paling atas tabel
        setReadings(prevReadings => [newReading, ...prevReadings]);
      }
    });

    // Cleanup pas komponen di-unmount
    return () => {
      disconnectSocket();
    };
  }, [selectedDevice]); // Kita refresh listener-nya tiap ganti device

  // Ambil daftar device
  useEffect(() => {
    getMyDevices()
      .then(response => {
        setDevices(response.data);
        if (response.data.length > 0) {
          handleSelectDevice(response.data[0]);
        }
      })
      .catch(error => console.error("Error fetching devices:", error));
  }, []);

  // Fungsi buat ngambil riwayat data
  const handleSelectDevice = (device) => {
    setSelectedDevice(device);
    setSelectedReading(null); // Reset grafik
    setLoading(true);

    getReadingsForDevice(device.device_id_str)
      .then(response => {
        setReadings(response.data);
        setLoading(false);
      })
      .catch(error => {
        console.error("Error fetching readings:", error);
        setLoading(false);
      });
  };

  // Fungsi buat ngambil data grafik (pas baris tabel diklik)
  const handleSelectReading = (readingId) => {
    getReadingById(readingId)
      .then(response => {
        setSelectedReading({
          id: response.data.id,
          // Ambil data sinyal dari 'processed_ecg_data' (sesuai backend)
          data: response.data.processed_ecg_data 
        });
      })
      .catch(error => console.error("Error fetching full reading data:", error));
  };

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ justifyContent: 'space-between' }}>
          <Typography variant="h6" noWrap component="div">
            ECG Dashboard
          </Typography>
          <Button color="inherit" onClick={logout}>Logout</Button>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth, flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto' }}>
          <List>
            <Typography variant="overline" sx={{ pl: 2 }}>Device Pasien</Typography>
            {devices.map((device) => (
              <ListItem key={device.id} disablePadding>
                <ListItemButton
                  selected={selectedDevice?.id === device.id}
                  onClick={() => handleSelectDevice(device)}
                >
                  <ListItemText primary={device.device_id_str} secondary={device.user_id} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        {!selectedDevice ? (
          <Typography>Pilih device dari sidebar untuk melihat data.</Typography>
        ) : (
          <Paper>
            <Typography variant="h5" sx={{ p: 2 }}>
              Menampilkan data untuk: {selectedDevice.device_id_str}
            </Typography>
            
            {/* Tampilkan Grafik kalo ada data */}
            {selectedReading && (
              <Box sx={{ p: 2, borderBottom: '1px solid #eee' }}>
                <Typography variant="h6">Grafik Sinyal (ID: {selectedReading.id})</Typography>
                <EcgChart data={selectedReading.data} dataKey="value" />
              </Box>
            )}

            {/* Tabel Riwayat */}
            <TableContainer>
              {loading && <CircularProgress sx={{ m: 2 }} />}
              <Table stickyHeader size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>ID</TableCell>
                    <TableCell>Timestamp</TableCell>
                    <TableCell>Hasil Prediksi</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {readings.map((row) => (
                    <TableRow 
                      key={row.id} 
                      hover 
                      onClick={() => handleSelectReading(row.id)}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>{row.id}</TableCell>
                      <TableCell>{new Date(row.timestamp).toLocaleString()}</TableCell>
                      <TableCell>{row.prediction}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        )}
      </Box>
    </Box>
  );
}

export default DashboardPage;

