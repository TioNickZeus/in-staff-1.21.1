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