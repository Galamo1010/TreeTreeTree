package com.example.treamaster.data

import android.content.Context
import java.util.UUID

/** Random per-install id, so the cloud can tell phones apart without a login. */
object DeviceId {
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }
}
