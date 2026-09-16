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