package com.example.keres9.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object SaveRepository {
    private const val PREFS_NAME = "keres9_prefs"
    private const val SAVE_KEY = "keres9_moon2035_v1"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveJson(json: String) {
        prefs?.edit()?.putString(SAVE_KEY, json)?.apply()
    }

    fun readSaveJson(): JSONObject? {
        val raw = prefs?.getString(SAVE_KEY, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            null
        }
    }

    fun hasSave(): Boolean = prefs?.contains(SAVE_KEY) == true

    fun clearSave() {
        prefs?.edit()?.remove(SAVE_KEY)?.apply()
    }
}
