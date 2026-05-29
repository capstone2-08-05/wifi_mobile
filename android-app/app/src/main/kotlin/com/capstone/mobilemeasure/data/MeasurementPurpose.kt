package com.capstone.mobilemeasure.data

enum class MeasurementPurpose(
    val wireValue: String,
    val title: String,
    val shortTitle: String,
    val description: String,
) {
    CALIBRATION(
        wireValue = "calibration",
        title = "보정용 측정",
        shortTitle = "보정용",
        description = "시뮬레이션 맵을 실제 공간에 맞게 보정하는 데 사용합니다.",
    ),
    VALIDATION(
        wireValue = "validation",
        title = "검증용 측정",
        shortTitle = "검증용",
        description = "보정에는 사용하지 않고, 보정 전/후 예측 오차 평가에만 사용합니다.",
    ),
    REFERENCE(
        wireValue = "reference",
        title = "참조맵용 측정",
        shortTitle = "참조맵용",
        description = "실측 기반 RSSI 참조맵을 만드는 데 사용합니다.",
    ),
    UNKNOWN(
        wireValue = "unknown",
        title = "목적 미지정",
        shortTitle = "미지정",
        description = "기존 데이터 호환을 위한 기본값입니다.",
    );

    companion object {
        fun fromWireValue(value: String?): MeasurementPurpose {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.wireValue == normalized } ?: CALIBRATION
        }
    }
}
