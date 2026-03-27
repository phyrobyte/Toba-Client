# Toba Client — Security Audit & Codebase Snapshot

**Date**: 2026-03-12
**Codebase**: Minecraft 1.21.11 Fabric client mod (Java 21, Yarn mappings, Dear ImGui UI)

---

## 1. SECURITY AUDIT

### CRITICAL

#### [S1] Certificate Pinning is Disabled (SPKI hashes empty)
- **File**: `src/client/java/dev/toba/client/api/utils/TobaAuthenticator.java:54-57`
- **Issue**: `PINNED_SPKI_HASHES` is an empty set. The code has full SPKI pinning infrastructure but the actual hashes are commented out with placeholder text. This means ALL auth and linking requests fall back to standard HTTPS with no pinning, leaving the connection vulnerable to MITM attacks with a rogue CA certificate.
- **Impact**: An attacker with a trusted CA cert (corporate proxy, compromised CA) can intercept authentication traffic, steal JWTs, HWIDs, and user IDs.
- **Fix**: Generate and populate the SPKI hashes for the leaf and intermediate certificates as documented in the code comments. Implement a hash rotation strategy for cert renewals.

#### [S2] Auto-Updater Downloads from Server-Provided URL Without Signature Verification
- **File**: `src/client/java/dev/toba/client/api/utils/AutoUpdater.java:114-124`
- **Issue**: The `downloadUrl` is taken directly from the server's JSON response and the downloaded JAR is saved to the mods folder with zero integrity verification — no checksum, no code signing, no hash comparison. If the server is compromised or the connection is intercepted (see S1), an attacker can deliver arbitrary code that runs with full JVM privileges on next launch.
- **Impact**: Remote Code Execution via malicious JAR replacement. Since certificate pinning is also disabled, this is doubly exploitable.
- **Fix**:
  1. The server should return a SHA-256 hash of the JAR alongside the download URL
  2. After download, verify the hash before renaming from `.tmp` to `.jar`
  3. Ideally, use a detached signature (Ed25519 or RSA) verified against a hardcoded public key

#### [S3] Auto-Updater Uses Plain HTTP Connection (No Pinning)
- **File**: `src/client/java/dev/toba/client/api/utils/AutoUpdater.java:96-97`
- **Issue**: The update check and download use `new URL(urlStr).openConnection()` directly instead of `openPinnedConnection()` from TobaAuthenticator. Even if certificate pinning were enabled for auth, the updater bypasses it entirely.
- **Fix**: Route update requests through the same pinned connection factory, or implement a separate pinning layer for update endpoints.

### HIGH

#### [S4] JWT Not Cryptographically Verified Client-Side
- **File**: `src/client/java/dev/toba/client/api/utils/AuthManager.java:331-344`
- **Issue**: `parseJwtExpiry()` decodes the JWT payload to read the `exp` claim but never verifies the JWT signature. The `isAuthenticated()` method (line 43-49) checks token presence, expiry, and HWID match — but an attacker who can intercept/modify the response could forge a JWT with arbitrary expiry and HWID, bypassing auth.
- **Impact**: Combined with S1 (no cert pinning), a MITM attacker can forge auth responses with fake JWTs. Even without MITM, if the token is extracted from the config file on disk, it can be replayed or modified.
- **Fix**: Either verify the JWT signature client-side with a hardcoded public key, or treat the JWT as opaque and validate it server-side on each sensitive operation.

#### [S5] Lua Sandbox Lacks Resource Limits (DoS via Infinite Loop)
- **File**: `src/client/java/dev/toba/client/api/script/LuaScriptModule.java:257-269`
- **Issue**: The sandboxed Globals loads `BaseLib`, `PackageLib`, `Bit32Lib`, `TableLib`, `StringLib`, `CoroutineLib`, and `JseMathLib`, but sets no instruction count limit or timeout. A malicious or buggy Lua script with `while true do end` will freeze the game thread permanently (onTick runs on the main client thread).
- **Impact**: Denial of service — game becomes unresponsive and requires force-kill.
- **Fix**: Use LuaJ's `LuaThread.setMaxInstructions()` or implement a `DebugLib` hook with an instruction counter that throws after N instructions per tick/render call.

