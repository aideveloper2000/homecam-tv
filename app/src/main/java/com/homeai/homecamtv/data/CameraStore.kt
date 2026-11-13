package com.homeai.homecamtv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists the camera list as JSON in SharedPreferences.
 *
 * Deliberately simple: the list is small, and SharedPreferences keeps the
 * camera definitions readable/seedable with `run-as` during testing.
 */
class CameraStore private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): MutableList<Camera> {
        val json = prefs.getString(KEY_CAMERAS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Camera>>() {}.type
            gson.fromJson<MutableList<Camera>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(cameras: List<Camera>) {
        prefs.edit().putString(KEY_CAMERAS, gson.toJson(cameras)).apply()
    }

    fun get(id: String): Camera? = load().firstOrNull { it.id == id }

    fun upsert(camera: Camera) {
        val cameras = load()
        val index = cameras.indexOfFirst { it.id == camera.id }
        if (index >= 0) cameras[index] = camera else cameras.add(camera)
        save(cameras)
    }

    fun delete(id: String) {
        val cameras = load()
        cameras.removeAll { it.id == id }
        save(cameras)
    }

    fun groups(): List<String> =
        load().map { it.group.ifBlank { Camera.GROUP_DEFAULT } }.distinct().sorted()

    companion object {
        private const val PREFS_NAME = "homecam_cameras"
        private const val KEY_CAMERAS = "cameras"

        @Volatile
        private var instance: CameraStore? = null

        fun get(context: Context): CameraStore =
            instance ?: synchronized(this) {
                instance ?: CameraStore(context).also { instance = it }
            }
    }
}
