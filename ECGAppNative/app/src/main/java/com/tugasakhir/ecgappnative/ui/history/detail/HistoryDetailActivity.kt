package com.tugasakhir.ecgappnative.ui.history.detail

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.model.HistoryDetailResponse
import com.tugasakhir.ecgappnative.databinding.ActivityHistoryDetailBinding
import com.tugasakhir.ecgappnative.ui.BaseActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HistoryDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: HistoryDetailViewModel by viewModels { factory }


    companion object {
        const val EXTRA_READING_ID = "EXTRA_READING_ID"
        // Opsional: Bawa data ini biar UI gak kosong pas loading
        const val EXTRA_PREDICTION = "EXTRA_PREDICTION"
        const val EXTRA_TIMESTAMP = "EXTRA_TIMESTAMP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()

        // Ambil data dari Intent
        val readingId = intent.getIntExtra(EXTRA_READING_ID, -1)
        val prediction = intent.getStringExtra(EXTRA_PREDICTION)
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP)

        // Tampilkan data "sementara" biar gak kosong
        if (prediction != null && timestamp != null) {
            binding.tvClassification.text = prediction
            binding.tvTimestamp.text = formatTimestamp(timestamp)
        }

        if (readingId != -1) {
            viewModel.loadDetail(readingId)
        } else {
            showToast("Error: Reading ID tidak valid")
            finish()
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish() // Tutup activity ini
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
        }

        viewModel.toastMessage.observe(this) {
            showToast(it)
        }

        viewModel.detailData.observe(this) { data ->
            updateUi(data)
            setupChart(data.ecgData)
        }
    }

    private fun updateUi(data: HistoryDetailResponse) {
        binding.tvClassification.text = data.classification
        binding.tvTimestamp.text = formatTimestamp(data.timestamp)
        binding.tvHeartRate.text = if(data.heartRate != null) {
            "${data.heartRate.toInt()} BPM"
        } else {
            "N/A"
        }
    }

    private fun setupChart(ecgData: List<Float>) {
        if (ecgData.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.invalidate()
            return
        }

        // 1. Ubah List<Float> jadi List<Entry>
        val entries = ArrayList<Entry>()
        ecgData.forEachIndexed { index, value ->
            entries.add(Entry(index.toFloat(), value))
        }

        // 2. Buat DataSet
        val dataSet = LineDataSet(entries, "ECG Signal").apply {
            color = Color.RED
            lineWidth = 1.5f
            setDrawValues(false)
            setDrawCircles(false)
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.LINEAR

            // Biar mulus, ilangin highlight pas diklik (opsional)
            isHighlightEnabled = false
        }

        // 4. Masukin ke LineData
        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.description.isEnabled = false // Hapus deskripsi
        binding.lineChart.legend.isEnabled = false // Hapus legenda

        binding.lineChart.xAxis.apply {
            isEnabled = true // Hidupin lagi
            position = XAxis.XAxisPosition.BOTTOM // Taro di bawah
            setDrawGridLines(true) // Kasih grid biar gampang baca waktunya
            gridColor = Color.LTGRAY // Grid abu-abu tipis
            textColor = Color.DKGRAY

            // Format angka jadi "1.5s"
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1fs", value)
                }
            }
        }
        // Styling Sumbu Y (Kiri & Kanan)
        binding.lineChart.axisLeft.isEnabled = true
        binding.lineChart.axisLeft.setDrawGridLines(false)
        binding.lineChart.axisRight.isEnabled = false

        binding.lineChart.isDragEnabled = true
        binding.lineChart.setScaleEnabled(true)
        binding.lineChart.setPinchZoom(true)

        // Refresh grafiknya
        binding.lineChart.invalidate()
    }

    private fun formatTimestamp(isoTimestamp: String): String {
        return try {
            val inputFormatter = DateTimeFormatter.ISO_DATE_TIME
            @Suppress("DEPRECATION")
            val outputFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale("id", "ID"))
            val dateTime = LocalDateTime.parse(isoTimestamp, inputFormatter)
            dateTime.format(outputFormatter)
        } catch (_: Exception) {
            isoTimestamp // Kalo gagal parsing, balikin apa adanya
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}