#### [S6] Lua Scripts Can Send Arbitrary Chat Messages and Commands
- **File**: `src/client/java/dev/toba/client/api/script/LuaAPI.java:260-267`
- **Issue**: `chat.send()` and `chat.command()` allow scripts to send ANY chat message or execute ANY command (including `/pay`, `/trade`, `/msg`, etc.) without restriction. A malicious script downloaded by the user could silently transfer in-game currency, send messages to other players, or execute server commands.
- **Impact**: In-game financial loss, reputation damage, potential server bans.
- **Fix**: Consider a command whitelist/blacklist, rate limiting, or a confirmation prompt for sensitive commands like `/pay`, `/trade`, `/transfer`.

#### [S7] Lua Scripts Can Control All Modules (Including Disabling Security Modules)
- **File**: `src/client/java/dev/toba/client/api/script/LuaAPI.java:276-336`
- **Issue**: The `modules` API allows scripts to `enable()`, `disable()`, or `toggle()` any module and change any setting. A malicious script could disable anti-cheat detection modules, change pathfinding targets, or modify other module settings silently.
- **Fix**: Consider making certain modules script-inaccessible or requiring explicit user consent for module state changes from scripts.

### MEDIUM

#### [S8] HWID Includes User-Changeable Components
- **File**: `src/client/java/dev/toba/client/api/utils/HWIDUtil.java:13-55`
- **Issue**: The HWID includes `user.name` (OS username), which can be changed by the user. It also includes `COMPUTERNAME` environment variable, which is user-modifiable. While the MAC addresses provide hardware grounding, a user could potentially generate a different HWID by changing their username.
- **Impact**: Possible auth bypass or multi-device spoofing.
- **Severity**: Medium — MAC addresses provide reasonable hardware binding, but the user-changeable components add noise.

#### [S9] DEV_AUTH Bypass Flag in Production Code
- **File**: `src/client/java/dev/toba/client/api/utils/AuthManager.java:19`
- **Issue**: `public static final boolean DEV_AUTH = false;` — while currently false, this flag exists in production code. If accidentally set to true (or modified via bytecode patching), it bypasses ALL authentication. The constant is `public` so it's accessible from any class.
- **Impact**: If set to true, complete auth bypass. Currently mitigated by being false.
- **Fix**: Remove the flag from release builds entirely (use build-time conditional compilation), or make it `private` and add integrity checks.

#### [S10] devauth.enabled=true in build.gradle (Uncommitted)
- **File**: `build.gradle:25`
- **Issue**: The uncommitted diff shows `vmArg "-Ddevauth.enabled=true"` which enables DevAuth for development. If this is committed and shipped in a production build, it could affect auth behavior.
- **Fix**: Ensure this is reverted to `false` before any release build.

#### [S11] Config File Stores Encrypted Credentials — Key Derivation is Deterministic
- **File**: `src/client/java/dev/toba/client/api/config/TobaConfig.java:266-269`
- **Issue**: Credentials are encrypted with AES-256-GCM using a key derived from `SHA-256("toba-cfg-v1:" + HWID)`. The prefix "toba-cfg-v1" is hardcoded, and the HWID is deterministic for a given machine. An attacker with access to the machine (or the HWID) can derive the key and decrypt the config.
- **Impact**: Medium — this is defense-in-depth (protects against casual config file theft), but not effective against a targeted attacker with machine access.
- **Note**: This is actually reasonable design for a client-side config — there's no way to have a truly secret key on a machine the user controls. The encryption prevents casual copying of configs between machines.

#### [S12] PackageLib Loaded in Lua Sandbox
- **File**: `src/client/java/dev/toba/client/api/script/LuaScriptModule.java:260`
- **Issue**: `PackageLib` is loaded in the sandbox. While `IoLib` and `OsLib` are correctly excluded, `PackageLib` enables `require()` which could load other Lua files from the filesystem depending on `package.path`. A script could potentially `require` files outside the scripts directory.
- **Impact**: Possible file read access beyond the scripts directory.
- **Fix**: Either remove PackageLib or configure `package.path` to only include the scripts directory.

### LOW

#### [S13] Error Messages May Leak Internal Paths
- **File**: Multiple locations (LuaScriptLoader, TobaConfig, AutoUpdater)
- **Issue**: Error messages sent to chat or stderr include full file paths (e.g., `file.getAbsolutePath()`), which expose the user's username and directory structure.
- **Fix**: Sanitize paths in user-facing error messages.

#### [S14] Apache HttpClient 4.5.14 as Runtime Dependency
- **File**: `build.gradle:61`
- **Issue**: `runtimeOnly "org.apache.httpcomponents:httpclient:4.5.14"` — this library has known CVEs in older versions. Version 4.5.14 is the latest 4.x but the library is in maintenance mode. The codebase uses `HttpURLConnection` directly rather than this library, so it may be an unused dependency.
- **Fix**: Verify if this dependency is actually used. If not, remove it. If yes, consider migrating to `httpclient5`.

