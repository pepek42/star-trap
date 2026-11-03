package com.github.pepek42.asteroids.settings

import com.badlogic.gdx.Preferences

/** Minimal persistent key-value storage, so settings logic can be tested without libGDX. */
interface KeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String, default: String): String
    fun putBoolean(key: String, value: Boolean)
    fun putFloat(key: String, value: Float)
    fun putString(key: String, value: String)
    fun flush()
}

/** [KeyValueStore] over libGDX [Preferences]. */
class GdxPreferencesStore(private val prefs: Preferences) : KeyValueStore {
    override fun contains(key: String) = prefs.contains(key)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)
    override fun getString(key: String, default: String): String = prefs.getString(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    override fun putFloat(key: String, value: Float) {
        prefs.putFloat(key, value)
    }

    override fun putString(key: String, value: String) {
        prefs.putString(key, value)
    }

    override fun flush() = prefs.flush()
}

/** In-memory [KeyValueStore] for tests and screenshot runs; nothing is persisted. */
class MemoryKeyValueStore : KeyValueStore {
    private val map = HashMap<String, Any>()
    override fun contains(key: String) = map.containsKey(key)
    override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
    override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
    override fun getString(key: String, default: String) = map[key] as? String ?: default
    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    override fun putFloat(key: String, value: Float) {
        map[key] = value
    }

    override fun putString(key: String, value: String) {
        map[key] = value
    }

    override fun flush() = Unit
}
