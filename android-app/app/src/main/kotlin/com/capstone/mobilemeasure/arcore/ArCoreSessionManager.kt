package com.capstone.mobilemeasure.arcore

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ARCore Session 라이프사이클을 들고 GLSurfaceView 렌더 루프 안에서 frame을 pump하는 헬퍼.
 *
 * 사용 흐름:
 *   1. installRequested() → 필요 시 ARCore APK 설치 유도. 사용자가 동의하면 onResume에서 다시 호출.
 *   2. resume(activity, surfaceView) → Session 생성, surfaceView를 렌더 attached.
 *   3. pause() → Session pause.
 *   4. close() → Session close.
 *
 * 각 frame마다 latestPose에 ArPoseSnapshot이 발행된다.
 */
class ArCoreSessionManager(
    private val onError: (String) -> Unit = {},
) {

    private var session: Session? = null
    private var requestedInstall = true

    private val backgroundRenderer = BackgroundRenderer()
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private var displayRotation: Int = 0

    private val _latestPose = MutableStateFlow<ArPoseSnapshot?>(null)
    val latestPose: StateFlow<ArPoseSnapshot?> = _latestPose.asStateFlow()

    private val _availability = MutableStateFlow(Availability.UNKNOWN)
    val availability: StateFlow<Availability> = _availability.asStateFlow()

    enum class Availability { UNKNOWN, SUPPORTED, UNSUPPORTED, INSTALL_REQUESTED }

    fun checkAvailability(context: Context) {
        val avail = ArCoreApk.getInstance().checkAvailability(context)
        _availability.value = when {
            avail.isSupported -> Availability.SUPPORTED
            avail.isUnknown -> Availability.UNKNOWN
            else -> Availability.UNSUPPORTED
        }
    }

    /**
     * Activity onResume 시에 호출. ARCore APK가 없으면 한 번 install dialog를 띄우고
     * INSTALL_REQUESTED를 반환한다(이때 다음 onResume에서 다시 호출되어야 한다).
     */
    fun setDisplayRotation(rotation: Int) {
        this.displayRotation = rotation
    }

    fun resume(activity: Activity, surfaceView: GLSurfaceView): Result<Unit> {
        return try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(activity, requestedInstall)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        requestedInstall = false
                        _availability.value = Availability.INSTALL_REQUESTED
                        return Result.success(Unit)
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }

                val newSession = Session(activity)
                val config = Config(newSession).apply {
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                newSession.configure(config)
                session = newSession
                _availability.value = Availability.SUPPORTED
            }

            session?.resume()
            surfaceView.onResume()
            Result.success(Unit)
        } catch (e: UnavailableException) {
            _availability.value = Availability.UNSUPPORTED
            val msg = "ARCore unavailable: ${e.javaClass.simpleName} ${e.message}"
            Log.e(TAG, msg, e)
            onError(msg)
            Result.failure(e)
        } catch (e: CameraNotAvailableException) {
            val msg = "Camera unavailable: ${e.message}"
            Log.e(TAG, msg, e)
            onError(msg)
            Result.failure(e)
        } catch (e: SecurityException) {
            val msg = "Camera permission missing: ${e.message}"
            Log.e(TAG, msg, e)
            onError(msg)
            Result.failure(e)
        }
    }

    fun pause(surfaceView: GLSurfaceView?) {
        surfaceView?.onPause()
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
        _latestPose.value = null
    }

    fun onSurfaceChanged(width: Int, height: Int, displayRotation: Int) {
        viewportWidth = width
        viewportHeight = height
        this.displayRotation = displayRotation
        GLES20.glViewport(0, 0, width, height)
    }

    val renderer: GLSurfaceView.Renderer = object : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            backgroundRenderer.createOnGlThread()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            viewportWidth = width
            viewportHeight = height
            session?.setDisplayGeometry(displayRotation, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val sess = session ?: return
            try {
                sess.setCameraTextureName(backgroundRenderer.textureId)
                if (viewportWidth > 0 && viewportHeight > 0) {
                    sess.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
                }
                val frame: Frame = sess.update()
                backgroundRenderer.draw(frame)

                val cam = frame.camera
                val pose = cam.pose
                val q = FloatArray(4)
                val t = FloatArray(3)
                pose.getRotationQuaternion(q, 0)
                pose.getTranslation(t, 0)
                _latestPose.value = ArPoseSnapshot(
                    tx = t[0], ty = t[1], tz = t[2],
                    qx = q[0], qy = q[1], qz = q[2], qw = q[3],
                    trackingState = cam.trackingState.name,
                    timestampNs = frame.timestamp,
                )
            } catch (e: Throwable) {
                Log.w(TAG, "draw frame failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ArCoreSessionManager"
    }
}
