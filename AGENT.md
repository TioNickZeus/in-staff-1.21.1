# AGENT.md — AI Agent & Developer Operating Manual

> **Scope**: Operating standards, Git Flow conventions, and development invariants for AI coding agents and contributors working on **In-Staff** (`in-staff-1.21.1`).
> **Repository**: [TioNickZeus/in-staff-1.21.1](https://github.com/TioNickZeus/in-staff-1.21.1)
> **Mod Loader**: NeoForge 1.21.1 (FML) | **Java**: 21 (Temurin)

---

## 1. Core Mission & Architectural Invariants

Every AI agent operating in this repository **must strictly preserve** these core invariants:

1. **Client + Server Required**:
   - In-Staff is a **dual-sided mod**: it must be installed on both the server and all connecting clients.
   - Vanilla clients are **not supported** — they are rejected during NeoForge's mod negotiation handshake.
   - This mod targets **modded NeoForge servers** exclusively. Vanilla servers have mature plugin ecosystems (EssentialsX, LuckPerms) and do not need this mod.
2. **Side Separation**:
   - Client-only code lives in `com.tio.instaff.client.*` and is loaded via `InStaffClient.java` (`@Mod(dist = Dist.CLIENT)`).
   - Server-side code (`commands/`, `moderation/`, `access/`, `protection/`) must **never** import from `client/`.
   - Shared code (`network/`, `inspection/`, `util/`) may be used by both sides.
3. **Defensive Programming & Zero Server Crash Policy**:
   - The mod must **never crash the server tick thread** under any circumstance.
   - Guard against corrupted tile entities and ticking entities via safe exception interception (`protection/`).
   - Bound and sanitize all inputs: Brigadier commands must enforce bounds, and config specs must use typed limits (`defineInRange` or `defineInList`).
   - Always check `InStaffConfig.SPEC.isLoaded()` before accessing config values to prevent bootstrapping or shutdown race conditions.
4. **Server-Authoritative Configuration**:
   - All moderation configuration uses `ModConfig.Type.SERVER`. The client cannot override or weaken server rules.
   - Client-specific preferences (e.g., UI settings) use a separate `ModConfig.Type.CLIENT` if needed.
5. **UUID-First Identification**:
   - Player identity must **always** be resolved and persisted by `UUID`, never solely by player name.
   - Offline lookups must support both online players (`ServerPlayer`) and offline disk data (`GameProfile` / `playerdata/<UUID>.dat`).
6. **Monotonic Timing (`Util.getMillis()`)**:
   - All durations and elapsed-time comparisons (temp-bans, temp-mutes, timeouts, playtime accumulation) must use `net.minecraft.Util.getMillis()`, never wall-clock `System.currentTimeMillis()`.
7. **Data Integrity & Atomic Persistence**:
   - Storage files (punishment ledger, dynamic whitelist, maintenance access list) must be written atomically using temporary file swaps (`Files.move` with `REPLACE_EXISTING`) and guarded by thread synchronization (`synchronized(LOCK)`).
8. **Zero Hardcoded Player-Facing Text**:
   - All messages rendered to players or staff must go through `LocalizationHelper.getMessage()` or `LocalizationHelper.getPrefixedMessage()`.
   - Every translation key must exist in both `src/main/resources/assets/instaff/lang/en_us.json` and `pt_br.json`.
9. **Additive & Modular Design**:
   - Modules (`moderation`, `access`, `inspection`, `protection`, `network`, `client`) must remain loosely coupled.
   - A failure or disabled feature in one module must not disrupt other modules.

---

## 2. Git Flow & GitHub Collaboration Standards

All contributions follow this Git Flow standard:

### 2.1 Branch Naming Conventions

Never commit directly to `main`. Always branch off from the latest `origin/main`.

| Branch Type | Prefix | Example | Purpose |
|:---|:---|:---|:---|
| **Feature** | `feat/` | `feat/invsee-curios`, `feat/maintenance-motd` | New user-facing mechanics, commands, or tools. |
| **Bug Fix** | `fix/` | `fix/offline-invsee-dupe`, `fix/freeze-teleport` | Resolving bugs, edge cases, crashes, or exploits. |
| **Chore / Maintenance** | `chore/` | `chore/hardening-pass`, `chore/bump-deps` | Refactoring, build scripts, workflows, or cleanup. |
| **Documentation** | `docs/` | `docs/update-architecture` | Documentation additions or revisions without code changes. |
| **Performance** | `perf/` | `perf/optimize-chunk-tick` | Measurable performance or memory allocation improvements. |

### 2.2 Commit Message Standards (Conventional Commits)

```text
<type>(<scope>): <imperative summary in present tense>

- Detailed bullet point explaining the "why" and "what"
- Reference to any issue or config key affected
```

**Allowed Types**: `feat`, `fix`, `docs`, `refactor`, `perf`, `chore`, `test`.
**Examples**:
- `feat(moderation): implement UUID-based tempban with duration parser`
- `fix(protection): prevent player item duplication during offline invsee save`
- `feat(client): add client integrity hash scanner for mods folder`

---

## 3. Pull Request (PR) Workflow

### 3.1 Pre-PR Verification Checklist

Before creating a Pull Request, the agent must verify:

- [ ] **Compilation**: `./gradlew compileJava --no-daemon` passes with 0 errors.
- [ ] **Full Build & Artifact**: `./gradlew build --stacktrace` passes.
- [ ] **Side Safety**: No server code imports from `com.tio.instaff.client.*`.
- [ ] **Translations**: Any new message key exists in both `en_us.json` and `pt_br.json`.
- [ ] **No Stale References**: Config names and permissions match `InStaffConfig.java` and `ARCHITECTURE.md`.
- [ ] **Changelog**: An entry is added to `CHANGELOG.md` under the targeted version.
- [ ] **Documentation**: `README.md` and `ARCHITECTURE.md` are updated if public behavior changed.

### 3.2 Pull Request Structure

Every PR must be opened against `main` and include:

```markdown
### Summary
Brief 1-3 sentence summary of the problem solved and the approach taken.

### Changes Made
- **[Component/File]**: Description of change.

### Verification & Testing
- How this change was verified.

### Breaking Changes / Invariants
- State whether any config defaults or behaviors changed (should be "None").
```

---

## 4. Key File Index

| File | Purpose | Rule for Agents |
|:---|:---|:---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Single Source of Truth (SSOT) for technical architecture | **Read first** before writing code. Update when packages/data change. |
| [`ROADMAP.md`](ROADMAP.md) | Backlog and planned feature board | Check before proposing new mechanics. Mark completed items. |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | Triage board for edge cases and exploits | Log reproduction guides for unconfirmed bugs here. |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history and release notes | Document every user-facing or architectural change. |
| [`gradle.properties`](gradle.properties) | Mod metadata and version | Update `mod_version` upon release preparation. |
| [`InStaff.java`](src/main/java/com/tio/instaff/InStaff.java) | Common entrypoint (`@Mod`) | Keep lightweight; delegate logic to module handlers. |
| [`InStaffClient.java`](src/main/java/com/tio/instaff/InStaffClient.java) | Client entrypoint (`@Mod, dist=CLIENT`) | Register client screens, key bindings, and integrity scanner here. |