#### [S13b] Command Injection Risk in FailsafeWarning TTS
- **File**: `src/client/java/dev/toba/client/api/failsafes/FailsafeWarning.java:219`
- **Issue**: `speakTTS()` constructs a PowerShell command via string concatenation. Currently only called with hardcoded text, but if any future path passes user-controlled data, PowerShell injection is trivial. On macOS, `say` command has no escaping at all.
- **Fix**: Use `ProcessBuilder` argument arrays instead of string concatenation for all shell commands.

#### [S13c] Unvendored JAR Dependency (EventManager.jar)
- **File**: `build.gradle:51`, `libs/EventManager.jar`
- **Issue**: Local JAR with no version tracking, no checksum verification, no source attribution. Opaque binary dependency.
- **Fix**: Publish to a Maven repo with proper versioning, or document source and include SHA-256 checksum.

### INFO

#### [S15] Good Practices Observed
- Lua sandbox correctly excludes `OsLib`, `IoLib`, and `LuajavaLib` (prevents file/process/reflection access)
- Config credentials are encrypted at rest with AES-256-GCM
- Auth uses JWT with expiry checking and HWID binding
- HWID generation uses SHA-256 hashing with multiple hardware identifiers including MAC addresses
- Network timeouts are set on all HTTP connections (10s connect, 10s read)
- SPKI certificate pinning infrastructure exists (just needs to be populated)
- Auto-updater uses atomic file operations (.tmp then rename)
- Auth tokens are cleared on failure; credentials are invalidated on persistent auth errors

---

## 2. LUA SCRIPTING — WHY SCRIPTS DON'T FUNCTION

### Root Cause Analysis

The scripts in `scripts/` (project root) and `run/config/toba/scripts/` (runtime) use APIs that were **recently added but may not be compiled into the running mod**.

### Issue 1: `screen` API is Uncommitted (Hub1Begger depends on it)

The `screen` table (`screen.isOpen()`, `screen.getTitle()`, `screen.getSlot()`, `screen.click()`, `screen.close()`, `screen.getSlotCount()`) was added to `LuaAPI.java` in uncommitted changes (visible in `git diff`). The `Hub1Begger.lua` script heavily depends on this API for:
- `screen.isOpen()` — checking if a GUI is open (lines 287, 441, 484, 521)
- `screen.getSlot(slot)` — reading item data from chest GUIs (line 309)
- `screen.click(slot)` — clicking slots in GUIs (lines 353, 503)
- `screen.close()` — closing GUIs (lines 344, 361)
- `screen.getSlotCount()` — reading container size (line 302)
- `screen.getTitle()` — reading GUI title (line 442)

**If the mod was not rebuilt after adding the `screen` table, it won't exist at runtime, and Hub1Begger will crash with "attempt to index nil value 'screen'" on any tick that tries to access it.**

### Issue 2: `onChat` Callback is Uncommitted (Hub1Begger depends on it)

The `onChat` callback support was added to `LuaScriptModule.java` in uncommitted changes:
- `onChatFn` field (line 31)
- `fnOrNull("onChat")` lookup (line 49)
- `onChatMessage()` dispatch method (lines 70-77)

The `ChatHudMixin.java` that feeds chat messages to `LuaAPI.onChatMessage()` is also a **new untracked file** (`?? src/client/java/dev/toba/client/mixin/ChatHudMixin.java`).

**Hub1Begger's `onChat(message)` function (line 167) will never fire if either the mixin or the callback support isn't compiled.**

### Issue 3: Script Directory Confusion

- **Scripts in project root**: `<project>/scripts/` — these are source/reference copies
- **Scripts loaded at runtime**: `<run>/config/toba/scripts/` — this is where `LuaScriptLoader` reads from

Both directories contain the same scripts currently. But if the user edits `scripts/AutoHeal.lua` and expects changes to take effect, they won't — the mod loads from `config/toba/scripts/`.

### Issue 4: AutoHeal and AutoRogueSword Should Work (API Matches)

These two scripts only use APIs that existed in the COMMITTED codebase:
- `player.health()`, `player.velocityX/Z()`, `player.statusEffects()` — all in playerTable
- `inventory.getSlot()`, `inventory.selectSlot()`, `inventory.getSelectedSlot()` — all in inventoryTable
- `action.useItem()` — in actionTable
- `chat.info()` — in chatTable
- `settings.get()`, `settings.addFloat/String/Integer/Boolean()` — in LuaScriptModule
- `imgui.*` — in imguiTable

