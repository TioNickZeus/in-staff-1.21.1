# In-Staff Roadmap & Feature Board

This document tracks planned features, current sprint goals, and technical milestones for **In-Staff** (Minecraft 1.21.1 / NeoForge).

---

## Version 1.0.0 — Foundation & Moderation Suite

- [x] **Project Initialization & Scaffolding**: NeoForge 1.21.1 template cleaned, build scripts tuned, and architectural guidelines established.
- [x] **Client + Server Architecture**: Dual-sided mod with `InStaffClient.java` client entrypoint, `client/` package structure, and side separation rules.
- [x] **Core Moderation Engine**:
  - [x] UUID-based ban and temp-ban system with custom kick/rejection screen.
  - [x] Chat mute and temp-mute system with remaining time alerts.
  - [x] Player freeze mechanic restricting movement, interactions, and rotation (server + client enforcement).
  - [x] Punishment audit history ledger (`/history <player>`).
  - [x] Human duration parser (`1d`, `12h`, `30m`).
  - [x] Offline server (`online-mode=false`) compatibility (local `UUIDUtil.createOfflinePlayerUUID` and `GameProfileCache` resolution, zero Mojang HTTP queries).
  - [x] Offline ban evasion mitigation (dual IP + client installation token binding).
- [x] **Access & Maintenance**:
  - [x] Dynamic maintenance mode with custom MOTD and staff whitelist bypass.
  - [x] In-game dynamic whitelist with live reload and custom kick messages.
  - [x] Player playtime tracker and `/seen` last-login telemetry.
- [x] **Inspection & Container Auditing**:
  - [x] Live online `/invsee` with bidirectional synchronization.
  - [x] Client-side `InvseeScreen` rendering.
  - [x] Live online `/endersee` for Ender Chest inspection.
  - [x] Client-side `EnderseeScreen` rendering.
  - [x] Offline player inventory loading and saving via `playerdata/<UUID>.dat`.
  - [ ] Curios accessory integration (soft dependency).
- [x] **World Protection & Health**:
  - [x] Ban-Item system with granular modes (`TOTAL`, `NO_USE`, `NO_PLACE`).
  - [x] Client-side banned item visual feedback (grayed-out rendering).
  - [x] Safe-Unload / Chunk Quarantine for corrupted ticking block entities and entities.
- [x] **Client Integrity (Anti-Cheat)**:
  - [x] Client-side hash scanner (`ClientHashScanner`) for `mods/` and `resourcepacks/`.
  - [x] Server-side hash validation with configurable whitelist/blacklist.
  - [x] Network handshake payloads (`IntegrityRequestPayload`, `IntegrityResponsePayload`).
  - [x] Configurable timeout and kick messages.

## Version 1.1.0 — Technical Debt & Polish

