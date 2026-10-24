package com.example.treamaster.ui.ar

import com.google.ar.core.Frame
import kotlinx.coroutines.suspendCancellableCoroutine

/** Lets a ViewModel read the live ARCore frame without holding the GL view. Bind it via [ArCameraView]. */
class ArController {
    @Volatile internal var renderer: ArRenderer? = null

    /**
     * Runs [block] on the GL thread with the next frame and the view size, and returns its result.
     * Returns null when no preview is bound. Suspends until a frame is drawn, so wrap it in a timeout.
     */
    suspend fun <T> withFrame(block: (frame: Frame, viewW: Int, viewH: Int) -> T): T? {
        val r = renderer ?: return null
        return suspendCancellableCoroutine { cont ->
            r.runOnNextFrame { frame, w, h -> cont.resumeWith(runCatching { block(frame, w, h) }) }
        }
    }
}
