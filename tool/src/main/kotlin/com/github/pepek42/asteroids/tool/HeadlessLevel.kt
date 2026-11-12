package com.github.pepek42.asteroids.tool

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntityListener
import com.badlogic.ashley.core.PooledEngine
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.box2d.World
import com.github.pepek42.asteroids.component.AsteroidComponent
import com.github.pepek42.asteroids.component.BodyComponent
import com.github.pepek42.asteroids.component.BaseInfoComponent
import com.github.pepek42.asteroids.component.EnemyComponent
import com.github.pepek42.asteroids.component.HealthComponent
import com.github.pepek42.asteroids.component.ProjectileComponent
import com.github.pepek42.asteroids.component.baseInfoCmp
import com.github.pepek42.asteroids.component.PlayerComponent
import com.github.pepek42.asteroids.component.RelativisticComponent
import com.github.pepek42.asteroids.component.bodyCmp
import com.github.pepek42.asteroids.component.projectileCmp
import com.github.pepek42.asteroids.component.healthCmp
import com.github.pepek42.asteroids.component.relativisticCmp
import com.github.pepek42.asteroids.ecs.GameplayDeps
import com.github.pepek42.asteroids.ecs.buildGameplaySystems
import com.github.pepek42.asteroids.ecs.clearLevel
import com.github.pepek42.asteroids.event.GameEventManager
import com.github.pepek42.asteroids.input.ControlScheme
import com.github.pepek42.asteroids.input.ControlSettings
import com.github.pepek42.asteroids.physics.GameClock
import com.github.pepek42.asteroids.physics.ObserverState
import com.github.pepek42.asteroids.physics.RelativitySettings
import com.github.pepek42.asteroids.physics.velocityFromU
import com.github.pepek42.asteroids.provider.AsteroidProvider
import com.github.pepek42.asteroids.faction.FactionEnum
import com.github.pepek42.asteroids.provider.EnemyShipProvider
import com.github.pepek42.asteroids.provider.enemyCount
import com.github.pepek42.asteroids.provider.MapProvider
import com.github.pepek42.asteroids.provider.LockOnProvider
import com.github.pepek42.asteroids.provider.RocketProvider
import com.github.pepek42.asteroids.provider.PLAYER_SPRITE_SIZE_PX
import com.github.pepek42.asteroids.provider.PlayerEntityProvider
import com.github.pepek42.asteroids.provider.WeaponProjectileProvider
import com.github.pepek42.asteroids.render.ShimmerImpacts
import com.github.pepek42.asteroids.system.PhysicsSystem.Companion.PHYSICS_UPDATE_INTERVAL
import ktx.ashley.allOf
import ktx.box2d.createWorld
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** How a headless level ended. */
enum class Outcome { CLEARED, DIED, TIMEOUT }

/** One run: a level, a seed and the physics knobs under test. */
data class LevelSetup(
    val c: Float,
    val engineAccelPerC: Float,
    val level: Int,
    val seed: Long,
    /** Pilot-clock seconds before the run counts as a timeout. */
    val maxSeconds: Float,
    val scheme: ControlScheme = ControlScheme.AUTO_AIM,
    /**
     * Enemy ships to spawn. The default is the game's rule ([enemyCount]); any other value spawns that many on a ring
     * around the player ([EnemyShipProvider.spawnNearEnemies]), which tests use to get an enemy on level 2.
     */
    val enemies: Int = enemyCount(level),
)

/** Everything measured in one run. Times are pilot-clock seconds (one frame is [PHYSICS_UPDATE_INTERVAL]). */
class LevelResult(
    val setup: LevelSetup,
    val bot: String,
    val outcome: Outcome,
    val seconds: Float,
    val damageTaken: Float,
    val kills: Int,
    val gammaMean: Float,
    val gammaP95: Float,
    val gammaMax: Float,
    val warpCappedFraction: Float,
    val stepsCappedFraction: Float,
    val stepsPerFrameMean: Float,
    val enemiesSpawned: Int = 0,
    /** Enemy entities removed during the run. */
    val enemiesKilled: Int = 0,
    /**
     * Player health lost in frames where a spent enemy projectile was removed (APPROX: a ram in the same frame counts
     * too, see `docs/tech/balance.md`).
     */
    val damageFromEnemies: Float = 0f,
    /** Pilot seconds until the last enemy died; NaN when there were none or some survived. */
    val enemyClearSeconds: Float = Float.NaN,
)

/** Texture-less atlas for the headless runner: sprites only carry the size the providers read, nothing is drawn. */
class HeadlessAtlas : TextureAtlas() {
    override fun createSprite(name: String): Sprite = Sprite().apply { setSize(PLAYER_SPRITE_SIZE_PX, PLAYER_SPRITE_SIZE_PX) }

