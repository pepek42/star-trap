package com.github.pepek42.asteroids.save

import com.github.pepek42.asteroids.FIRST_LEVEL
import com.github.pepek42.asteroids.GameState
import com.github.pepek42.asteroids.settings.KeyValueStore
import com.github.pepek42.asteroids.shop.RunUpgrades
import com.github.pepek42.asteroids.shop.ShopItem
import com.github.pepek42.asteroids.settings.SettingSpec
import com.github.pepek42.asteroids.settings.loadSettings
import com.github.pepek42.asteroids.settings.saveSettings

/** Key prefix of the single run-save slot inside the preferences store. */
const val RUN_SAVE_PREFIX = "runsave."

/** Format version of the run save; a save with another version is ignored. Bump when the layout changes. */
const val RUN_SAVE_VERSION = 1

private const val KEY_VERSION = "version"
private const val KEY_NEXT_LEVEL = "next_level"
private const val KEY_CREDITS = "credits"
private const val KEY_UPGRADE_PREFIX = "upgrade."

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
 * [SettingSpec] values (see [writeRunSave]). [credits] and the shop [upgrades] come with it (missing keys read as 0, so older saves still load).
 */
data class RunSave(val nextLevel: Int, val credits: Int = 0, val upgrades: Map<ShopItem, Int> = emptyMap())

/** Reads the save, or null when there is none, it has another version, or it is corrupt. */
fun readRunSave(store: KeyValueStore): RunSave? {
    val s = PrefixedStore(store, RUN_SAVE_PREFIX)
    if (!s.contains(KEY_VERSION) || s.getFloat(KEY_VERSION, 0f).toInt() != RUN_SAVE_VERSION) return null
    val level = s.getFloat(KEY_NEXT_LEVEL, 0f).toInt()
    if (level < FIRST_LEVEL) return null
    val upgrades = ShopItem.entries.associateWith { s.getFloat(KEY_UPGRADE_PREFIX + it.name, 0f).toInt() }.filterValues { it > 0 }
    return RunSave(level, s.getFloat(KEY_CREDITS, 0f).toInt().coerceAtLeast(0), upgrades)
}

/** Writes the save (next level plus run physics from [runSpecs]) and flushes. */
fun writeRunSave(
    store: KeyValueStore,
    nextLevel: Int,
    runSpecs: List<SettingSpec>,
    credits: Int = 0,
    upgrades: RunUpgrades = RunUpgrades(),
) {
    val s = PrefixedStore(store, RUN_SAVE_PREFIX)
    s.putFloat(KEY_VERSION, RUN_SAVE_VERSION.toFloat())
    s.putFloat(KEY_NEXT_LEVEL, nextLevel.toFloat())
    s.putFloat(KEY_CREDITS, credits.toFloat())
    ShopItem.entries.forEach { s.putFloat(KEY_UPGRADE_PREFIX + it.name, upgrades.level(it).toFloat()) }
    saveSettings(s, runSpecs)
}

/** Restores credits and upgrades from [save] into [state] (Continue). */
fun restoreRunProgress(save: RunSave, state: GameState) {
    state.credits = save.credits
    state.upgrades.clear()
    for (e in save.upgrades.entries) state.upgrades.setLevel(e.key, e.value)
}

/** Saves after a cleared level of a normal run; scenarios never save. Returns whether it wrote. */
fun saveAfterLevel(store: KeyValueStore, state: GameState, runSpecs: List<SettingSpec>): Boolean {
    if (state.scenario != null) return false
    writeRunSave(store, state.level + 1, runSpecs, state.credits, state.upgrades)
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
    s.putFloat(KEY_CREDITS, 0f)
    ShopItem.entries.forEach { s.putFloat(KEY_UPGRADE_PREFIX + it.name, 0f) }
    s.flush()
}
