package com.capstone.mobilemeasure.data

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One measurement run. Owns a CSV file inside the app's external files dir
 * (visible via adb pull /sdcard/Android/data/<pkg>/files/sessions/).
 */
class MeasurementSession private constructor(
    val sessionId: String,
    val startedAtMs: Long,
    private val writer: BufferedWriter,
    val file: File,
) {

    @Volatile
    private var sampleCount: Int = 0
    val totalSamples: Int get() = sampleCount

    @Synchronized
    fun append(sample: RssiSample) {
        writer.write(sample.toCsvRow())
        writer.newLine()
        writer.flush()
        sampleCount += 1
    }

    @Synchronized
    fun close() {
        try {
            writer.flush()
            writer.close()
        } catch (_: Exception) {
            // best effort
        }
    }

    companion object {
        private val DIR_NAME = "sessions"
        private val FILE_DATE_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        fun start(context: Context): MeasurementSession {
            val now = System.currentTimeMillis()
            val baseDir = File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }
            val stamp = FILE_DATE_FMT.format(Date(now))
            val sessionId = "session_$stamp"
            val file = File(baseDir, "$sessionId.csv")

            val writer = BufferedWriter(FileWriter(file, /* append = */ false))
            writer.write(RssiSample.CSV_HEADER)
            writer.newLine()
            writer.flush()

            return MeasurementSession(
                sessionId = sessionId,
                startedAtMs = now,
                writer = writer,
                file = file,
            )
        }
    }
}