    override fun createSprite(name: String, index: Int): Sprite = createSprite(name)
}

/**
 * The player's mouse is replaced by the bot: the camera maps screen coordinates 1:1 to world coordinates, so the bot
 * aims by "moving the mouse" to a far point in the wanted direction.
 */
class IdentityCamera : OrthographicCamera() {
    override fun unproject(screenCoords: Vector3): Vector3 = screenCoords
}

/** Distance (u) of the fake mouse point from the ship: far, so integer screen coordinates still give a precise angle. */
private const val AIM_REACH = 2000f

private class RunningStats(frames: Int) {
    val gammas = FloatArray(frames)
    var n = 0
}

/**
 * Plays one level headless with the real systems (via [buildGameplaySystems]), a real Box2D world and fixed frames of
 * [PHYSICS_UPDATE_INTERVAL]. The bot drives the same `GameEventManager` input callbacks the mouse does.
 */
fun runLevel(setup: LevelSetup, bot: BalanceBot): LevelResult {
    val settings = RelativitySettings(c = setup.c, engineAccelPerC = setup.engineAccelPerC)
    val world: World = createWorld().apply { autoClearForces = false }
    val engine = PooledEngine()
    val random = RandomXS128(setup.seed)
    val gameEvents = GameEventManager()
    val mapProvider = MapProvider(gameEvents)
    val clock = GameClock()
    val atlas = HeadlessAtlas()
    var cleared = false
    var died = false
    val deps = GameplayDeps(
        engine = engine,
        world = world,
        settings = settings,
        mapProvider = mapProvider,
        clock = clock,
        observerState = ObserverState(),
        gameEventManager = gameEvents,
        camera = IdentityCamera(),
        controlSettings = ControlSettings(setup.scheme),
        weaponProjectileProvider = WeaponProjectileProvider(atlas, engine, world, settings),
        lockOnProvider = LockOnProvider(atlas, engine, world, settings),
        rocketProvider = RocketProvider(atlas, engine, world, settings),
        asteroidProvider = AsteroidProvider(atlas, engine, world, random, settings),
        random = random,
        shimmerImpacts = ShimmerImpacts(),
        onLevelCleared = { cleared = true },
        onPlayerDeath = { died = true },
    )
    buildGameplaySystems(deps)
    PlayerEntityProvider(engine, world, mapProvider, atlas, settings).spawnPlayerEntity()
    deps.asteroidProvider.spawnAsteroids(setup.level, mapProvider)
    val enemyProvider = EnemyShipProvider(engine, world, atlas, settings)
    if (setup.enemies == enemyCount(setup.level)) {
        enemyProvider.spawn(setup.level, random, mapProvider, FloatArray(0))
    } else {
        enemyProvider.spawnNearEnemies(setup.enemies, mapProvider.playerSpawnLocation())
    }

    var kills = 0
    val asteroidFamily = allOf(AsteroidComponent::class, HealthComponent::class).get()
    engine.addEntityListener(asteroidFamily, object : EntityListener {
        override fun entityAdded(entity: Entity) = Unit
        override fun entityRemoved(entity: Entity) {
            if (entity.healthCmp.health <= 0f) kills++
        }
    })

    var enemiesKilled = 0
    var enemyShotRemoved = false
    val enemyFamily = allOf(EnemyComponent::class, HealthComponent::class).get()
    val projectileFamily = allOf(ProjectileComponent::class, BaseInfoComponent::class).get()
    engine.addEntityListener(enemyFamily, object : EntityListener {
        override fun entityAdded(entity: Entity) = Unit
        override fun entityRemoved(entity: Entity) {
            enemiesKilled++
        }
    })
    engine.addEntityListener(projectileFamily, object : EntityListener {
        override fun entityAdded(entity: Entity) = Unit
        override fun entityRemoved(entity: Entity) {
            if (entity.projectileCmp.spent && entity.baseInfoCmp.faction == FactionEnum.ENEMY) enemyShotRemoved = true
        }
    })
    val enemiesSpawned = engine.getEntitiesFor(enemyFamily).size()
    val enemies = engine.getEntitiesFor(allOf(EnemyComponent::class, BodyComponent::class).get())

    val player = engine.getEntitiesFor(allOf(PlayerComponent::class, BodyComponent::class, RelativisticComponent::class).get()).first()
    val asteroids = engine.getEntitiesFor(allOf(AsteroidComponent::class, BodyComponent::class).get())
    val driver = BotDriver(gameEvents)
    val view = BotView()
    view.c = settings.c
    val command = BotCommand()

    val frames = max(1, (setup.maxSeconds / PHYSICS_UPDATE_INTERVAL).roundToInt())
    val stats = RunningStats(frames)
    var gammaSum = 0.0
    var gammaMax = 1f
    var warpCapped = 0
    var stepsCapped = 0
    var steps = 0L
    var damage = 0f
    var frame = 0
    val v = Vector2()
    var healthBefore = player.healthCmp.health
    var damageFromEnemies = 0f
    var enemyClearFrame = -1

    while (frame < frames && !cleared && !died) {
        fillView(view, player, asteroids, enemies, v, settings.c)
        bot.decide(view, command)
        driver.apply(command, view)

        val timeBefore = clock.worldTime
        engine.update(PHYSICS_UPDATE_INTERVAL)
        frame++
        steps += ((clock.worldTime - timeBefore) / PHYSICS_UPDATE_INTERVAL).roundToInt()

        val gamma = player.relativisticCmp.gUsed
        stats.gammas[stats.n++] = gamma
        gammaSum += gamma
        if (gamma > gammaMax) gammaMax = gamma
        if (clock.warpCapped) warpCapped++
        if (clock.stepsCapped) stepsCapped++
        val health = player.healthCmp.health
        if (health < healthBefore) {
            damage += healthBefore - health
            if (enemyShotRemoved) damageFromEnemies += healthBefore - health
        }
        enemyShotRemoved = false
        if (enemyClearFrame < 0 && enemiesSpawned > 0 && enemiesKilled >= enemiesSpawned) enemyClearFrame = frame
        healthBefore = health
    }

    val outcome = when {
        died -> Outcome.DIED
        cleared -> Outcome.CLEARED
        else -> Outcome.TIMEOUT
    }
    val n = max(1, stats.n)
    val result = LevelResult(
        setup = setup,
        bot = bot.name,
        outcome = outcome,
        seconds = frame * PHYSICS_UPDATE_INTERVAL,
        damageTaken = damage,
        kills = kills,
        gammaMean = (gammaSum / n).toFloat(),
        gammaP95 = percentile(stats.gammas, stats.n, 0.95f),
        gammaMax = gammaMax,
        warpCappedFraction = warpCapped.toFloat() / n,
        stepsCappedFraction = stepsCapped.toFloat() / n,
        stepsPerFrameMean = steps.toFloat() / n,
        enemiesSpawned = enemiesSpawned,
        enemiesKilled = enemiesKilled,
        damageFromEnemies = damageFromEnemies,
        enemyClearSeconds = if (enemyClearFrame < 0) Float.NaN else enemyClearFrame * PHYSICS_UPDATE_INTERVAL,
    )
    clearLevel(engine, world)
    world.dispose()
    return result
}

