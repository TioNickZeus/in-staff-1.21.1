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