# 🗺️ In-Staff Roadmap & Feature Board

This document tracks planned features, current sprint goals, and technical milestones for **In-Staff** (Minecraft 1.21.1 / NeoForge).

---

## 🚀 Version 1.0.0 — Foundation & Moderation Suite

- [x] **Project Initialization & Scaffolding**: NeoForge 1.21.1 template cleaned, build scripts tuned, and architectural guidelines established.
- [ ] **Core Moderation Engine**:
  - [ ] UUID-based ban and temp-ban system with custom kick/rejection screen.
  - [ ] Chat mute and temp-mute system with remaining time alerts.
  - [ ] Player freeze mechanic restricting movement, interactions, and rotation.
  - [ ] Punishment audit history ledger (`/history <player>`).
  - [ ] Human duration parser (`1d`, `12h`, `30m`).
- [ ] **Access & Maintenance**:
  - [ ] Dynamic maintenance mode with custom MOTD and staff whitelist bypass.
  - [ ] In-game dynamic whitelist with live reload and custom kick messages.
  - [ ] Player playtime tracker and `/seen` last-login telemetry.
- [ ] **Inspection & Container Auditing**:
  - [ ] Live online `/invsee` with bidirectional synchronization.
  - [ ] Live online `/endersee` for Ender Chest inspection.
  - [ ] Offline player inventory loading and saving via `playerdata/<UUID>.dat`.
  - [ ] Curios accessory integration (soft dependency).
- [ ] **World Protection & Health**:
  - [ ] Ban-Item system with granular modes (`TOTAL`, `NO_USE`, `NO_PLACE`).
  - [ ] Safe-Unload / Chunk Quarantine for corrupted ticking block entities and entities.
- [ ] **Client Integrity (Anti-Cheat)**:
  - [ ] Network handshake verifying client mod and resource pack hashes.