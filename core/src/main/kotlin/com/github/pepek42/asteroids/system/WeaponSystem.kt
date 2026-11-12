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
import com.github.pepek42.asteroids.provider.RocketProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.weapon.LOCK_ON_MAX_IN_FLIGHT
import com.github.pepek42.asteroids.weapon.LOCK_ON_SHOTS_PER_SECOND
import com.github.pepek42.asteroids.weapon.WeaponType
import com.badlogic.gdx.math.MathUtils
import com.github.pepek42.asteroids.component.WeaponLoadoutComponent
import com.github.pepek42.asteroids.component.baseInfoMapper
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.SPEED_OF_LIGHT
import com.github.pepek42.asteroids.weapon.TARGET_LOCK_CONE_RAD
import com.github.pepek42.asteroids.weapon.TARGET_LOCK_KEEP_CONE_RAD
import com.github.pepek42.asteroids.weapon.TARGET_LOCK_NO_LOCK_CLICK_INTERVAL_S
import com.github.pepek42.asteroids.weapon.TARGET_LOCK_RANGE_LIGHT_SECONDS
import com.github.pepek42.asteroids.weapon.TargetLockState
import com.github.pepek42.asteroids.weapon.advanceLock
import com.github.pepek42.asteroids.weapon.clearLock
import com.github.pepek42.asteroids.weapon.lockOffAxis
import com.github.pepek42.asteroids.weapon.lockedTarget
import com.github.pepek42.asteroids.weapon.canFireRocket
import com.github.pepek42.asteroids.weapon.consumeRocket
import com.github.pepek42.asteroids.weapon.regenLockOnAmmo
import com.github.pepek42.asteroids.weapon.tickRocketPod
import ktx.ashley.allOf

