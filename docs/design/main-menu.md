# Main menu: controls and settings

Owner request (2026-11-03): "Main screen should show all control shortcuts. Before starting the game, I want to have options to choose whatever can be changed. For example, choosing aim mode [...]. Start screen can have play and quit as now, but add all controls and settings that could be changed."

Cards: ST-113 … ST-119 (`docs/backlog.md`, milestone "Main menu").

## Layout

One screen (`screen/MainMenuScreen.kt`), scene2d `Table` filling the stage:

```
+-------------------------------------------------------------+
|                         STAR TRAP                           |
|                                                             |
|  [ Single Player ]     SETTINGS               CONTROLS      |
|  [ Exit ]              Aim mode   [mouse v]   Mouse   Aim   |
|                        Pilot view [ ]         LMB     Fire  |
|                        ...                    RMB     Thrust|
|                        [Reset defaults]       ...           |
+-------------------------------------------------------------+
```

- Left: the existing Play and Quit buttons, unchanged (Quit stays hidden on WebGL).
- Middle: settings panel, one row per player-facing setting (label, widget, tooltip from a `*_hint` key), then "Reset defaults".
- Right: controls list, two columns (input, action). It shows the bindings of the **selected** aim mode and rebuilds when the mode changes. Debug bindings (F1) are listed under a "Debug" subheading only when `DebugSettings.panelAllowed`.

## Single sources of truth

- **Bindings** (ST-114): `input/InputBindings.kt` holds one table of `Binding(action, trigger, labelKey, debugOnly, schemes)`. `GameEventManager` dispatches through it (no keycodes in `when` branches) and the menu lists it, so the menu can't drift from the real keys. Key names come from `Input.Keys.toString(code)`; mouse triggers use i18n keys (`input_lmb`, `input_rmb`, `input_mouse`, `input_wheel`).
- **Settings** (ST-115): `settings/MenuSettings.kt` holds one list of setting specs (`Toggle`, `Slider`, `Choice`), each with a prefs key, label key, default, getter and setter onto the existing DI objects. The menu, persistence and "Reset defaults" all iterate the same list.

## Inventory

### Bindings (today)

| Input | Action | Where | Menu |
|---|---|---|---|
| Mouse move | Aim (hull turns toward the cursor) | `GameEventManager.mouseMoved` → `screenAimPoint` | yes |
| LMB (hold) | Fire | `touchDown/Up` → `fire` | yes |
| RMB (hold) | Thrust | `touchDown/Up` → `movement` | yes |
| Wheel | Zoom (0.5–2×, plus auto zoom-out with speed) | `scrolled` → `zoom` | yes |
| V | Toggle pilot view | `keyDown` → `togglePilotView` | yes |
| Right Ctrl | Shield / block (placeholder, does nothing yet) | `keyDown` → `block` | yes, labelled "(not yet)" |
| F1 | Debug panel | `keyDown` → `DebugInputListener` (works even when input is ignored) | debug section only |

Coming: W/A/S/D + arrows (twin-stick move, ST-061; scheme `TWIN_STICK` only).

### Aim modes

`input/ControlScheme.kt` has three entries, but only one has its own strategy:

| Scheme | Flag | State |
|---|---|---|
| `MOUSE_HULL` | `mouse` | implemented (`MouseHullStrategy`) |
| `TWIN_STICK` | `twin` | falls back to mouse-hull until ST-061 (WASD thrust direction, mouse turret) |
| `AUTO_AIM` | `auto` | falls back to mouse-hull until ST-062 (guns auto-target in a forward cone); ST-063 makes it the default |
| (trance) | — | parked, ST-091 |

ST-114 adds `ControlScheme.implemented`. The menu offers only implemented schemes, so the selector has one entry today and grows as ST-061/ST-062 land. The debug panel keeps listing all of them.

### Settings

| Setting | Backing field | Type / range | Default | Where |
|---|---|---|---|---|
| Aim mode | `ControlSettings.scheme` | choice of implemented `ControlScheme`s | `ControlSettings()` default (mouse; auto after ST-063) | **menu** |
| Pilot view (experimental) | `RelativitySettings.pilotView` | toggle (V in game) | `PILOT_VIEW_DEFAULT` (false) | **menu** |
| Relativity (off = Newtonian) | `RelativitySettings.relativityEnabled` | toggle | `RELATIVITY_ENABLED` (true) | **menu** |
| Speed of light c | `RelativitySettings.c` | slider `C_MIN`…`C_MAX_BOX2D_SAFE`, step 1 | `SPEED_OF_LIGHT` (20) | **menu**, disabled when relativity is off |
| Time warp (arena runs on ship time) | `RelativitySettings.timeWarpEnabled` | toggle | `TIME_WARP_ENABLED` (true) | **menu**, disabled when relativity is off |
| Target pointers | `DebugSettings.targetPointersEnabled` | toggle | `TARGET_POINTERS_ENABLED` | **menu** |
| Reference grid | `DebugSettings.gridEnabled` | toggle | `GRID_ENABLED` | **menu** |
| Language | prefs only, read at startup | choice: system / English / Polski | system | **menu** (ST-117), applies on restart |
| Fullscreen | `Gdx.graphics` | toggle, desktop only | false | **menu** (ST-117) |
| Boundary field on/off, aberration, light delay | `RelativitySettings` | toggles | true | debug panel only |
| Engine a0/c, brake boost, field damping, warp cap, γ cap | `RelativitySettings` | sliders | consts | debug panel only (balance knobs, ST-101) |
| Time scale, show vectors | `DebugSettings` | buttons / toggle | 1×, off | debug panel only |
| Seed, screenshot, near asteroids, `--debug-panel` | `LaunchOptions` | CLI flags | — | command line only |

Rule of thumb: a setting is on the menu if it changes what the player sees or how they control the ship. Physics balance knobs stay on the F1 panel until ST-101 has picked values.

## Flow into PlayScreen

No new plumbing: `RelativitySettings`, `ControlSettings` and `DebugSettings` are already DI singletons that `PlayScreen` and its systems read every frame. The menu mutates those same objects. The F1 panel and V edit the same objects in game, so the menu always shows the current values.

## Persistence

libGDX `Preferences` (name `PREFERENCES_NAME = "star-trap-settings"` in `Config.kt`; a file in `~/.prefs/` on desktop, localStorage on TeaVM). Behind a small `KeyValueStore` interface so tests use an in-memory map.

- Load once in `Game.create()` after the singletons are bound; values go through the existing clamping setters, and an unknown or unimplemented scheme flag falls back to the default.
- Command-line flags win over prefs (`--controls=` overrides the saved aim mode).
- Screenshot runs (`--screenshot`) skip loading, so harness output stays reproducible.
- Save (and `flush`) whenever a menu widget changes a value, and on Play.
- Only menu settings are persisted; debug-panel-only knobs reset every launch.
- Key `settings_version = 1` for future migrations.

## i18n

Every label, hint and input name is a key in both `assets/i18n/messages.properties` and `messages_pl.properties`. ST-113 adds a test that the two files have identical key sets, and ST-114/ST-115 extend it to check that every `labelKey` in the bindings and settings tables exists.

## Out of scope (for now)

- Key rebinding (ST-119 asks the owner; the bindings table makes it possible later).
- A pause / back-to-menu key in game.
- Gamepad.
