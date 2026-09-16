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