---
name: run-desktop
description: "Build, run, and drive the HISS Compose desktop app for real end-to-end verification: launch it with the cmp-bridge debug server armed, then read its live UI tree, click, type, and screenshot it through cmp-bridge-http-server. Use when asked to run the desktop app, take a screenshot of it, or manually verify a flow (e.g. login, navigation, a form) actually works in the running app -- not just that its tests pass."
---

# run-desktop — Drive the HISS desktop app for real

`composeApp` embeds [cmp-bridge](https://github.com/CRamsan/cmp-bridge)
(`com.cramsan.cmpbridge:cmp-bridge`, desktop-only, wired into
`desktopMain/kotlin/com/cramsan/hirsh/main.kt`). It drives the app through
Compose's real semantics tree and posts synthetic input onto the app's own AWT
event queue -- never `java.awt.Robot`, never the host X display. **This is
deliberately not an X11/xdotool/screen-capture approach** -- earlier attempts at
that hit two real problems: `XGetImage` on the root window fails (`BadMatch`)
under a compositing WM, and more importantly, `DISPLAY` in this environment is
the user's own live desktop session, not an isolated Xvfb -- driving it that way
risks screenshotting/clicking into whatever the user actually has open.
cmp-bridge sidesteps both: screenshots come from `SkiaLayer.screenshot()` (the
app's own render surface), and clicks/keystrokes are posted directly onto the
app window's event queue, so none of it ever touches the real display.

All paths below are relative to the repo root.

## Prerequisites

- A GitHub CLI (`gh`) with network access, to pull the standalone driver jar
  from cmp-bridge's releases (it isn't on Maven Central -- see Gotchas).
- Everything `README.md` already requires to build `composeApp` (JDK 17/21).

## One-time: fetch the driver jar

```bash
mkdir -p /tmp/cmp-bridge
gh release download --repo CRamsan/cmp-bridge --dir /tmp/cmp-bridge --clobber \
  --pattern 'cmp-bridge-http-server-all.jar'
```

Re-run this if `gradle/libs.versions.toml`'s `cmp-bridge` version bumps --
the driver jar's protocol should stay compatible within a minor line, but
matching versions avoids surprises.

## Launch the app with the bridge armed

```bash
CMP_BRIDGE_ENABLED=true ./gradlew :composeApp:run --quiet &
# wait for the in-app bridge socket, not just the process
timeout 60 bash -c 'until (echo > /dev/tcp/127.0.0.1/8901) 2>/dev/null; do sleep 1; done'
```

`DesktopBridgeServer.startIfEnabled` (called from `main.kt`) is a no-op without
`CMP_BRIDGE_ENABLED=true` -- a plain `./gradlew :composeApp:run` is always safe
and never opens the debug port.

## Launch the driver server, pointed at the running app

```bash
java -jar /tmp/cmp-bridge/cmp-bridge-http-server-all.jar --platform desktop &
timeout 20 bash -c 'until (echo > /dev/tcp/127.0.0.1/8090) 2>/dev/null; do sleep 1; done'
```

## Drive it

One endpoint, `POST /bridge`, `{"operation": "...", "payload": {...}}`:

```bash
curl -s -X POST http://127.0.0.1:8090/bridge -H 'Content-Type: application/json' \
  -d '{"operation":"getHierarchy"}'                                    # live UI tree as JSON
curl -s -X POST http://127.0.0.1:8090/bridge -H 'Content-Type: application/json' \
  -d '{"operation":"click","payload":{"tag":"login_submit_button"}}'
curl -s -X POST http://127.0.0.1:8090/bridge -H 'Content-Type: application/json' \
  -d '{"operation":"setText","payload":{"tag":"login_username_field","text":"drpatel"}}'
curl -s -X POST http://127.0.0.1:8090/bridge -H 'Content-Type: application/json' \
  -d '{"operation":"screenshot"}' -o shot.png                          # PNG, read it -- don't skip this
```

`getHierarchy`'s tree gives every node's `text` regardless of whether it has a
`testTag` -- reading screen content back (e.g. confirming "Pacientes" rendered)
never needs a tag; only `click`/`setText` targets do.

### Known test tags

| Tag | Screen | Element |
|---|---|---|
| `login_username_field` | Login | Usuario field |
| `login_password_field` | Login | Contrasena field |
| `login_submit_button` | Login | Iniciar sesion button |
| `nav_patients` | sidebar (any `AppScaffold` screen) | Pacientes nav item |
| `nav_profile` | sidebar (any `AppScaffold` screen) | Perfil nav item |
| `profile_sign_out_button` | Profile | Cerrar sesion button |

Add a tag the same way for any new interactive element you need to drive:
`Modifier.testTag("some_stable_name")` from `androidx.compose.ui.platform.testTag`
(see `LoginScreen.kt`/`ProfileScreen.kt`/`AppScaffold.kt` for the existing
pattern -- `AppScaffold`'s nav items are tagged `"nav_${item.destination}"`, one
tag per route rather than per label, so it stays correct as labels change).

## Tear down

```bash
pkill -f cmp-bridge-http-server-all.jar
pkill -f com.cramsan.hirsh.MainKt
```

`pkill -f` against the app's own pattern is more reliable than the gradle
wrapper's PID -- `:composeApp:run` forks the actual JVM as a child process, so
killing only the wrapper can leave the app running.

## Gotchas

- **Rapid back-to-back `setText` calls into two different fields can race.**
  Multi-character text entry goes through the system clipboard + Ctrl+V (see
  cmp-bridge's `ARCHITECTURE.md`) rather than simulated keystrokes, so firing
  `setText` at field A immediately followed by `setText` at field B can have
  B's clipboard write land before A's paste completes, leaving A empty. Put a
  `sleep 1` (or poll `getHierarchy` for the expected text) between `setText`
  calls into different fields, not just after the last one.
- **A session can already be logged in when you launch.** `AppPreferences`
  persists `sessionUsername` via `java.util.prefs`, which survives across
  `:composeApp:run` invocations on the same machine -- don't assume a fresh
  launch starts on the login screen; check `getHierarchy` first (if
  `nav_patients`/`nav_profile` show up, sign out via `profile_sign_out_button`
  before testing the login flow itself).
- **`cmp-bridge`/`cmp-bridge-driver` are on Maven Central** as
  `com.cramsan.cmpbridge:{cmp-bridge,cmp-bridge-driver}` -- Central's search UI
  (`search.maven.org`'s Solr index) can lag behind what's actually live on
  `repo1.maven.org`; if a search comes up empty, check
  `https://repo1.maven.org/maven2/com/cramsan/cmpbridge/<artifact>/maven-metadata.xml`
  directly before concluding it isn't published. `cmp-bridge-http-server` and
  `cmp-bridge-mcp-server` are CLI apps, not libraries -- they're never on Maven
  Central by design; pull them from GitHub Releases (see above).
