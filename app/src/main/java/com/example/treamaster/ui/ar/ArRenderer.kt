package com.example.treamaster.ui.ar

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Display
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Drives the ARCore frame loop on the GL thread and draws the camera preview.
 * [session] may be null until AR is ready; the renderer just clears the screen until then.
 */
class ArRenderer(
    private val context: Context,
    private val onTrackingChanged: (Boolean) -> Unit,
    private val onCameraError: (Throwable) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null

    private val background = BackgroundRenderer()
    private var viewportChanged = false
    private var width = 0
    private var height = 0
    private var lastTracking: Boolean? = null
    private val pending = ConcurrentLinkedQueue<(Frame, Int, Int) -> Unit>()

    /** Runs [block] on the GL thread with the next frame and the view size in pixels. */
    fun runOnNextFrame(block: (frame: Frame, viewW: Int, viewH: Int) -> Unit) {
        pending.add(block)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return

        session.setCameraTextureName(background.textureId)
        if (viewportChanged) {
            session.setDisplayGeometry(displayRotation(), width, height)
            viewportChanged = false
        }

        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            onCameraError(e)
            return
        }
        background.draw(frame)
        // Hit tests must use the current frame, so taps are queued and served here.
        while (true) pending.poll()?.invoke(frame, width, height) ?: break

        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        if (tracking != lastTracking) {
            lastTracking = tracking
            onTrackingChanged(tracking)
        }
    }

    /** Call when the session is replaced so the next frame re-applies display geometry. */
    fun invalidateViewport() {
        viewportChanged = true
    }

    private fun displayRotation(): Int =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY).rotation
}
