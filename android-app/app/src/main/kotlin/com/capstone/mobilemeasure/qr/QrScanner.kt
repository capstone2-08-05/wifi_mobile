package com.capstone.mobilemeasure.qr

import android.app.Activity
import android.util.Log
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google Play Services Code Scanner 래퍼.
 *
 * 자체 카메라 Activity를 띄우므로 CAMERA 권한을 별도로 요구하지 않는다.
 *
 * 주의: `play-services-code-scanner`는 첫 호출 시 Play Services에서 barcode UI 모듈을
 * 다이내믹으로 받아오는데, 다운로드가 끝나기 전에 startScan을 부르면 delegate Activity가
 * 즉시 닫히면서 실패한다. 그래서 [ModuleInstall]로 먼저 설치를 보장한 뒤 스캔한다.
 */
object QrScanner {

    private const val TAG = "QrScanner"

    fun start(
        activity: Activity,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onCanceled: () -> Unit = {},
        onInstallProgress: (String) -> Unit = {},
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(activity, options)

        val moduleInstall = ModuleInstall.getClient(activity)
        val request = ModuleInstallRequest.newBuilder()
            .addApi(scanner)
            .build()

        onInstallProgress("스캐너 모듈 준비 중…")
        moduleInstall.installModules(request)
            .addOnSuccessListener {
                Log.d(TAG, "module install ok: alreadyInstalled=${it.areModulesAlreadyInstalled()}")
                runScan(scanner, onResult, onError, onCanceled)
            }
            .addOnFailureListener { e ->
                val msg = "스캐너 모듈 설치 실패: ${e.javaClass.simpleName} ${e.message}"
                Log.e(TAG, msg, e)
                onError(msg)
            }
    }

    private fun runScan(
        scanner: com.google.mlkit.vision.codescanner.GmsBarcodeScanner,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onCanceled: () -> Unit,
    ) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw.isNullOrBlank()) {
                    onError("QR 값 없음")
                } else {
                    onResult(raw)
                }
            }
            .addOnCanceledListener { onCanceled() }
            .addOnFailureListener { e ->
                val cause = e.cause?.let { " cause=${it.javaClass.simpleName}:${it.message}" } ?: ""
                val msg = "${e.javaClass.simpleName}: ${e.message}$cause"
                Log.e(TAG, "startScan failed: $msg", e)
                onError(msg)
            }
    }
}
