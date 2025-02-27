# star-trap

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/tommyettinger/gdx-liftoff).

This project was generated with a Kotlin project template that includes Kotlin application launchers and [KTX](https://libktx.github.io/) utilities.

## About

This game is POC for physics-based space shooter. The idea was to make it an Action Roguelike game. Inspirations:

* Asteroids - ofc
* Much Action Roguelike, lets mention [Nova Drift](https://store.steampowered.com/app/858210/Nova_Drift/) as it is space
  Acton Roguelike
* [ΔV: Rings of Saturn](https://store.steampowered.com/app/846030/DV_Rings_of_Saturn/) for showing realistic spaceship
  mechanics can be fun

## How to play

Left mouse button to shoot, right to use thrusters. Mouse wheel to zoom in and out.

## Possible ideas

* Speed of light mechanics to limit ship speed, black holes, realistic physics
* Rouge like progression
* Instead of teleport make force field around play arena to smooth out experience

## Third party assets used

* [lunar-battle assets pack](https://mattwalkden.itch.io/lunar-battle-pack)
* https://enjl.itch.io/background-starry-space under https://creativecommons.org/licenses/by-sa/4.0/

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3.
- `teavm`: Experimental web platform using TeaVM and WebGL.

## Gradle

This project uses [Gradle](http://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/lib`.
- `lwjgl3:run`: starts the application.
- `teavm:build`: builds the JavaScript application into the build/dist/webapp folder.
- `teavm:run`: serves the JavaScript application at http://localhost:8080 via a local Jetty server.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
