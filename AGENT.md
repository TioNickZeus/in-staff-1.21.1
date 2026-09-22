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
5. **UUID-First Identification & Offline Mode Support**:
   - Player identity must **always** be resolved and persisted by `UUID`, never solely by player name.
   - Must cleanly support both Online (`online-mode=true`) and Offline (`online-mode=false`) servers.
   - **Zero External Mojang HTTP Requests**: Never query external Mojang APIs (e.g. `api.mojang.com`) to resolve player UUIDs. In offline servers, Mojang returns online UUIDs that conflict with the server's deterministic offline UUIDs, corrupting punishments and data.
   - Offline lookups must resolve through `ServerPlayer.getUUID()`, the server's local `GameProfileCache` (`server.getProfileCache().get(name)`), or native deterministic offline generation (`UUIDUtil.createOfflinePlayerUUID(name)`).
   - **Offline Ban Evasion Mitigation**: In offline servers, players can easily change nicks to get new UUIDs. The moderation engine must support IP tracking and leverage the client-side mod installation token/handshake to deter evasion.
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

## 4. Surgical Editing & Anti-Rewrite Policy

Working code is a liability to break, not an invitation to improve. Every agent must treat existing functional code as **load-bearing until proven otherwise**, and edit it with the smallest possible footprint.

1. **Minimal Diff Principle**:
   - Touch only the lines strictly required to satisfy the task. Do not reformat, reorder imports, rename variables, or "clean up" code that isn't part of the requested change.
   - Prefer targeted patches (find-and-replace on the exact block) over regenerating a whole file, class, or method — even when it would be faster to write from scratch.
   - If the required change touches more than ~30% of a file, stop and confirm scope with the maintainer before proceeding.
2. **No Drive-By Refactors**:
   - Noticing an unrelated smell, dead code, or a "better way to do it" while working on a feature/fix is **not** license to change it in the same commit.
   - Log it instead: add an entry to `KNOWN_ISSUES.md` (if it's a risk) or `ROADMAP.md` (if it's an improvement), and leave the code untouched.
   - Refactors are only performed in a dedicated `refactor/` or `chore/` branch, requested explicitly.
3. **Read Before You Write**:
   - Always read the full method/class — and, when relevant, its call sites — before editing it. Never patch code you haven't fully read in the current session.
   - Re-read a file immediately before editing it if any prior edit in the session may have changed its state.
4. **Preserve Public Contracts**:
   - Method signatures, payload record shapes (`network/`), config keys, OP level requirements, and command syntax must not change unless the task explicitly requires it.
   - If a signature or config key must change, document every call site updated and flag it under "Breaking Changes / Invariants" in the PR (see §3.2).
5. **Match Existing Patterns, Don't Impose New Ones**:
   - Follow the file's existing style, naming, and idioms even if the agent would personally structure it differently. Consistency with the surrounding codebase outranks personal/model preference.
   - New patterns (e.g. a different exception-handling style, a new manager pattern) are only introduced with explicit approval, and then applied consistently, not as a one-off.
6. **One Concern Per Change**:
   - A single commit/PR addresses one feature, one fix, or one refactor — never a mix. Mixing a rewrite with a behavioral fix makes the diff impossible to review safely.
7. **Justify Behavioral Changes**:
   - If a change alters observable behavior (timing, message wording, default config value, command output), state the "why" explicitly in the commit body and PR description — never as a silent side effect of a rewrite.
8. **When In Doubt, Ask — Don't Rewrite**:
   - If the correct minimal edit is unclear, ask the maintainer rather than defaulting to a broader rewrite "to be safe." A rewrite is never the safe option for functional code.

---

## 5. General Operating Discipline

1. **Fail Loud, Not Silent**: if requirements, config defaults, or expected behavior are ambiguous, ask before proceeding. Never guess silently and ship a plausible-looking assumption.
2. **No Scope Creep**: implement exactly what was asked. Additional features, extra config options, or "while I'm here" additions belong in `ROADMAP.md`, not in the diff.
3. **Invariant Guard**: before finalizing any change, re-check it against the invariants in §1. A change that satisfies the immediate request but violates an invariant is not acceptable.
4. **Verify, Don't Assume**: run `./gradlew compileJava --no-daemon` after any non-trivial edit, not only before opening the PR. Don't assume a change compiles because it "looks right."
5. **Idempotent, Reversible Actions**: prefer changes that are easy to revert cleanly (a self-contained commit) over changes entangled with unrelated edits.
6. **Traceability**: every non-obvious decision (why this approach over an alternative) gets one line in the commit body or PR description — future agents and maintainers should not have to reverse-engineer intent.
7. **Respect the SSOT**: `ARCHITECTURE.md` is authoritative for structure, `AGENT.md` for process. If code and docs disagree, flag the discrepancy rather than silently trusting either one.

---

## 6. Key File Index

| File | Purpose | Rule for Agents |
|:---|:---|:---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Single Source of Truth (SSOT) for technical architecture | **Read first** before writing code. Update when packages/data change. |
| [`ROADMAP.md`](ROADMAP.md) | Backlog and planned feature board | Check before proposing new mechanics. Mark completed items. |
| [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | Triage board for edge cases and exploits | Log reproduction guides for unconfirmed bugs here. |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history and release notes | Document every user-facing or architectural change. |
| [`gradle.properties`](gradle.properties) | Mod metadata and version | Update `mod_version` upon release preparation. |
| [`InStaff.java`](src/main/java/com/tio/instaff/InStaff.java) | Common entrypoint (`@Mod`) | Keep lightweight; delegate logic to module handlers. |
| [`InStaffClient.java`](src/main/java/com/tio/instaff/InStaffClient.java) | Client entrypoint (`@Mod, dist=CLIENT`) | Register client screens, key bindings, and integrity scanner here. |