**If these scripts don't work either, the issue is likely that the mod hasn't been rebuilt at all since the last committed version, or there's a build error preventing compilation.**

### Fix Checklist

1. **Rebuild the mod**: `./gradlew build` — ensure all uncommitted Java changes are compiled
2. **Verify no compile errors**: Check build output for errors in LuaAPI.java, LuaScriptModule.java, ChatHudMixin.java
3. **Ensure ChatHudMixin is registered**: Confirmed in `toba.client.mixins.json` line 19 — but verify the class compiles
4. **Test scripts in runtime directory**: Ensure scripts are in `run/config/toba/scripts/`, not just `scripts/`
5. **Check in-game chat for Lua errors**: LuaScriptModule catches LuaError and prints to chat via `TobaChat.error()`
6. **Add instruction limit** to sandbox to prevent infinite loop freezes

---

## 3. ARCHITECTURE QUICK REFERENCE

### Key File Paths

| File | Purpose |
|------|---------|
| `api/script/LuaAPI.java` | All Lua API bindings (player, world, chat, screen, etc.) |
| `api/script/LuaScriptModule.java` | Module wrapper for Lua scripts, sandbox creation |
| `api/script/LuaScriptLoader.java` | Loads .lua files from config/toba/scripts/ |
| `api/utils/AuthManager.java` | JWT session management, auth flow orchestration |
| `api/utils/TobaAuthenticator.java` | HTTP auth/link requests, SPKI pinning infrastructure |
| `api/utils/AutoUpdater.java` | Version check and JAR download/replacement |
| `api/utils/HWIDUtil.java` | Hardware fingerprint generation (SHA-256) |
| `api/config/TobaConfig.java` | Config persistence with AES-256-GCM credential encryption |
| `mixin/ChatHudMixin.java` | Intercepts chat messages for Lua onChat callback |
| `screens/gui/auth/AuthOverlay.java` | In-game re-link overlay (ImGui) |

### Lua API Surface (bound in `LuaAPI.bind()`)

| Table | Key Functions |
|-------|--------------|
| `player` | x/y/z, health, velocity, statusEffects, dimension, heldItem |
| `world` | getBlock, isAir, isSolid, getBiome, time, weather |
| `game` | fps, windowWidth/Height, serverAddress |
| `chat` | send, command, info, error |
| `screen` | isOpen, getTitle, close, getSlot, click, shiftClick, getSlotCount |
| `imgui` | begin/finish, text, textColored, button, Flags |
| `modules` | get, list → enable/disable/toggle, getSetting/setSetting |
| `rotation` | setTarget, lookAt, lookAtEntity, clearTarget, inFOV |
| `pathfinder` | goto, stop, isPathing |
| `esp` | addBlock, addEntity, addBox, clear |
| `entities` | getAll, getPlayers, getMobs, getPassive, getClosest, getById |
| `inventory` | getSlot, selectSlot, getSelectedSlot, findItem, getArmor |
| `action` | attack, useItem, swingHand, interactEntity, interactBlock |
| `movement` | forward/backward/left/right/jump/sneak/sprint, stopAll |
| `input` | isKeyDown + KEY_* constants |
| `tablist` | SkyBlock-specific data (area, purse, skills, pets, etc.) |
| `bazaar` | getSellPrice, getBuyPrice, volumes, product ID constants |
| `scoreboard` | getTitle, getLines, getLine |
| `settings` | addBoolean/Float/Integer/Mode/Color/String, get, set |

### Auth Flow
1. `TobaConfig.loadCredentials()` → decrypt userId/token from AES-256-GCM config
2. `AuthManager.init()` → if credentials exist, `forceAuthenticate(false)`; else show linking dialog
3. `TobaAuthenticator.authenticate()` → POST to server with userId, HWID, mcUsername
4. Server returns JWT → `AuthManager` extracts expiry, binds to HWID
5. `isAuthenticated()` checks: token exists + not expired + HWID matches
6. On failure: credentials invalidated, AuthOverlay shows re-link UI
7. After auth success: `AutoUpdater.checkAsync()` runs

### Dependency Versions (from build.gradle)
- LuaJ: 3.0.1 (`org.luaj:luaj-jse`)
- ImGui: version from `project.imgui_version`
- MixinExtras: 0.5.3
- Apache HttpClient: 4.5.14 (runtime only, possibly unused)
- Fabric Loom: 1.15-SNAPSHOT
