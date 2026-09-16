# Changelog

All notable changes to the In-Staff mod will be documented in this file.

## [1.0.0] - Unreleased

### Initial Setup
- Initial project scaffolding for Minecraft 1.21.1 on NeoForge 21.1.250.
- Standardized package hierarchy under `com.tio.instaff`.
- Baseline configuration, governance documents, and build pipeline established.

### Architecture Pivot (Client + Server)
- Redesigned mod as dual-sided (client + server required). Vanilla clients not supported.
- Created `InStaffClient.java` client entrypoint (`@Mod(dist = Dist.CLIENT)`).
- Added `client/screen/` and `client/integrity/` package structure.
- Changed config type from `COMMON` to `SERVER` (server-authoritative moderation rules).
- Updated `ARCHITECTURE.md` with full client+server package map, side distribution table, and network handshake lifecycle (§3.5).
- Updated `AGENT.md` with client+server invariants and side separation rules.
- Added `displayURL` and `issueTrackerURL` to `neoforge.mods.toml`.

### Offline Server (`online-mode=false`) Architectural Invariants
- Formalized offline mode compatibility in `AGENT.md` and `ARCHITECTURE.md`.
- Prohibited external Mojang HTTP API calls (`api.mojang.com`) for UUID resolution to prevent identity conflicts.
- Standardized offline player lookups via `server.getProfileCache()` and `UUIDUtil.createOfflinePlayerUUID(name)`.
- Specified offline ban evasion mitigations combining IP tracking and client-side installation token binding.
- Updated `ROADMAP.md` with offline mode milestone deliverables.

### Added (Batch 1: Core Utilities, Config & Localization)
- Implemented comprehensive server-authoritative configuration specification in `InStaffConfig.java` with defensive accessors (`SPEC.isLoaded()`).
- Added `LocalizationHelper.java` supporting graceful fallback, prefixing, safe argument sanitization, and raw string translation.
- Added foundational localization keys in `en_us.json` and `pt_br.json`.
- Implemented `DurationParser.java` supporting monotonic time (`Util.getMillis()`), permanent durations, units (`s`, `m`, `h`, `d`, `w`, `mo`, `y`), composite strings, and duration formatting.
- Implemented `TextUtil.java` with color code translation (`&` to `§`), formatting stripping, epoch formatting, and safe identity formatting.
- Added comprehensive unit test suite in `Lote1CoreTest.java` (12 tests, 100% pass rate).

### Added (Batch 2: Data Persistence & State Managers)
- Implemented `FileStorageUtil.java` providing atomic `.tmp` file swaps (`Files.move(REPLACE_EXISTING)`), thread-safe JSON serialization, corrupt file backup, and zero server crash policy.
- Created `PlayerResolver.java` enforcing local deterministic UUID lookups (`UUIDUtil.createOfflinePlayerUUID`) and local `GameProfileCache` queries with zero external Mojang HTTP calls.
- Implemented `PunishmentRecord.java` POJO tracking audit metadata, expirations, and offline evasion detection fields (IP + client installation tokens).
- Built `PunishmentManager.java` thread-safe moderation engine indexing active bans, mutes, freezes, and IP/token lookups with atomic persistence to `instaff/punishments_history.json`.
- Built `MaintenanceManager.java` dynamic maintenance access controller supporting staff bypass and atomic persistence to `instaff/maintenance.json`.
- Built `WhitelistManager.java` smart whitelist manager supporting dynamic reloads and atomic persistence to `instaff/whitelist.json`.
- Built `PlaytimeTracker.java` tracking monotonic session durations (`Util.getMillis()`), historical totals, and last-seen telemetry in `instaff/playtime.json`.
- Added comprehensive unit test suite in `Lote2PersistenceTest.java` (22 total tests across Lote 1 and 2, 100% pass rate).

