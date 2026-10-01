package com.vaelmourn;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.material.Material;
import com.jme3.light.DirectionalLight;
import com.jme3.light.AmbientLight;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates stage transitions, enemy spawning, and roguelike loop progression. */
public class StageManager {

    private final List<Stage> stages = new ArrayList<>();
    private int currentStageIndex = 0;
    private Stage currentStage;
    private int loopCount = 0;
    private float difficultyScalar = 1.0f;

    private final AssetManager assetManager;
    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final SimpleApplication app;

    /** Five-gem run progression: drop rolls, boss gating and per-run gem state. */
    private final GemProgression gems;
    /** The player's backpack, so a collected gem is a real inventory item. */
    private Inventory inventory;
    /** Optional HUD/console line for gem feedback. */
    private java.util.function.Consumer<String> messageSink;

    private List<EnemyController> activeEnemies = new ArrayList<>();
    /** boss summons land here mid-update, flushed onto activeEnemies after the
     *  step so no list is mutated while combat is iterating it */
    private final List<EnemyController> pendingSpawns = new ArrayList<>();

    /** cached "closest living enemy" for the HUD arrow; see getNearestLivingEnemy */
    private EnemyController nearestEnemy;
    private float nearestRefreshTimer;
    /** the arrow re-picks a target at most this often, but instantly when its
     *  current target dies, so a kill never leaves the arrow pointing at a corpse */
    private static final float NEAREST_REFRESH_INTERVAL = 0.15f;
    private Geometry portalGeo;
    private Node portalNode;
    private Vector3f portalPosition;
    private static final float PORTAL_ACTIVATION_RANGE = 3.5f;
    /** half-width of the portal oval, matching PORTAL_BASE_HEIGHT so the trigger zone
     *  covers the whole visible doorway instead of just its centre line */
    private static final float PORTAL_RADIUS = 2.2f;
    /** height of the portal oval's centre above the ground it rests on — the mesh is
     *  a 4.2-radius sphere flattened to a doorway, so 4.2 puts its base on the floor */
    private static final float PORTAL_BASE_HEIGHT = 4.2f;

    // currency economy knobs (soul dust): tune here, awarded once per enemy death
    private static final int[] TIER_REWARDS = {5, 10, 16, 24}; // index = tier - 1
    private static final int BOSS_REWARD_BASE = 250;
    private static final int BOSS_REWARD_PER_LOOP = 75;
    private static final int ENEMY_REWARD_PER_LOOP = 3;

