package com.example.treamaster.data

import android.content.Context
import com.example.treamaster.sync.SupabaseRest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A management region from the website; its [code] is the tree-number prefix ("A" in "A-3"). */
data class Region(val code: String, val name: String, val slotCount: Int)

/**
 * Active regions from the website's `management_regions`. The last list fetched is kept on the phone
 * so trees can still be numbered where there is no signal.
 */
class RegionStore private constructor(context: Context, private val rest: SupabaseRest) {
    private val prefs = context.getSharedPreferences("regions", Context.MODE_PRIVATE)
    private val _regions = MutableStateFlow(parse(prefs.getString(KEY, null) ?: "[]"))
    val regions: StateFlow<List<Region>> = _regions.asStateFlow()

    /** Returns false when the website could not be reached; the cached list is kept then. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!rest.isConfigured) return@withContext false
        val response = try {
            rest.request(
                "GET",
                "management_regions?select=code,name,slot_count&is_active=eq.true&order=display_order.asc,code.asc",
            )
        } catch (e: Exception) {
            return@withContext false
        }
        if (!response.isSuccess) return@withContext false
        val list = try {
            parse(response.body)
        } catch (e: Exception) {
            return@withContext false
        }
        prefs.edit().putString(KEY, response.body).apply()
        _regions.value = list
        true
    }

    companion object {
        private const val KEY = "list"
        @Volatile private var instance: RegionStore? = null

        fun get(context: Context): RegionStore = instance ?: synchronized(this) {
            instance ?: RegionStore(context.applicationContext, SupabaseRest()).also { instance = it }
        }

        private fun parse(json: String): List<Region> {
            val array = JSONArray(json)
            return (0 until array.length()).map { i ->
                val o: JSONObject = array.getJSONObject(i)
                Region(o.getString("code").uppercase(), o.getString("name"), o.optInt("slot_count", 20))
            }
        }
    }
}
