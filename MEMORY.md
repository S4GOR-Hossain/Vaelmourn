# MEMORY.md

Project: **Vaelmourn** — jME 3.6.1 action RPG, Windows/PowerShell.
Root: `E:\Github codes\vaelmourn`
Build: `& "C:\Program Files\Apache\maven\apache-maven-3.9.16\bin\mvn.cmd" -o -q clean compile`
(quote `-Dmdep...` args with `""` in PowerShell; unquoted `-Dmdep.outputFile=...` breaks argument parsing)

Last verified: `mvn -o -q clean compile` passes clean.

---

## Status: enemy HUD complete, build green

Both requested features are implemented and compile-verified.

### 1. Remaining-enemy counter
- `StageManager.getRemainingEnemyCount()` — counts `!isDead()` in `activeEnemies`
  plus committed `pendingSpawns`; returns 0 on Sanctuaries.
- Counts dead bodies as already dead. Enemies linger ~0.6s in `activeEnemies`
  after death for the death animation, so filtering on `isDead()` (not list
  membership) is what makes the number drop instantly on a kill.
- Boss stages need no special case: the boss is a roster member, so it reads 1
  and a summoning boss counts itself plus minions.
- Portal completion still uses `activeEnemies.isEmpty()`. Count reaching 0 may
  therefore precede the portal opening by the death-animation delay. Intended.
- `ForestBiome.updateHUD()` renders `ENEMIES: NN` top-right, right-aligned to
  `hudSoulRightAnchor` (same anchor as Soul Dust, y=58 vs y=30).
- Zero-padding is deliberate so line width never jitters.

### 2. Nearest-enemy direction arrow
- New file `src/main/java/com/vaelmourn/EnemyDirectionArrow.java`.
- Single flat unshaded triangle, ~2.55m above the player's feet.
- `BillboardControl` on the node + Z roll on the geometry. This ordering is the
  key: billboard pins local +X to screen right and +Y to screen up, so the roll
  reads directly as screen space. ahead=up, right=right, behind=down, and it
  tracks player facing for free with no world-orientation guesswork.
- Bearing = `-atan2(alongRight, alongForward)` using ground-plane (Y zeroed) X/Z
  projected onto the camera's right/forward basis. Elevation never tilts it.
- Position and roll both exponentially smoothed (rates 14f / 16f). Roll smoothing
  uses `atan2(sin d, cos d)` so it eases the short way across the ±PI seam.
- Null target → marker hidden outright; an arrow pointing at nothing can't exist.
- Upward `Ray` probe pulls the marker down under low ceilings; skips nodes named
  `EnemyArrow*`, `Player`, `Boss`, `Enemy*` so the player capsule and enemy bodies
  can't be mistaken for a ceiling.
- `StageManager.getNearestLivingEnemy(from, tpf)` — caches on a 0.15s interval
  (`NEAREST_REFRESH_INTERVAL`). Immediate re-pick only when the *cached* target
  died/canRemove. A null target on a cleared level still respects the interval
  (an earlier version rescanned every frame forever; fixed).
- `StageManager.clearEnemyTracking()` called from `unloadCurrentStage()`, so a new
  level can never point at the previous level's enemy.
- Wired in `ForestBiome.update()` after `stageManager.update(...)`, hidden on
  setup and on new-run reset.

---

## jME 3.6.1 API quirks in this project (cost real debugging time — reuse these)

Verified by `javap` against the resolved `jme3-core` jar:

| Mistake | Reality |
|---|---|
| `com.jme3.ray.Ray` | it's `com.jme3.math.Ray` |
| `Spatial.setVisible(boolean)` | doesn't exist. Use `setCullHint(CullHint.Always/Never)` — matches how this project hides HUD/effect nodes |
| `cam.getRight()` | only `getLeft()` exists. Right axis = `getLeft().negate()`. Confirmed `getLeft()` returns a **fresh** vector each call, so the negate can't corrupt camera rotation |
| `Vector3f.lerp(v,t)` | `interpolateLocal(v,t)` |
| `FastMath.wrapPi(f)` | doesn't exist. Wrap manually |
| `setLocalRotation(x,y,z)` | takes `Quaternion` or `Matrix3f`. Use `quat.fromAngleAxis(angle, Vector3f.UNIT_Z)` |
| `new Mesh(name, FloatBuffer[], short[])` | no such ctor. `new Mesh()` + `setBuffer(...)`; use `VertexBuffer.Type.Index` + `BufferUtils.createIntBuffer(indices)`, then `updateBound()` + `updateCounts()`. Existing example: `StageDecor.iceSpike()` |
| `Quaternion.fromEulerAngles` static | use instance `fromAngleAxis` |
| `Vector3f.equals(v, eps)` | no epsilon overload; compare components manually |

Existing patterns worth copying: `CombatEffects` and `EnemyController`'s health
bar both use `new BillboardControl()`; HUD hiding uses `setCullHint`.

---

## Project facts

- **All normal enemies spawn at stage load** via `Stage.spawnEnemies()`.
  Authored groups (e.g. Darkwood's `spawnEncounters`) are *placement order*, not
  delayed waves. So there is no hidden future-wave count to add — the roster is
  the truth. Loop-count reinforcements are included in the same spawn call.
- `StageManager.activeEnemies` is the authoritative roster, shared by combat,
  completion, and the gem hook.
- FBX material override handles a missing `PalleteTex.png`.
- GPU skinning crashes the AMD OpenGL driver (`EXCEPTION_ACCESS_VIOLATION` in
  `glBufferData`) — CPU skinning only. See `EnemyController.disableHardwareSkinning`.

---

## Prior completed work: gem progression (5 gems)

Also done and headless-verified (29/29 checks passed before the enemy-HUD work):
- `GemProgression.java` — state machine, originating-stage persistence,
  consumption, reset.
- Boss gate: 5th gem only drops when the boss is beaten.
- `StageManager` — gem hooks + loop-wrap reset, so later loops can't softlock.
- `ForestBiome` — `showHudMessage` with `frameDelta` timing.
- Note: a direct stage-start dev bypass exists and is currently unguarded.

---

## Verification status

Headless (passed, harness since deleted — recreate if needed):
- Arrow bearing maths: 4 cardinals, 4 diagonals, 90° camera turn, elevation
  independence, ±PI seam smoothing, degenerate-axis NaN rejection.
- `cam.getLeft()` mutation safety.
- Gem progression: 30-stage mapping, single drop, stage re-entry, pickup, boss
  consumption, reset.

NOT verified (needs a GL context / real play):
- Actual on-screen arrow orientation, sign, size, readability.
- Hover height and ceiling-probe feel during movement and jumping.
- Counter placement not colliding with other HUD text in-game.

Manual checklist: normal level (count starts correct, decrements per kill,
arrow tracks nearest and retargets instantly on kill, hides at 0), boss arena
(counters itself + summons), Sanctuary (both hidden), stage transition (no stale
previous-level target), new run (arrow hidden, count resets).

There is no in-repo test framework. Verification has been temporary headless
harnesses placed in `C:\Users\opusa\AppData\Local\Temp\opencode\`, compiled
against `dependency:build-classpath` output, then deleted.