### Added (Batch 3: Event Handlers, Freeze & Item Protections)
- Implemented `ModerationEventHandler.java` enforcing login checks (maintenance mode, dynamic whitelist, UUID & IP ban evasion detection), chat mute interception, and player freeze mechanics (position clamping, interaction denials, periodic notification).
- Implemented `BanItemMode.java` enum defining `TOTAL`, `NO_USE`, and `NO_PLACE` restriction levels.
- Built `BanItemManager.java` with thread-safe in-memory index and atomic persistence to `instaff/banned_items.json`.
- Implemented `ProtectionEventHandler.java` intercepting `RightClickItem`, `RightClickBlock`, `EntityPlaceEvent`, `ItemEntityPickupEvent.Pre` with `TriState.FALSE`, and periodic inventory scans for total confiscation.
- Built `ChunkQuarantineHandler.java` providing zero-crash defensive shields for corrupted entities and block entities with persistent logging in `instaff/quarantine.log`.
- Added localized messages for whitelist kicks and ban-item denials in `en_us.json` and `pt_br.json`.
- Added comprehensive unit test suite in `Lote3ProtectionTest.java` (26 total tests across Lotes 1, 2, and 3, 100% pass rate).

### Added (Batch 4: Inspection Engine & Offline NBT)
- Implemented `ModMenus.java` registering `invsee` and `endersee` menu types using `IMenuTypeExtension`.
- Implemented `InvseeMenu.java` providing full 41-slot container inspection (armor, offhand, main, and hotbar) with bidirectional shift-click transfer.
- Implemented `EnderseeMenu.java` providing 27-slot Ender Chest inspection with bidirectional shift-click transfer.
- Implemented `OfflinePlayerDataHelper.java` reading and writing `world/playerdata/<UUID>.dat` NBT directly with atomic `.tmp` swaps upon menu closure.
- Implemented client screens `InvseeScreen.java` and `EnderseeScreen.java` under `com.tio.instaff.client.screen`, registered via `RegisterMenuScreensEvent` in `InStaffClient.java` with strict side separation.
- Added comprehensive unit test suite in `Lote4InspectionTest.java` (29 total tests across Lotes 1-4, 100% pass rate).

### Added (Batch 5: Network Payloads & Client Integrity)
- Implemented `IntegrityRequestPayload.java` and `IntegrityResponsePayload.java` with CustomPacketPayload and stream codecs.
- Implemented `InStaffNetwork.java` registering payloads via `RegisterPayloadHandlersEvent` on mod event bus with decoupled client delegate to preserve strict side separation.
- Built `ClientHashScanner.java` computing SHA-256 for all `.jar` files in `mods/`, collecting loaded mod IDs, and persisting unique installation tokens in `.instaff_token`.
- Built `ServerIntegrityValidator.java` enforcing required mods, blacklisted mod IDs (`xray`, `baritone`, `freecam`), blacklisted SHA-256 hashes, offline ban evasion detection via client token matching, and handshake timeout watchdogs.
- Connected integrity handshake initiation and watchdog tick into `ModerationEventHandler.java`.
- Added comprehensive unit test suite in `Lote5NetworkTest.java` (34 total tests across Lotes 1-5, 100% pass rate).

### Added (Batch 6: Commands & Final Exposure)
- Implemented `StaffCommand.java` providing `/staff` and `/instaff` administrative hubs with permission checks and help overviews.
- Implemented `PunishCommands.java` implementing all core moderation commands with full offline player support and tab-completion:
  - `/ban <player> [reason]` (permanent ban, kicks player, broadcasts punishment).
  - `/tempban <player> <duration> [reason]` (duration parsing, kicks player, broadcasts punishment).
  - `/unban <player>` (removes active ban in `PunishmentManager`).
  - `/mute <player> [reason]` (permanent chat mute, target notification).
  - `/tempmute <player> <duration> [reason]` (temporary mute with duration parsing, target notification).
  - `/unmute <player>` (removes active mute in `PunishmentManager`).
  - `/kick <player> [reason]` (online check, disconnects player, audits to history).
  - `/freeze <player>` (toggles freeze state, notifies target and staff).
