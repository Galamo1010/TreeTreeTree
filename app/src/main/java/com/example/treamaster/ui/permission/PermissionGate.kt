package com.example.treamaster.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.treamaster.R

/** What the rest of the app may use. Camera is guaranteed inside [PermissionGate]; location is optional. */
data class Permissions(val locationGranted: Boolean)

/**
 * Shows [content] once the camera permission is granted. Location is requested together with it but
 * is optional: if the user declines, [content] still runs with `locationGranted = false`.
 */
@Composable
fun PermissionGate(content: @Composable (Permissions) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraGranted by remember { mutableStateOf(context.isGranted(Manifest.permission.CAMERA)) }
    var locationGranted by remember { mutableStateOf(context.hasLocation()) }
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requested = true
        cameraGranted = context.isGranted(Manifest.permission.CAMERA)
        locationGranted = context.hasLocation()
    }

    // The user may flip permissions in system settings and come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = context.isGranted(Manifest.permission.CAMERA)
                locationGranted = context.hasLocation()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (cameraGranted) {
        content(Permissions(locationGranted))
        return
    }

    // After a denial with no rationale left, the system dialog will not show again.
    val permanentlyDenied = requested &&
        context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.perm_camera_why), modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.perm_location_why), modifier = Modifier.padding(top = 8.dp))
        if (permanentlyDenied) {
            Text(stringResource(R.string.perm_denied_permanently), modifier = Modifier.padding(top = 12.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text(stringResource(R.string.perm_open_settings)) }
        } else {
            Button(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasLocation() =
    isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
