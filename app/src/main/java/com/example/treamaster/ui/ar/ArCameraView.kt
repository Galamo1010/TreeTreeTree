package com.example.treamaster.ui.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.treamaster.R
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

private sealed interface ArStatus {
    data object Starting : ArStatus
    data object Running : ArStatus
    data class Failed(val messageRes: Int, val detail: String? = null) : ArStatus
}

/**
 * Full-screen AR camera preview. Owns the ARCore [Session] and ties it to the lifecycle.
 * [onTrackingChanged] is called with true once ARCore is tracking the environment.
 */
@Composable
fun ArCameraView(
    modifier: Modifier = Modifier,
    controller: ArController? = null,
    onTrackingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf<ArStatus>(ArStatus.Starting) }
    var retryCount by remember { mutableIntStateOf(0) }

    val renderer = remember {
        ArRenderer(
            context = context,
            onTrackingChanged = onTrackingChanged,
            onCameraError = { status = ArStatus.Failed(R.string.ar_error_camera, it.message) },
        )
    }
    DisposableEffect(controller) {
        controller?.renderer = renderer
        onDispose { controller?.renderer = null }
    }
    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setWillNotDraw(false)
        }
    }

    DisposableEffect(lifecycleOwner, retryCount) {
        var session: Session? = null
        var installRequested = false

        fun startSession() {
            val activity = context.findActivity() ?: return
            try {
                if (session == null) {
                    val install = ArCoreApk.getInstance()
                        .requestInstall(activity, !installRequested)
                    if (install == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                        // Play Services for AR is being installed; onResume runs again afterwards.
                        installRequested = true
                        return
                    }
                    session = Session(activity).also { s ->
                        s.configure(s.config.apply {
                            if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                depthMode = Config.DepthMode.AUTOMATIC
                            }
                            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                            focusMode = Config.FocusMode.AUTO
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        })
                        renderer.session = s
                        renderer.invalidateViewport()
                    }
                }
                session?.resume()
                glView.onResume()
                status = ArStatus.Running
            } catch (e: UnavailableUserDeclinedInstallationException) {
                status = ArStatus.Failed(R.string.ar_error_declined)
            } catch (e: UnavailableDeviceNotCompatibleException) {
                status = ArStatus.Failed(R.string.ar_error_unsupported)
            } catch (e: UnavailableApkTooOldException) {
                status = ArStatus.Failed(R.string.ar_error_update_app)
            } catch (e: Exception) {
                status = ArStatus.Failed(R.string.ar_error_camera, e.message)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startSession()
                Lifecycle.Event.ON_PAUSE -> {
                    glView.onPause()
                    session?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
            renderer.session = null
            session?.close()
            session = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        when (val s = status) {
            ArStatus.Starting -> Text(
                text = stringResource(R.string.ar_starting),
                modifier = Modifier.align(Alignment.Center),
            )
            ArStatus.Running -> Unit
            is ArStatus.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (s.detail != null) stringResource(s.messageRes, s.detail)
                        else stringResource(s.messageRes),
                    )
                    Button(
                        onClick = { status = ArStatus.Starting; retryCount++ },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text(stringResource(R.string.ar_retry)) }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
