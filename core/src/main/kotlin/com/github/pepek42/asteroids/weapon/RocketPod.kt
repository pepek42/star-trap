package com.github.pepek42.asteroids.weapon

import com.github.pepek42.asteroids.component.WeaponLoadoutComponent

/**
 * Ticks the rocket pod by [dt] ship proper seconds (ST-181), whatever slot is selected, so a pod keeps reloading while the
 * laser is in use: the salvo cooldown counts down, and an empty pod counts its reload and refills to [ROCKET_POD_SIZE].
 */
fun WeaponLoadoutComponent.tickRocketPod(dt: Float) {
    if (rocketSalvoCooldown > 0f) rocketSalvoCooldown = (rocketSalvoCooldown - dt).coerceAtLeast(0f)
    if (rocketsInPod <= 0) {
        rocketReloadLeft -= dt
        if (rocketReloadLeft <= 0f) {
            rocketsInPod = ROCKET_POD_SIZE
            rocketReloadLeft = 0f
        }
    }
}

/** True when a rocket may leave now: one is in the pod and the salvo cooldown is over. */
fun WeaponLoadoutComponent.canFireRocket(): Boolean = rocketsInPod > 0 && rocketSalvoCooldown <= 0f

/** Takes one rocket out of the pod and starts the salvo cooldown; the last one starts the [ROCKET_RELOAD_SECONDS] reload. */
fun WeaponLoadoutComponent.consumeRocket() {
    rocketsInPod--
    rocketSalvoCooldown = ROCKET_SALVO_INTERVAL
    if (rocketsInPod <= 0) {
        rocketsInPod = 0
        rocketReloadLeft = ROCKET_RELOAD_SECONDS
    }
}