- [x] **Logging Standardization**: Replace generic `System.err.println` and `printStackTrace()` in defensive tick-loop try/catch blocks (`ProtectionEventHandler`, `ModerationEventHandler`, `IntegrityEventHandler`) with the centralized `InStaff.LOGGER.error`.
- [x] **Command Delegation Strictness**: Refactor punishment commands (e.g., `/kick`, `/ban`) to fully delegate player disconnection logic to `PunishmentManager` instead of issuing the `connection.disconnect()` call directly from the command execution layer.
- [x] **Mute Command Bypass**: Intercept `CommandEvent` in `ModerationEventHandler` to prevent muted players from using chat-related commands. Instead of hardcoding, expose a `blocked_mute_commands` string list in the config file, defaulting to standard chat/PM commands (`/msg`, `/tell`, `/g`, `/global`, `/w`, `/reply`, etc.).
- [x] **IMPORTANT: Command Collision Mitigation**: Renamed `/invsee`, `/endersee`, `/ban`, `/tempban`, and `/unban` to `/isinvsee`, `/isendersee`, `/isban`, `/istempban`, and `/isunban` to prevent conflicts with vanilla server commands and server utility mods (Essentials).
- [x] **Pre-Join Disconnection**: Refactor punishment login checks to happen during the network configuration phase via `PunishmentCheckConfigurationTask`. This prevents banned players from briefly appearing in the world or triggering "Player joined the game" messages before being kicked.
- [x] **Pre-Join Maintenance & Whitelist Disconnection**: Migrated `MaintenanceManager` and `WhitelistManager` checks from `PlayerLoggedInEvent` into a configuration-phase task (`AccessCheckConfigurationTask`), ensuring unauthorized players are kicked before spawning in the world and before join broadcasts.
- [x] **Command Help & Documentation Alignment**: Synchronized `/staff` and `/instaff` help output (`pt_br.json`, `en_us.json`) and `README.md` to reference the renamed `/isinvsee`, `/isendersee`, `/isban`, `/istempban`, and `/isunban` commands.
- [x] **Hybrid Client Token (50% Server / 50% Client)**: Replace external OS queries and local `.instaff_token` persistence with a zero-process, per-server deterministic client token model.
  - **Server-Side (50%)**: `ClientTokenManager` generates a persistent 10-character alphanumeric `serverKey` (`SecureRandom`) and stores player token records in `instaff/client_tokens.json` via atomic `.tmp` swaps, sending `serverKey` in `IntegrityRequestPayload`.
  - **Client-Side (50%)**: `MachineIdFetcher` collects native JVM/Minecraft signals 100% in memory (CPU/arch/cores, physical RAM, disk capacity, OS/user, OpenGL GPU via `GlUtil`) and derives `SHA-256("instaff-token-v2:" + serverKey + "|" + nativeFingerprint)` as a 36-char UUID — zero `ProcessBuilder`, zero `/etc/machine-id`, and zero `.instaff_token` disk files.
  - **Cross-Server Privacy & Trust Ceiling**: Distinct `serverKey` values yield distinct tokens per server, and raw host signals are never logged or transmitted. Remains a client-side deterrent rather than cryptographic proof against modified clients.

## Version 1.1.1 — Account Security & QoL Improvements

- [x] **Account Device Lock / Anti-Impersonation Binding & OP Auto-Bind**:
  - **Problem**: In offline-mode servers (`online-mode=false`), any player can type another user's nickname in their launcher and log into their account. While `clientToken` catches banned players attempting ban evasion, it did not stop unbanned staff nicknames from being hijacked by an unauthorized machine.
  - **Solution**: Console-gated client token binding for staff accounts (`OP level >= 2`), stored atomically in `instaff/device_locks.json`, with **OP Auto-Bind** when `deviceLockEnabled = true` (TOFU auto-bind on first verified handshake and immediate auto-bind upon `/op <player>` when a token is already recorded).
  - **Enforcement & Quarantine**: On join, bound staff accounts undergo a brief command quarantine (`pendingDeviceLocks`) until the Play-phase integrity handshake completes. On token mismatch, the account is auto-banned via `PunishmentManager`, kicked, and an alert is broadcast to online staff.
  - **Console-Only Security**: Binding and unbinding are strictly console-only (`/isdevice bind <player> [token]` with automatic `ClientTokenManager` lookup when `[token]` is omitted, `/isdevice unbind <player>`), preventing in-game hijack escalation. Staff inspection available via `/isdevice info` and `/isdevice list`.
  - **Configuration**: Disabled by default via `deviceLockEnabled = false` under `[security]`. Safe fail-closed behavior on corrupted JSON.
- [x] **Multiplayer Data Consent Notice (Client-Side)**: Intercept `JoinMultiplayerScreen` via `ScreenEvent.Opening` (`MultiplayerConsentHandler` / `MultiplayerConsentScreen`) on first launch with a Yes/No prompt explaining that only mod IDs, SHA-256 mod hashes, and a per-server anonymous SHA-256 token are sent to the connected server; persisted in `config/instaff-client.toml` (`InStaffClientConfig`).
- [ ] **Staff Mod Inspection (`/modinspec`)**: Cache the `IntegrityResponsePayload` mod lists in `ServerIntegrityValidator` upon successful login. Implement a `/modinspec <player>` command to allow staff to view a live player's loaded mods on demand without needing to disconnect them, aiding in "gray-area" investigations.
- [x] **Invsee & Endersee GUI Alignment**: Aligned `InvseeScreen`/`InvseeMenu` (5-row grid) and `EnderseeScreen`/`EnderseeMenu` (3-row grid) to exact vanilla `generic_54.png` pixel coordinates.
- [ ] **Curios Accessory Integration**: Implement soft-dependency support for inspecting and modifying Curios slots via `/isinvsee`.