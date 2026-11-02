package com.example.treamaster.ui.ar

import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Display
import com.example.treamaster.geometry.Vec3
import com.example.treamaster.measure.Mark
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
    /** Points to draw markers at, in world space. Replace the whole list to update. */
    @Volatile var marks: List<Mark> = emptyList()

    private val background = BackgroundRenderer()
    private val markers = MarkerRenderer()
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
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
        markers.createOnGlThread()
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
        if (tracking) {
            val camera = frame.camera
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projMatrix, 0, NEAR, FAR)
            val p = camera.pose
            markers.draw(marks, viewMatrix, projMatrix, Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()))
        }
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

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
    }
}
