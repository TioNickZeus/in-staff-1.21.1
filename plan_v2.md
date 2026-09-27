# Implementation Plan - Mute Bypass & Pre-Join Disconnection

> **Sequencing note**: These are two unrelated concerns (per `AGENT.md` §4.6, "One Concern Per Change") and will be implemented, tested, and merged **sequentially, not in parallel**:
> 1. **Part 1 (Mute Command Bypass)** is implemented first, fully tested in-game, and merged/closed before Part 2 begins.
> 2. **Part 2 (Pre-Join Disconnection)** only starts after Part 1 is confirmed working. It carries more architectural risk (see §2.1 below) and benefits from not being rushed alongside another change.
> 3. **Phase 2 (Deterministic Client Token)** remains a separate future PR after both of the above, as already noted.
>
> Each part should be its own branch and its own PR (`feat/mute-command-bypass`, then `feat/prejoin-disconnect`), not a single combined diff.

---

## 1. Mute Command Bypass

**Status**: Ready to implement first.

**Goal**: Intercept `CommandEvent` to prevent muted players from using specific chat-related commands (`/msg`, `/tell`, etc.).

**Target File 1**: `c:\Users\Rian\Documents\GitHub\in-staff-1.21.1\src\main\java\com\tio\instaff\config\InStaffConfig.java`
**Changes**:
1. Add a new `ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_MUTE_COMMANDS` in the `moderation` block.
2. Initialize it with default values: `"msg", "tell", "w", "r", "reply", "me", "g", "global"`. (**Correction**: `/me` is back in the default list — confirmed against `KNOWN_ISSUES.md` §5 "Command Chat Bypass," which already lists `/me` as a known mute-bypass vector, since unlike `/say` it doesn't require OP. An earlier draft of this plan incorrectly dropped it; that was wrong.)
3. Create a public getter `getBlockedMuteCommands()` following the project's existing config-access pattern — guard with `SPEC.isLoaded()` like other getters in the file, e.g.:
   ```java
   public static List<? extends String> getBlockedMuteCommands() {
       return SPEC.isLoaded() ? BLOCKED_MUTE_COMMANDS.get() : List.of();
   }
   ```

**Target File 2**: `c:\Users\Rian\Documents\GitHub\in-staff-1.21.1\src\main\java\com\tio\instaff\moderation\ModerationEventHandler.java`
**Changes**:
1. Add a new `@SubscribeEvent` method `onCommandEvent(net.neoforged.neoforge.event.CommandEvent event)`.
2. Resolve the executing entity defensively before anything else — not every `CommandSourceStack` belongs to a player (console, command blocks):
   ```java
   if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) {
       return;
   }
   ```
3. Check if they have an active mute via `PunishmentManager.getInstance().getActiveMute(uuid)`.
4. If muted, defensively retrieve the root command node name — guard against an empty node list (e.g. malformed/empty input) before indexing:
   ```java
   var nodes = event.getParseResults().getContext().getNodes();
   if (nodes.isEmpty()) return;
   String commandName = nodes.get(0).getNode().getName();
   ```
5. Normalize the command name before comparing against the blocked list: lowercase it (`toLowerCase(Locale.ROOT)`) and strip a leading `minecraft:` namespace prefix if present, so `/minecraft:msg` can't bypass the check.
6. Check if the blocked list from config contains this normalized command name.
7. If yes, call `event.setCanceled(true)` and send the mute message **via `LocalizationHelper`**, reusing the same translation key already used for the `ServerChatEvent` mute block (per `AGENT.md` Invariant 8 — no new hardcoded/duplicate string for a message that already exists elsewhere).

---

## 2. Pre-Join Disconnection (Early Kick)

**Status**: Do not start until Part 1 is merged and tested.

**Goal**: Kick banned players before the "Player joined the game" broadcast and before they are spawned in the world.

### 2.1 Why `RegisterConfigurationTasksEvent`, not Mixin

This decision was researched and reconsidered before committing to an approach — documented here so the reasoning doesn't get lost or re-litigated later.

**What was considered:**
- **Mixin**, injecting into `ServerLoginPacketListenerImpl#handleAcceptedLogin` (or `verifyLoginAndFinishConnectionSetup`) — cuts in at the exact moment vanilla itself validates the profile, closest to how vanilla's own `PlayerList#canPlayerLogin` works.
- **`net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent`** — a native NeoForge event that fires during the network **Configuration phase**, which happens after login/profile validation but still before the Play phase (before world spawn and the "joined the game" broadcast). It doesn't implement `ICancellableEvent`; disconnecting requires registering an `ICustomConfigurationTask` via `event.register(...)` and calling `listener.connection.disconnect(kickMessage)` from within that task's `run()`.

**Why the native event wins here:**
1. **Consistency with the rest of the codebase.** Every other subsystem in In-Staff (`moderation/`, `access/`, `protection/`) is built entirely on NeoForge's `@SubscribeEvent` model. Mixin would be a brand-new pattern introduced into the project — and per `AGENT.md` §4.5 ("Match Existing Patterns, Don't Impose New Ones"), a new pattern needs a real justification, not just "it's a cleaner cut." Here, a native event exists that satisfies the functional requirement (disconnects before Play phase, before spawn, before the join broadcast), so there's no forcing function to introduce Mixin.
2. **Mixin is the most common source of a mod breaking on a Minecraft version bump.** A NeoForge event is a stable public API; a Mixin targets a specific method signature under a specific obfuscation mapping for that exact version. Every Minecraft update turns every existing Mixin into an audit item (`NoSuchMethodError` risk) — a NeoForge event usually just needs a recompile. For a small/solo-maintained mod, this is a recurring maintenance cost, not a one-time one.
3. **Mixin failure modes are silent and hard to debug.** A misconfigured `mixins.json` package path, or a missing `refmap.json` at production build time (vs. working fine in the IDE dev environment), fails with obscure errors (`NoSuchMethodError`) discovered only after a real `./gradlew build`, not during normal dev iteration.
4. **Considered and rejected: introducing Mixin now "since we'll need the ecosystem anyway" for other subsystems** (file/hash integrity scanning, the installation token). Neither of those needs Mixin — both are plain file I/O with no game behavior to intercept. The only genuine Mixin candidate among the three was this ban-check timing, and the native event already covers it. Expanding Mixin's footprint "since we're already setting it up" was explicitly rejected as scope creep that adds ongoing maintenance cost without a matching benefit.

**Trade-off being accepted:** the Configuration phase is semantically intended for syncing registries, resource packs, and custom data — not for ban enforcement. Using it this way works, but is a secondary use of the API, and requires a bit more boilerplate (a task class) than a one-line Mixin injection would. This is accepted in exchange for staying on a stable, project-consistent API.

**If this changes**: if a future requirement appears that NeoForge's event API genuinely cannot satisfy (not "less convenient," but "not possible"), Mixin becomes the right call at that point — the objection above is to introducing it *now*, for *this*, not a permanent ban on ever using it.

### 2.2 Before writing code: validate on the dummy project

- Confirm from the NeoForge source (already available locally as decompiled sources) that `RegisterConfigurationTasksEvent` indeed does not implement `ICancellableEvent`, and read the exact `ICustomConfigurationTask` interface / `run()` signature before coding against it.
- On the dummy mod, verify that calling `listener.connection.disconnect(...)` mid-Configuration-phase from inside a custom task doesn't leave any other pending configuration task (registry sync, resource pack negotiation, etc.) in a bad state.
- Confirm the event fires on the **Mod Event Bus**, not `NeoForge.EVENT_BUS` (per the earlier research) — get the registration location right the first time.

### 2.2a Confirmed via `javap` against the actual decompiled NeoForge/Minecraft classes (not just documentation/inference)

These were verified by running `javap` directly on the classes in this project's Gradle cache, not just described from general knowledge — treat them as higher-confidence than the rest of this plan, but still confirm the exact method signature in the IDE before writing code against it, since the raw `javap` output wasn't independently reviewed outside the IDE session:

- **`RegisterConfigurationTasksEvent` implements `IModBusEvent`**, not a `NeoForge.EVENT_BUS` event. If subscribing via `@EventBusSubscriber`, it must declare `bus = EventBusSubscriber.Bus.MOD` explicitly, or the listener silently never fires. If registering manually, use the mod event bus (`modBus.addListener(...)`), not `NeoForge.EVENT_BUS.addListener(...)`.
- **Critical — task completion is not automatic.** If the player is *not* banned, the task must explicitly call `listener.finishCurrentTask(this.type())` (confirm exact signature in-IDE) to signal completion. Skipping this leaves the connecting client stuck on the negotiation screen until it times out — this is the single most important correctness requirement of this whole approach and needs to be verified working on the dummy before touching the real codebase.
- If the player *is* banned, `listener.disconnect(kickMessage)` is sufficient — no need to call `finishCurrentTask` in that branch.
- **Wrap the task's ban-check logic in `try-catch (Throwable t)`.** An I/O failure while checking `PunishmentManager` should never be allowed to hang or crash the network pipeline — falls under `AGENT.md` Invariant 1 (never crash / never hang the connection pipeline).
- **Identity/IP without `ServerPlayer`**: `ServerPlayer` doesn't exist yet at this phase. `ServerConfigurationPacketListenerImpl` extends `ServerCommonPacketListenerImpl`, which exposes what's needed directly: `listener.getOwner().getId()` / `.getName()` for the profile, and `listener.getConnection().getRemoteAddress()` for the IP. This keeps the check aligned with Invariant 5 (local, deterministic identity resolution — no `ServerPlayer` dependency needed).

### 2.3 Decision: maintenance/whitelist stay on `PlayerLoggedInEvent` for now (resolved)

Only the ban check moves to the Configuration phase in this PR. Maintenance mode and whitelist rejection **stay where they are** (`PlayerLoggedInEvent`), not because of laziness but for a concrete technical reason: `AccessEventHandler.java`'s bypass check uses `player.hasPermissions(2)`, which requires a `ServerPlayer` instance that doesn't exist yet during Configuration phase. Moving those checks too would require querying `server.getProfilePermissions(profile)` instead — a real, working alternative, but extra surface area and testing for a feature this PR doesn't need to deliver. Revisit as a separate, explicit PR if the "joined and immediately kicked" log noise for maintenance/whitelist rejections becomes an actual complaint, not preemptively.

**Target File 1**: `c:\Users\Rian\Documents\GitHub\in-staff-1.21.1\src\main\java\com\tio\instaff\network\...` (exact package TBD after dummy validation) — new `ICustomConfigurationTask` implementation, e.g. `PunishmentCheckConfigurationTask.java`.
**Changes**:
1. Implement `ICustomConfigurationTask`, holding a reference to the `ServerConfigurationPacketListenerImpl`.
2. In `run()` (confirm exact interface method in-IDE per §2.2a), wrap the whole body in `try-catch (Throwable t)`.
3. Resolve identity via `listener.getOwner().getId()` / `.getName()` and IP via `listener.getConnection().getRemoteAddress()` — no `ServerPlayer` needed at this phase.
4. Check `PunishmentManager` for an active ban on UUID or IP.
5. If banned: call `listener.disconnect(kickMessage)` (localized via `LocalizationHelper`).
6. If not banned: call `listener.finishCurrentTask(this.type())` (confirm exact signature in-IDE) — **do not skip this**, or the connection hangs until timeout (see §2.2a).
7. In the `catch` block: log the failure via `InStaff.LOGGER.error` and fail safe — decide explicitly whether "fail safe" means allowing the connection through (availability-favoring) or disconnecting (security-favoring) on an unexpected I/O error, and document that choice in the PR description.

**Target File 2**: wherever the registration listener lives (likely alongside `InStaffNetwork.java` or a new small class in `network/`)
**Changes**:
1. Subscribe to `RegisterConfigurationTasksEvent` **on the Mod Event Bus** (`bus = EventBusSubscriber.Bus.MOD` if using `@EventBusSubscriber`, or `modBus.addListener(...)` if registering manually — see §2.2a, this event does not fire on `NeoForge.EVENT_BUS`), registering the task from Target File 1 via `event.register(...)`.

**Target File 3**: `c:\Users\Rian\Documents\GitHub\in-staff-1.21.1\src\main\java\com\tio\instaff\moderation\ModerationEventHandler.java`
**Changes**:
1. Remove the current `PlayerLoggedInEvent` ban check (now handled earlier, in Configuration phase). Maintenance and whitelist checks remain untouched — see §2.3.

**No longer needed** (removed from this plan): `instaff.mixins.json`, any `mixin/` package, `neoforge.mods.toml` mixin registration, and any Mixin-related `build.gradle`/annotation-processor changes — these were part of the earlier Mixin-based draft and are dropped per §2.1.

---

## 3. Phase 2 (Experimental / Future PR)
**Status**: After Part 1 and Part 2 are both merged and stable. Not started until then.

**Goal**: Implement the Deterministic Client Token (Machine ID Fallback) to deter ban evasion.
*As requested by the user, this feature is kept separate from the current PR to minimize risk and ensure the 2 main features are merged cleanly first.*

**Implementation Strategy**:
- Create `MachineIdFetcher` utility in `client/` to read OS-specific machine identifiers (e.g. `ProcessBuilder` for `reg query` on Windows, `/etc/machine-id` on Linux).
- Hash the retrieved identifier using SHA-256 for privacy.
- **Anti-freeze safety**: any `ProcessBuilder`-based lookup (e.g. `reg query` on Windows) must use a bounded wait — `process.waitFor(1, TimeUnit.SECONDS)` — with immediate fallback to random generation if the subprocess doesn't respond in time. Never let a hung OS subprocess block client startup.
- Fallback to standard SecureRandom UUID generation if reading the OS fails or times out.
- Modify the client-side `.instaff_token` generation logic to use this fetcher when a token is missing.