class WeaponSystem(
    private val weaponProjectileProvider: WeaponProjectileProvider,
    private val clock: GameClock? = null,
    /** The player's shots go here for `HullSoundSystem`; null in headless runs. */
    private val hullSounds: HullSoundQueue? = null,
    /** Creates lock-on shot bodies (ST-164); null in tests that only exercise lasers. */
    private val lockOnProvider: LockOnProvider? = null,
    /** Runtime c for the lock range (ST-165); null = [SPEED_OF_LIGHT]. */
    private val relativity: RelativitySettings? = null,
    /** Creates rocket bodies (ST-181); null in tests that never fire slot 3. */
    private val rocketProvider: RocketProvider? = null,
) : IteratingSystem(allOf(WeaponComponent::class, BodyComponent::class).get()) {
    private val lockOnShots = allOf(LockOnComponent::class).get()
    private val targets = levelTargetFamily()

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val weaponCmp = entity.weaponCmp
        val dt = entity.properDelta(clock, deltaTime)
        weaponCmp.cooldown -= dt
        val loadout = entity.weaponLoadoutCmpOptional
        if (loadout != null) {
            loadout.tickInactive(dt)
            loadout.regenLockOnAmmo(dt)
            loadout.tickRocketPod(dt)
            if (weaponCmp.weaponType == WeaponType.LOCK_ON) updateLock(entity, weaponCmp, loadout, dt)
            else if (loadout.lockState != TargetLockState.NONE) loadout.clearLock()
        }

        if (!weaponCmp.doAttack || weaponCmp.cooldown > 0f) return
        when (weaponCmp.weaponType) {
            WeaponType.LASER -> fireLaser(weaponCmp, entity)
            WeaponType.LOCK_ON -> fireLockOn(weaponCmp, entity)
            WeaponType.ROCKET -> fireRocket(weaponCmp, entity)
        }
    }

    private fun fireLaser(weaponCmp: WeaponComponent, entity: Entity) {
        weaponCmp.cooldown = 1 / weaponCmp.shotsPerSecond
        weaponProjectileProvider.spawnProjectile(weaponCmp, entity)
        if (entity.playerCmpOptional != null) hullSounds?.push(HullSoundKind.LASER_FIRE)
    }

    /**
     * Slot 2 (ST-165): fires only with a LOCKED target, which the lock-on shot then homes on. Without a lock it fires
     * nothing and the player hears a short "no lock" click. Ammo and the per-shooter in-flight limit gate the shot.
     */
    private fun fireLockOn(weaponCmp: WeaponComponent, entity: Entity) {
        val provider = lockOnProvider ?: return
        val loadout = entity.weaponLoadoutCmpOptional ?: return
        val target = loadout.lockedTarget()
        if (target == null) {
            weaponCmp.cooldown = TARGET_LOCK_NO_LOCK_CLICK_INTERVAL_S
            if (entity.playerCmpOptional != null) hullSounds?.push(HullSoundKind.NO_LOCK)
            return
        }
        if (loadout.lockOnAmmo <= 0 || lockOnsInFlight(entity) >= LOCK_ON_MAX_IN_FLIGHT) return
        weaponCmp.cooldown = 1 / LOCK_ON_SHOTS_PER_SECOND
        provider.spawnLockOn(entity, target, weaponCmp)
        loadout.lockOnAmmo--
    }

    /**
     * Slot 3 (ST-181): launches one unguided rocket along the aim while the pod has one and the salvo cooldown is over.
     * The pod state and its cooldowns live in the loadout, on ship proper time, so they tick while another slot is selected.
     */
    private fun fireRocket(weaponCmp: WeaponComponent, entity: Entity) {
        val provider = rocketProvider ?: return
        val loadout = entity.weaponLoadoutCmpOptional ?: return
        if (!loadout.canFireRocket()) return
        provider.spawnRocket(entity, weaponCmp)
        loadout.consumeRocket()
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

    /**
     * Targeting computer (ST-165), true state: keeps the current lock while its target is alive, in range and inside
     * [TARGET_LOCK_KEEP_CONE_RAD], and offers the level target nearest the aim inside [TARGET_LOCK_CONE_RAD] as the
     * candidate. The aim is `aimAngleRad` (turret schemes) or the hull angle. Allocation-free.
     */
    private fun updateLock(shooter: Entity, weaponCmp: WeaponComponent, loadout: WeaponLoadoutComponent, dt: Float) {
        val body = shooter.bodyCmp.body
        val origin = body.position
        val aim = weaponCmp.aimAngleRad ?: body.angle
        val dirX = MathUtils.cos(aim)
        val dirY = MathUtils.sin(aim)
        val range = TARGET_LOCK_RANGE_LIGHT_SECONDS * (relativity?.c ?: SPEED_OF_LIGHT)

        var kept = false
        val current = loadout.lockTarget
        if (current != null && isLive(current, loadout.lockTargetId)) {
            val p = current.bodyCmp.body.position
            val off = lockOffAxis(p.x - origin.x, p.y - origin.y, dirX, dirY, range)
            kept = !off.isNaN() && off <= TARGET_LOCK_KEEP_CONE_RAD
        }

        val list = engine.getEntitiesFor(targets)
        var best: Entity? = null
        var bestOff = TARGET_LOCK_CONE_RAD
        for (i in 0 until list.size()) {
            val candidate = list[i]
            if (removeMapper.get(candidate) != null) continue
            val p = candidate.bodyCmpOptional?.body?.position ?: continue
            val off = lockOffAxis(p.x - origin.x, p.y - origin.y, dirX, dirY, range)
            if (off.isNaN() || off > bestOff) continue
            bestOff = off
            best = candidate
        }
        val id = best?.baseInfoCmp?.id ?: -1
        if (loadout.advanceLock(best, id, kept, dt) && shooter.playerCmpOptional != null) {
            hullSounds?.push(HullSoundKind.LOCK_TONE)
        }
    }

    /** [target] is still the entity with [id], not removed and with a live body (pooled entities get reused). */
    private fun isLive(target: Entity, id: Int): Boolean {
        if (removeMapper.get(target) != null) return false
        val info = baseInfoMapper.get(target) ?: return false
        if (info.id != id) return false
        return target.bodyCmpOptional?.body?.userData === target
    }
}