    public StageManager(AssetManager assetManager, Node rootNode, BulletAppState bulletAppState,
                       SimpleApplication app) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.app = app;
        this.gems = new GemProgression(assetManager, rootNode);
    }

    /** The gem progression itself, so the game can reset it and show its messages. */
    public GemProgression getGems() {
        return gems;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setMessageSink(java.util.function.Consumer<String> sink) {
        this.messageSink = sink;
        gems.setMessageSink(sink);
    }

    private void reportGem(String message) {
        if (messageSink != null) messageSink.accept(message);
    }

    public void addStage(Stage stage) {
        stages.add(stage);
        // keep the gem plan in step with the rotation as it is registered
        gems.configure(stages);
    }

    public void loadInitialStage(BetterCharacterControl playerControl) {
        if (stages.isEmpty()) {
            System.err.println("ERROR: No stages registered!");
            return;
        }

        currentStageIndex = 0;
        // dev hook: boot straight into any stage with -Dvaelmourn.startStage=N
        String startProp = System.getProperty("vaelmourn.startStage");
        if (startProp != null) {
            try {
                int requested = Integer.parseInt(startProp.trim());
                currentStageIndex = Math.min(Math.max(0, requested), stages.size() - 1);
                System.out.println("[DEV] vaelmourn.startStage=" + requested
                        + " -> booting directly into index " + currentStageIndex);
            } catch (NumberFormatException ignored) {
            }
        }
        gems.configure(stages);
        loadStage();

        Vector3f spawnPoint = currentStage.getPlayerSpawnPoint();
        playerControl.warp(spawnPoint);

        System.out.println("Loaded initial stage: " + currentStage.getName());
    }

    /**
     * Tear down whatever stage we're on and boot a completely fresh run: the very
     * first stage (index 0, the Sanctuary) at loop 0 with base difficulty. No
     * run-specific state (old enemies, portal, emitters, stage geometry, dropped
     * gems) may leak over. Deliberately ignores -Dvaelmourn.startStage, which only
     * applies at boot.
     */
    public void resetToFirstStage(BetterCharacterControl playerControl) {
        unloadCurrentStage();

        // fresh run: back to stage 0 at base difficulty
        currentStageIndex = 0;
        loopCount = 0;
        difficultyScalar = 1.0f;
        // every gem goes back to NOT_DROPPED — this is the one true new-run path
        gems.resetRun();

        loadStage();

        Vector3f spawnPoint = currentStage.getPlayerSpawnPoint();
        playerControl.warp(spawnPoint);

        System.out.println("Reset to fresh run at: " + currentStage.getName());
    }

    public void advanceStage() {
        if (currentStage == null) return;

        // clearing a normal level counts toward that biome's four-level requirement
        gems.onStageLeft(currentStageIndex);

        int next = currentStageIndex + 1;
        boolean newLoop = false;

        if (next >= stages.size()) {
            // cleared the last arena: start the next loop at a fresh Sanctuary
            next = 0;
            newLoop = true;
        } else if (gems.isBossStage(next) && !gems.canEnterBoss(next, inventory)) {
            // Boss gate. Reached by trying to walk into an unlocked boss arena (which
            // can only be the biome's fourth level ending) or by the dev skip; either
            // way the player is sent back to this biome's own Sanctuary with the run
            // state, upgrades and inventory untouched.
            int biome = gems.biomeOf(next);
            reportGem(gems.bossRefusalReason(next));
            next = gems.sanctuaryIndexOf(biome);
        }

        unloadCurrentStage();

        if (newLoop) {
            loopCount++;
            updateDifficultyScalar();
            // A full cycle ended with the Fallen King consuming the last gem, so the
            // next loop starts the gem progression over. Without this the arcs would
            // stay COMPLETED with no gems in the bag and every boss would stay locked.
            gems.resetRun();
            reportGem("New cycle begins. All five gems are available again.");
            System.out.println("Loop " + loopCount + " started! Difficulty: " + difficultyScalar + "x");
        }

        currentStageIndex = next;
        loadStage();

        System.out.println("Transitioned to: " + currentStage.getName());
    }

    /** Detaches the current stage, its enemies, its effects and any ground gem. */
    private void unloadCurrentStage() {
        if (currentStage != null) {
            currentStage.cleanup(rootNode, bulletAppState);
        }
        // detach leftover enemies and their effect nodes (emitters are scene
        // nodes, not stage nodes) so nothing lingers into the next biome
        for (EnemyController enemy : activeEnemies) {
            enemy.cleanup(bulletAppState);
        }
        activeEnemies.clear();
        pendingSpawns.clear();
        // no stale arrow target may survive into the next level
        clearEnemyTracking();
        if (portalNode != null) {
            portalNode.removeFromParent();
            portalNode = null;
            portalGeo = null;
            portalPosition = null;
        }
        // an uncollected gem keeps its state but loses its scene node; re-entering the
        // biome rebuilds that same single gem at the position it was dropped
        gems.onStageUnloaded();
    }

    /** Builds, spawns for and lights whatever {@link #currentStageIndex} points at. */
    private void loadStage() {
        currentStage = stages.get(currentStageIndex);
        currentStage.build(assetManager, rootNode, bulletAppState);
        registerBossSummons();

        // spawn this stage's enemies (safe hubs like the Sanctuary stay empty)
        if (!currentStage.isSafe()) {
            activeEnemies = currentStage.spawnEnemies(assetManager, rootNode, bulletAppState, loopCount);
            System.out.println("Spawned " + activeEnemies.size() + " enemies in " + currentStage.getName());
        }

        // level counters, and the re-creation of an uncollected gem left in this biome
        gems.onStageEntered(currentStageIndex);

        updateLighting();
    }

    public void update(float tpf, Vector3f playerPos, BetterCharacterControl playerControl) {
        if (currentStage == null) return;

        for (EnemyController enemy : activeEnemies) {
            enemy.update(tpf, playerPos, playerStats);
        }

        // flush anything the boss summoned this step onto the live fight
        if (!pendingSpawns.isEmpty()) {
            activeEnemies.addAll(pendingSpawns);
            pendingSpawns.clear();
        }

        // sweep out any enemies that croaked this frame — only once their death
        // effect actually finished playing (canRemove), or the death pop would
        // never be seen
        activeEnemies.removeIf(e -> {
            if (e.canRemove()) {
                // sampled before teardown, while the node still has a world transform
                Vector3f deathPos = e.getPosition().clone();
                boolean bossKill = e.isBoss();
                e.cleanup(bulletAppState);
                if (playerStats != null) {
                    // boss kills drop a real jackpot; normal kills pay by tier.
                    // awarded exactly once — this is the single removal point, so
                    // dying enemies never leave the list without cashing in.
                    int reward = e.isBoss()
                            ? BOSS_REWARD_BASE + loopCount * BOSS_REWARD_PER_LOOP
                            : tierReward(e.getTier()) + loopCount * ENEMY_REWARD_PER_LOOP;
                    // Luck run-upgrade scales the actual payout. This is the project's
                    // single centralized reward point, so one multiplier here covers
                    // every kill in every stage with no parallel calculation. Floored so
                    // a fractional bonus can never round a kill's dust down to zero.
                    reward = (int) Math.floor(reward * playerStats.getLuckMultiplier());
                    playerStats.addSoulDust(reward);
                }
                // one central loot hook: a biome enemy may drop this biome's single gem
                gems.onEnemyDefeated(currentStageIndex, deathPos, bossKill);
                // beating a boss consumes its gem, which completes the arc
                if (bossKill) gems.onBossDefeated(currentStageIndex, inventory);
                return true;
            }
            return false;
        });

        // any ground gem still lying around spins, hovers and gets collected on contact
        gems.update(tpf, playerPos, inventory);

        // Safe stages (any Sanctuary instance in the rotation) always keep an
        // open exit portal; combat stages only open theirs once everything's dead.
        boolean needsPortal = currentStage.isSafe() || activeEnemies.isEmpty();
        if (needsPortal && portalGeo == null) {
            spawnExitPortal(playerPos);
        }

        if (portalGeo != null) {
            // kept the portal non-rotating on purpose — a spinning door looks wrong.
            // Measure against the doorway's footprint on the ground, not just its
            // centre, and keep the vertical term loose: the oval is 8.4 units tall, so
            // a strict 3D distance could never be satisfied by a player standing on the
            // floor in front of it.
            Vector3f horizontal = new Vector3f(
                    playerPos.x - portalPosition.x, 0, playerPos.z - portalPosition.z);
            float vertical = Math.abs(playerPos.y - portalPosition.y);
            if (horizontal.length() < PORTAL_ACTIVATION_RANGE
                    && vertical < PORTAL_BASE_HEIGHT + 2f) {
                advanceStage();
            }
        }
    }

    private void spawnExitPortal(Vector3f playerPos) {
        if (portalGeo != null) return;

        // glowing oval portal; its base sits at the stage's own ground height, so
        // height field stages don't end up with the portal buried underground or
        // hanging in mid-air over a dip. The flat stages all sit at y=0, which
        // reproduces the old hardcoded placement exactly.
        Vector3f ground = currentStage.getExitPortalGround();
        portalPosition = new Vector3f(ground.x, ground.y + PORTAL_BASE_HEIGHT, ground.z);

        portalNode = new Node("Portal");
        // went with a stock jME3 Sphere (flattened into an oval) instead of a
        // hand-built triangle-fan mesh — that custom mesh's buffer upload crashes
        // this AMD OpenGL driver (EXCEPTION_ACCESS_VIOLATION in glBufferData, MultiPassLighting)
        com.jme3.scene.shape.Sphere portalMesh = new com.jme3.scene.shape.Sphere(16, 24, 4.2f);
        portalGeo = new Geometry("PortalGeometry", portalMesh);
        portalGeo.setLocalScale(PORTAL_RADIUS / 4.2f, 1f, 0.12f);
        // the sphere mesh bounds assume an unscaled 4.2 radius; after the flatten
        // above the real bounds are much narrower, so recompute them or the renderer
        // frustum-culls the portal away even though it is right in front of the player
        portalGeo.updateModelBound();

        Material portalMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        portalMat.setBoolean("UseMaterialColors", true);
        portalMat.setColor("Diffuse", new ColorRGBA(0.2f, 1f, 0.8f, 1f));
        portalMat.setColor("GlowColor", new ColorRGBA(0f, 0.8f, 0.5f, 1f));
        // it's a flat disc, so turn face culling off to see it from both sides
        portalMat.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
        portalGeo.setMaterial(portalMat);

        portalNode.attachChild(portalGeo);
        portalNode.setLocalTranslation(portalPosition);
        rootNode.attachChild(portalNode);

        System.out.println("Portal spawned! ground=" + currentStage.getName()
                + " pos=" + portalPosition
                + " bounds=" + portalGeo.getModelBound().getClass().getSimpleName()
                + " parentAttached=" + (portalNode.getParent() != null));
    }

    private void updateLighting() {
        for (com.jme3.light.Light light : rootNode.getLocalLightList()) {
            rootNode.removeLight(light);
        }

        ColorRGBA skyColor = currentStage.getSkyColor();
        app.getViewPort().setBackgroundColor(skyColor);

        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(currentStage.getSunDirection());
        sun.setColor(ColorRGBA.White.mult(1.0f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(currentStage.getAmbientColor());
        rootNode.addLight(ambient);
    }

    private void updateDifficultyScalar() {
        // base 1.0x, +0.2x per loop, hard-capped at 3.0x
        difficultyScalar = Math.min(3.0f, 1.0f + loopCount * 0.2f);
    }

    /** Let a boss arena feed fight-summoned enemies onto the live roster. */
    private void registerBossSummons() {
        if (currentStage instanceof BossStage bossStage) {
            bossStage.setEnemySpawnSink(spawned -> pendingSpawns.addAll(spawned));
        }
    }

    public float getDifficultyScale() {
        return difficultyScalar;
    }

    private int tierReward(int tier) {
        if (tier < 1) tier = 1;
        if (tier > TIER_REWARDS.length) tier = TIER_REWARDS.length;
        return TIER_REWARDS[tier - 1];
    }

    public int getLoopCount() {
        return loopCount;
    }

    public List<EnemyController> getActiveEnemies() {
        return activeEnemies;
    }

    /**
     * Enemies still alive in the current level, i.e. the number of kills still needed
     * before the exit portal opens.
     *
     * <p>This is derived from the same {@link #activeEnemies} roster the completion
     * check uses, so the HUD can never disagree with the real game state. Three
     * subtleties are handled here:</p>
     * <ul>
     *   <li>an enemy that has died but whose death animation is still playing is
     *       already <i>not</i> a required kill, so it stops counting the frame it dies
     *       (the roster only sheds it 0.6s later, once {@code canRemove()} is true)</li>
     *   <li>boss summons queued in {@link #pendingSpawns} count as soon as the boss
     *       commits to them, so a summoning boss never flickers down to a number the
     *       player cannot influence</li>
     *   <li>a safe stage (any Sanctuary) has no required combat at all and reports 0,
     *       which matches its always-open portal</li>
     * </ul>
     *
     * <p>Boss arenas need no special case: the boss is a normal member of the roster,
     * so a lone boss reads as 1 and a summoning boss counts itself plus its minions.</p>
     */
    public int getRemainingEnemyCount() {
        if (currentStage == null || currentStage.isSafe()) return 0;
        int living = 0;
        for (EnemyController enemy : activeEnemies) {
            if (!enemy.isDead()) living++;
        }
        return living + pendingSpawns.size();
    }

    /**
     * Closest living enemy to {@code from}, for the HUD direction arrow.
     *
     * <p>Scanning a small roster is cheap, but there is no reason to do it every frame,
     * so the answer is cached and refreshed on a short interval. The one thing that
     * must never feel laggy is a kill, so a dead or removed current target forces an
     * immediate re-pick rather than waiting out the interval.</p>
     *
     * <p>Returns null when the level is cleared or the player stands in a Sanctuary.
     * Callers get the live controller, not a copy, and must still tolerate it becoming
     * dead between this call and their own use of it.</p>
     */
    public EnemyController getNearestLivingEnemy(Vector3f from, float tpf) {
        if (from == null || currentStage == null || currentStage.isSafe()) {
            nearestEnemy = null;
            return null;
        }

        // Only a target that actually existed and then died forces an immediate re-pick.
        // A null target on a cleared level must still respect the interval, otherwise the
        // scan runs every frame forever for nothing.
        boolean targetDied = nearestEnemy != null
                && (nearestEnemy.isDead() || nearestEnemy.canRemove());
        if (!targetDied && nearestRefreshTimer > 0f) {
            nearestRefreshTimer -= tpf;
            if (nearestRefreshTimer > 0f) return nearestEnemy;
        }

        nearestRefreshTimer = NEAREST_REFRESH_INTERVAL;
        EnemyController best = null;
        float bestSq = Float.MAX_VALUE;
        for (EnemyController enemy : activeEnemies) {
            if (enemy.isDead() || enemy.canRemove()) continue;
            float dx = enemy.getPosition().x - from.x;
            float dz = enemy.getPosition().z - from.z;
            float sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = enemy;
            }
        }
        nearestEnemy = best;
        return nearestEnemy;
    }

    /**
     * Drops the cached arrow target. Called on every stage teardown so a new level can
     * never briefly point at an enemy that belonged to the previous one.
     */
    public void clearEnemyTracking() {
        nearestEnemy = null;
        nearestRefreshTimer = 0f;
    }

    public String getCurrentStageName() {
        return currentStage != null ? currentStage.getName() : "Unknown";
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    private PlayerStats playerStats;
    public void setPlayerStats(PlayerStats stats) {
        this.playerStats = stats;
    }
}