private fun fillView(view: BotView, player: Entity, asteroids: Iterable<Entity>, enemies: Iterable<Entity>, v: Vector2, c: Float) {
    val body = player.bodyCmp.body
    val u = player.relativisticCmp.u
    velocityFromU(u.x, u.y, c, v)
    view.px = body.position.x
    view.py = body.position.y
    view.vx = v.x
    view.vy = v.y
    view.hullAngle = body.angle
    view.clearAsteroids()
    for (asteroid in asteroids) {
        val p = asteroid.bodyCmp.body.position
        view.addAsteroid(p.x, p.y)
    }
    for (enemy in enemies) {
        val p = enemy.bodyCmp.body.position
        view.addEnemy(p.x, p.y)
    }
}

/** Turns a [BotCommand] into the input events the game receives from a mouse. Edge-triggers the fire button. */
class BotDriver(private val events: GameEventManager) {
    private var firing = false
    private var aimX = 0
    private var aimY = 0

    fun apply(command: BotCommand, view: BotView) {
        events.thrusters(command.thrust)
        command.aimRad?.let {
            aimX = (view.px + cos(it) * AIM_REACH).roundToInt()
            aimY = (view.py + sin(it) * AIM_REACH).roundToInt()
            events.mouseMoved(aimX, aimY)
        }
        if (command.fire != firing) {
            firing = command.fire
            // Button events also carry the pointer position, so repeat the last aim point.
            if (firing) events.touchDown(aimX, aimY, 0, Input.Buttons.LEFT) else events.touchUp(aimX, aimY, 0, Input.Buttons.LEFT)
        }
    }
}

/** [p]-quantile (0..1) of the first [n] values of [values] (nearest rank). Sorts a copy. */
fun percentile(values: FloatArray, n: Int, p: Float): Float {
    if (n <= 0) return 0f
    val sorted = values.copyOf(n)
    sorted.sort()
    val idx = ((n - 1) * p).roundToInt().coerceIn(0, n - 1)
    return sorted[idx]
}
