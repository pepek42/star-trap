package com.github.pepek42.asteroids.system

import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.github.pepek42.asteroids.audio.HullSoundKind
import com.github.pepek42.asteroids.audio.HullSoundQueue
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.LockOnComponent
import com.github.pepek42.asteroids.component.WeaponComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.bodyCmpOptional
import com.github.pepek42.asteroids.component.lockOnCmp
import com.github.pepek42.asteroids.component.playerCmpOptional
import com.github.pepek42.asteroids.component.properDelta
import com.github.pepek42.asteroids.component.removeMapper
import com.github.pepek42.asteroids.component.tickInactive
import com.github.pepek42.asteroids.component.weaponCmp
import com.github.pepek42.asteroids.component.weaponLoadoutCmpOptional
import com.github.pepek42.asteroids.ecs.levelTargetFamily
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.provider.LockOnProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.weapon.LOCK_ON_MAX_IN_FLIGHT
import com.github.pepek42.asteroids.weapon.LOCK_ON_SHOTS_PER_SECOND
import com.github.pepek42.asteroids.weapon.WeaponType
import ktx.ashley.allOf

class WeaponSystem(
    private val weaponProjectileProvider: WeaponProjectileProvider,
    private val clock: GameClock? = null,
    /** The player's shots go here for `HullSoundSystem`; null in headless runs. */
    private val hullSounds: HullSoundQueue? = null,
    /** Creates lock-on shot bodies (ST-164); null in tests that only exercise lasers. */
    private val lockOnProvider: LockOnProvider? = null,
) : IteratingSystem(allOf(WeaponComponent::class, BodyComponent::class).get()) {
    private val lockOnShots = allOf(LockOnComponent::class).get()
    private val targets = levelTargetFamily()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val weaponCmp = entity.weaponCmp
        val dt = entity.properDelta(clock, deltaTime)
        weaponCmp.cooldown -= dt
        entity.weaponLoadoutCmpOptional?.tickInactive(dt)

        if (!weaponCmp.doAttack || weaponCmp.cooldown > 0f) return
        when (weaponCmp.weaponType) {
            WeaponType.LASER -> fireLaser(weaponCmp, entity)
            WeaponType.LOCK_ON -> fireLockOn(weaponCmp, entity)
        }
    }

    private fun fireLaser(weaponCmp: WeaponComponent, entity: Entity) {
        weaponCmp.cooldown = 1 / weaponCmp.shotsPerSecond
        weaponProjectileProvider.spawnProjectile(weaponCmp, entity)
        if (entity.playerCmpOptional != null) hullSounds?.push(HullSoundKind.LASER_FIRE)
    }

    /**
     * Temporary fire path for slot 2 (ST-164, until the lock-on of ST-165): launch at the nearest level target. Without
     * one the lock-on shot flies dumb along the current aim. Ammo and the per-shooter in-flight limit gate the shot.
     */
    private fun fireLockOn(weaponCmp: WeaponComponent, entity: Entity) {
        val provider = lockOnProvider ?: return
        val loadout = entity.weaponLoadoutCmpOptional ?: return
        if (loadout.lockOnAmmo <= 0 || lockOnsInFlight(entity) >= LOCK_ON_MAX_IN_FLIGHT) return
        weaponCmp.cooldown = 1 / LOCK_ON_SHOTS_PER_SECOND
        provider.spawnLockOn(entity, nearestTarget(entity), weaponCmp)
        loadout.lockOnAmmo--
    }

    /** Lock-on shots of [shooter] that are still alive; ones already tagged for removal do not count. */
    private fun lockOnsInFlight(shooter: Entity): Int {
        val id = shooter.baseInfoCmp.id
        val list = engine.getEntitiesFor(lockOnShots)
        var count = 0
        for (i in 0 until list.size()) {
            val shot = list[i]
            if (removeMapper.get(shot) == null && shot.lockOnCmp.shooterId == id) count++
        }
        return count
    }

    /** Nearest asteroid or enemy ship to [shooter], the stand-in lock of the temporary debug fire path. */
    private fun nearestTarget(shooter: Entity): Entity? {
        val list = engine.getEntitiesFor(targets)
        if (list.size() == 0) return null
        val origin = shooter.bodyCmp.body.position
        var best: Entity? = null
        var bestDistanceSq = Float.MAX_VALUE
        for (i in 0 until list.size()) {
            val candidate = list[i]
            val position = candidate.bodyCmpOptional?.body?.position ?: continue
            val distanceSq = position.dst2(origin)
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                best = candidate
            }
        }
        return best
    }
}
