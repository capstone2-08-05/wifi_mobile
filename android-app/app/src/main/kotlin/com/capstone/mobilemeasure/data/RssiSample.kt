package com.capstone.mobilemeasure.data

data class RssiSample(
    val timestampMs: Long,
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val isConnected: Boolean = false,
) {
    fun toCsvRow(): String =
        "$timestampMs,${escape(ssid)},$bssid,$rssi,$frequencyMhz"

    companion object {
        const val CSV_HEADER = "timestampMs,ssid,bssid,rssi,frequencyMhz"

        private fun escape(value: String): String {
            val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
            val escaped = value.replace("\"", "\"\"")
            return if (needsQuote) "\"$escaped\"" else escaped
        }
    }
}