- Implemented `MaintenanceCommand.java` with subcommands `on`, `off`, `status`, and `bypass add|remove <player>`.
- Implemented `WhitelistCommand.java` with subcommands `on`, `off`, `add <player>`, `remove <player>`, `list`, and `reload`.
- Implemented `InvseeCommand.java` registering `/invsee <player>` and `/endersee <player>` with live synchronization for online players and atomic `.tmp` offline playerdata NBT manipulation.
- Implemented `BanItemCommand.java` with subcommands `add <itemId> [mode]`, `remove <itemId>`, `list`, and `check [itemId]`. Added `fromString` parser to `BanItemMode.java`.
- Implemented `HistoryCommand.java` providing `/history <player>` and `/checkpunish <player>` with full audit trail formatting (date, staff, reason, active/expired/revoked state).
- Implemented `PlaytimeCommand.java` providing `/playtime` (self and player lookups) and `/seen <player>` (activity telemetry, last seen, first joined).
- Implemented `ModCommands.java` subscribing to `RegisterCommandsEvent` on `NeoForge.EVENT_BUS` to register all commands cleanly.
- Updated language dictionaries in `en_us.json` and `pt_br.json` with complete localization for all commands and status tokens.
- Added comprehensive unit test suite in `Lote6CommandsTest.java` (47 total tests across all batches, 100% pass rate).

### Fixed (Code Review: Batches 5 & 6)
- **Ban evasion token binding now works.** `PunishmentRecord.clientToken` was never populated (every construction passed `null`), so `PunishmentManager.getActiveBanByClientToken` could never match and the offline ban evasion deterrent was inert. `ServerIntegrityValidator` now retains the last installation token reported per player UUID (exposed via `getKnownClientToken`), `PunishmentManager.findLastKnownClientToken` provides a history fallback, and `/ban`, `/tempban` and `/kick` bind the token onto the record.
- **`/banitem` accepts namespaced item IDs.** The item argument used `StringArgumentType.word()`, which cannot contain `:`, so `/banitem add minecraft:bedrock` failed to parse while `BanItemManager` keys its rules on the full `namespace:path` string. Switched `add`, `remove` and `check` to `ResourceLocationArgument.id()` and added registry validation plus a `not_banned` failure path for `remove`.
- **Hardened `IntegrityResponsePayload` decoding.** The client-supplied element count was unbounded and used to pre-size collections, letting a crafted packet trigger an `OutOfMemoryError` on the server. Counts are now bounded by `MAX_ENTRIES` and by the readable bytes remaining, strings are length-capped, and an invalid count raises `DecoderException`.
- **Added missing translation keys.** `instaff.punishment.kicked` and `instaff.punishment.unmuted` were referenced by `PunishCommands` but absent from both language files, so players saw the raw key on the kick screen.
- **Inspection menus close when the inspected player disconnects.** `stillValid` had been reduced to `player.isAlive()`, so a live `/invsee` or `/endersee` session stayed open after the target logged out and further edits were applied to a discarded `ServerPlayer` inventory and silently lost.
- **Offline inspection no longer clobbers live playerdata.** Offline `/invsee` and `/endersee` sessions are now exclusive per target UUID, and the save callback aborts (notifying staff) if the player reconnected while the snapshot was open.
- **Client integrity scan moved off the render thread.** Hashing every jar in `mods/` ran synchronously inside the payload handler, freezing large-modpack clients on join and risking a handshake timeout kick. The scan now runs on a dedicated daemon thread and the response is dispatched back on the client main thread.
- **`maintenance.maxTempBanDays` is enforced.** `/tempban` previously ignored the configured cap entirely.
- **Punishment announcements are no longer delivered twice to operators.** `sendSuccess(..., true)` and `broadcastSystemMessage` both reached operators; announcements now go out exactly once per recipient, with an audit log line.
- Extended the test suite with regression coverage for the argument type, payload bounds and language file parity.
