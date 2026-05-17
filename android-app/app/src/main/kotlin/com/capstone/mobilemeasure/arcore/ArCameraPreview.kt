package com.capstone.mobilemeasure.arcore

import android.app.Activity
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * GLSurfaceView를 Compose에 박아 ARCore 카메라 프리뷰를 그린다.
 * Activity 라이프사이클(onResume/onPause)에 맞춰 ArCoreSessionManager를 깨우고 재운다.
 */
@Composable
fun ArCameraPreview(
    sessionManager: ArCoreSessionManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val surfaceView = remember(context) {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(sessionManager.renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val activity = context.findActivity()
                    if (activity != null) {
                        sessionManager.checkAvailability(context)
                        sessionManager.setDisplayRotation(context.currentDisplayRotation())
                        sessionManager.resume(activity, surfaceView)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> sessionManager.pause(surfaceView)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sessionManager.pause(surfaceView)
        }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

internal fun Context.currentDisplayRotation(): Int {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @Suppress("DEPRECATION")
    return wm.defaultDisplay?.rotation ?: Surface.ROTATION_0
}
