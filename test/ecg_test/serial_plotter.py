import serial
import serial.tools.list_ports
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
import collections
import numpy as np

# --- Konfigurasi Serial Port ---
# PENTING: Ganti nilai di bawah ini dengan port serial ESP32 kamu!
# Contoh di Windows: 'COM3'
# Contoh di macOS/Linux: '/dev/ttyUSB0' atau '/dev/cu.SLAB_USBtoUART'
port_name = 'COM3'

try:
    ser = serial.Serial(port_name, 115200, timeout=1)
    print(f"Terhubung ke port {port_name}. Menunggu data...")
except serial.SerialException as e:
    print(f"Error saat membuka port serial '{port_name}': {e}")
    print("Pastikan ESP32 terhubung dan nama port sudah benar.")
    exit()

# --- Konfigurasi Plotting ---
# Ukuran buffer untuk menyimpan data yang akan di-plot
buffer_size = 500
data_raw = collections.deque(maxlen=buffer_size)

plt.style.use('seaborn-v0_8-whitegrid')
fig, ax = plt.subplots(figsize=(12, 6))
line, = ax.plot([], [], color='cornflowerblue', linewidth=1)

ax.set_title("Sinyal ECG Real-time dari ESP32", fontsize=16)
ax.set_xlabel("Waktu (sampel)", fontsize=12)
ax.set_ylabel("Nilai ADC", fontsize=12)
ax.set_ylim(0, 4095) # Nilai ADC 12-bit di ESP32
ax.set_xlim(0, buffer_size)

# --- Fungsi untuk membaca data serial ---
def read_serial_data():
    """Membaca data dari port serial dan melakukan parsing."""
    while ser.in_waiting > 0:
        line_data = ser.readline().decode('utf-8').strip()
        # Periksa apakah string mengandung "Raw ECG:" sebelum mencoba parsing
        if "Raw ECG:" in line_data:
            try:
                raw_value_str = line_data.split("Raw ECG:")[1].strip()
                raw_value = int(raw_value_str)
                return raw_value
            except (ValueError, IndexError) as e:
                # Log pesan error untuk debugging, tapi tidak menghentikan program
                print(f"Gagal parsing data: {line_data} - Error: {e}")
    return None

# --- Fungsi pembaruan plot yang dipanggil oleh FuncAnimation ---
def update_plot(frame):
    """
    Fungsi ini dipanggil secara berulang oleh FuncAnimation.
    Membaca data baru dan memperbarui grafik.
    """
    new_data = read_serial_data()
    if new_data is not None:
        data_raw.append(new_data)
    
    # Perbarui data pada grafik
    line.set_ydata(list(data_raw))
    line.set_xdata(np.arange(len(data_raw)))
    
    return line,

# --- Main loop ---
try:
    ani = FuncAnimation(fig, update_plot, interval=50, blit=True) # interval dalam milidetik
    plt.show()

except KeyboardInterrupt:
    print("Program dihentikan oleh pengguna.")
finally:
    ser.close()
    print("Koneksi serial ditutup.")
