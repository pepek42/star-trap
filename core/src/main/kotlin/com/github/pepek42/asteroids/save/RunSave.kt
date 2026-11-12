package com.github.pepek42.asteroids.save

import com.github.pepek42.asteroids.FIRST_LEVEL
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.settings.SettingSpec
import com.github.pepek42.asteroids.settings.loadSettings
import com.github.pepek42.asteroids.settings.saveSettings

/** Key prefix of the single run-save slot inside the preferences store. */
const val RUN_SAVE_PREFIX = "runsave."

/** Format version of the run save; a save with another version is ignored. Bump when the layout changes. */
const val RUN_SAVE_VERSION = 2

private const val KEY_VERSION = "version"
private const val KEY_NEXT_LEVEL = "next_level"

/** [KeyValueStore] view that prepends [prefix] to every key; keeps the save apart from the global settings. */
class PrefixedStore(private val inner: KeyValueStore, private val prefix: String) : KeyValueStore {
    override fun contains(key: String) = inner.contains(prefix + key)
    override fun getBoolean(key: String, default: Boolean) = inner.getBoolean(prefix + key, default)
    override fun getFloat(key: String, default: Float) = inner.getFloat(prefix + key, default)
    override fun getString(key: String, default: String) = inner.getString(prefix + key, default)
    override fun putBoolean(key: String, value: Boolean) = inner.putBoolean(prefix + key, value)
    override fun putFloat(key: String, value: Float) = inner.putFloat(prefix + key, value)
    override fun putString(key: String, value: String) = inner.putString(prefix + key, value)
    override fun flush() = inner.flush()
}

/**
 * What a run needs to resume. [nextLevel] is the level to play next; the run physics are stored beside it as
 * [SettingSpec] values (see [writeRunSave]).
 */
data class RunSave(val nextLevel: Int)

/** Reads the save, or null when there is none, it has another version, or it is corrupt. */
fun readRunSave(store: KeyValueStore): RunSave? {
    val s = PrefixedStore(store, RUN_SAVE_PREFIX)
    if (!s.contains(KEY_VERSION) || s.getFloat(KEY_VERSION, 0f).toInt() != RUN_SAVE_VERSION) return null
    val level = s.getFloat(KEY_NEXT_LEVEL, 0f).toInt()
    if (level < FIRST_LEVEL) return null
    return RunSave(level)
}

/** Writes the save (next level plus run physics from [runSpecs]) and flushes. */
fun writeRunSave(store: KeyValueStore, nextLevel: Int, runSpecs: List<SettingSpec>) {
    val s = PrefixedStore(store, RUN_SAVE_PREFIX)
    s.putFloat(KEY_VERSION, RUN_SAVE_VERSION.toFloat())
    s.putFloat(KEY_NEXT_LEVEL, nextLevel.toFloat())
    saveSettings(s, runSpecs)
}

/** Saves after a cleared level of a normal run; scenarios never save. Returns whether it wrote. */
fun saveAfterLevel(store: KeyValueStore, state: GameState, runSpecs: List<SettingSpec>): Boolean {
    if (state.scenario != null) return false
    writeRunSave(store, state.level + 1, runSpecs)
    return true
}

/** Applies the saved run physics through [runSpecs] (does not persist them as New-game defaults). */
fun applyRunSave(store: KeyValueStore, runSpecs: List<SettingSpec>) =
    loadSettings(PrefixedStore(store, RUN_SAVE_PREFIX), runSpecs)

/** Deletes the save (the run is over). Marked with version 0, since the store has no remove. */
fun clearRunSave(store: KeyValueStore) {
    val s = PrefixedStore(store, RUN_SAVE_PREFIX)
    s.putFloat(KEY_VERSION, 0f)
    s.putFloat(KEY_NEXT_LEVEL, 0f)
    s.flush()
}
