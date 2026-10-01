package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The five-gem run progression: one unique gem per biome arc, collected from the
 * world during that biome's normal levels and spent to unlock that biome's boss.
 *
 * <p>Everything the mechanic needs is centralized here: the biome -&gt; gem -&gt; boss
 * table, the drop chance, the per-biome state machine and every decision the stage
 * manager asks it to make. The manager never hardcodes a gem rule of its own.</p>
 *
 * <h3>Per-biome run state</h3>
 * <pre>
 *   NOT_DROPPED -&gt; DROPPED -&gt; COLLECTED -&gt; COMPLETED
 * </pre>
 * <ul>
 *   <li>{@code NOT_DROPPED}: no drop has happened yet for this run.</li>
 *   <li>{@code DROPPED}: the one and only gem exists in the world, at
 *       {@link #dropPos}. The reservation is immediate — the drop is spent the moment
 *       the roll succeeds, so a second gem can never spawn even if this one is still
 *       lying on the floor.</li>
 *   <li>{@code COLLECTED}: the gem is in the player's inventory (or was, until the
 *       boss consumed it). It is kept across level changes and Sanctuary returns.</li>
 *   <li>{@code COMPLETED}: the biome boss was defeated with the gem in hand; the gem
 *       was removed and this arc is done for the run.</li>
 * </ul>
 *
 * <p>All five states reset only through {@link #resetRun()}, which the stage manager
 * calls from its genuine new-run path. Failing a boss check sends the player back to
 * the same biome's Sanctuary without touching any of this state.</p>
 *
 * <p>The world gem is not parented to the stage node: stage teardown detaches it
 * ({@link #onStageUnloaded()}) and re-entering a biome stage re-creates it at the exact
 * stored position ({@link #onStageEntered(int)}). That is what lets a player who leaves
 * a level without picking the gem up find the same single gem later instead of a
 * second copy.</p>
 */
public final class GemProgression {

    /** Authoritative per-gem lifecycle for one run. */
    public enum State {
        NOT_DROPPED,
        DROPPED,
        COLLECTED,
        COMPLETED
    }

    /**
     * One row of the central configuration table: which item a biome drops, which
     * model renders it and which boss it unlocks.
     */
    public static final class Gem {
        public final String id;
        public final String name;
        public final String modelPath;
        public final String biomeName;
        public final String bossName;
        public final ColorRGBA color;

        Gem(String id, String name, String modelPath, String biomeName,
            String bossName, ColorRGBA color) {
            this.id = id;
            this.name = name;
            this.modelPath = modelPath;
            this.biomeName = biomeName;
            this.bossName = bossName;
            this.color = color;
        }
    }

    /**
     * The whole progression in one table — gem id, display name, FBX model, owning
     * biome, boss it gates and the tint the dropped model is re-materialized with.
     * Order is the run order (Darkwood -&gt; Kingdom Court) and is also the biome
     * index used everywhere else in this class.
     */
    public static final Gem[] GEMS = {
            new Gem("darkwood_gem", "Verdant Gem", "Models/props/Gem1.fbx",
                    "Darkwood", "Tree Warden", new ColorRGBA(0.35f, 0.85f, 0.35f, 1f)),
            new Gem("ashen_gem", "Ember Gem", "Models/props/Gem2.fbx",
                    "Ashen Wastes", "Hellhound", new ColorRGBA(0.95f, 0.50f, 0.15f, 1f)),
            new Gem("frost_gem", "Glacier Gem", "Models/props/Gem3.fbx",
                    "Frozen Depths", "Frost Giant", new ColorRGBA(0.50f, 0.85f, 1.00f, 1f)),
            new Gem("jungle_gem", "Bloom Gem", "Models/props/Gem4.fbx",
                    "Jungle", "Beekeeper", new ColorRGBA(0.20f, 0.90f, 0.75f, 1f)),
            new Gem("kingdom_gem", "Sovereign Gem", "Models/props/Gem5.fbx",
                    "Kingdom Court", "Fallen King", new ColorRGBA(0.80f, 0.60f, 1.00f, 1f)),
    };

    public static final int GEM_COUNT = GEMS.length;

    /** Normal levels a biome must clear before its boss is reachable. */
    public static final int LEVELS_PER_BIOME = 4;

    /** Chance that a biome enemy death drops this biome's gem (its first and only one). */
    public static final float DROP_CHANCE = 0.05f;

    /** Player distance that auto-collects the ground gem. */
    public static final float PICKUP_RANGE = 2.2f;

    /** Ground clearance of the dropped model, so it hovers rather than z-fights the floor. */
    private static final float GEM_HOVER_HEIGHT = 0.35f;
    /** Dropped gems are scaled to this world height regardless of the source model's size. */
    private static final float GEM_TARGET_HEIGHT = 0.75f;
    private static final float SPIN_SPEED = 1.6f;
    private static final float BOB_SPEED = 1.8f;
    private static final float BOB_HEIGHT = 0.12f;

    private final AssetManager assetManager;
    private final Node rootNode;
    private final Random random = new Random();

    /** HUD/console feedback sink; the game points this at its message line. */
    private Consumer<String> messages = msg -> System.out.println("[Gem] " + msg);

    // ---- stage plan, derived once from the registered rotation ----------------
    /** -1 where a stage belongs to no biome (Sanctuary hubs). */
    private int[] stageBiome = new int[0];
    /** 0 for hubs/bosses, else the 1-based normal level number within the biome. */
    private int[] stageLevel = new int[0];
    private boolean[] stageBoss = new boolean[0];
    private boolean[] stageSanctuary = new boolean[0];
    /** stage index of each biome's own Sanctuary checkpoint. */
    private final int[] biomeSanctuary = new int[GEM_COUNT];
    /** stage index of each biome's boss arena. */
    private final int[] biomeBoss = new int[GEM_COUNT];

    // ---- per-biome run state -------------------------------------------------
    private final State[] states = new State[GEM_COUNT];
    private final int[] levelsCleared = new int[GEM_COUNT];
    private final Vector3f[] dropPos = new Vector3f[GEM_COUNT];
    /** stage index the gem was dropped in, so it comes back in that same level */
    private final int[] dropStage = new int[GEM_COUNT];
    private final Node[] worldNodes = new Node[GEM_COUNT];
    private final boolean[] loadFailed = new boolean[GEM_COUNT];
    private float bobClock;

    public GemProgression(AssetManager assetManager, Node rootNode) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        resetRun();
    }

    public void setMessageSink(Consumer<String> sink) {
        if (sink != null) this.messages = sink;
    }

    /**
     * Builds the stage -&gt; biome/level/boss lookup from the registered rotation.
     *
     * <p>Stage classes are matched by type rather than by list position, so inserting
     * or reordering the rotation (for example adding the Kingdom Court's fourth level)
     * cannot silently point a gem at the wrong biome.</p>
     */
    public void configure(List<Stage> stages) {
        int n = stages.size();
        stageBiome = new int[n];
        stageLevel = new int[n];
        stageBoss = new boolean[n];
        stageSanctuary = new boolean[n];
        java.util.Arrays.fill(stageBiome, -1);
        java.util.Arrays.fill(biomeSanctuary, -1);
        java.util.Arrays.fill(biomeBoss, -1);

        int[] levelCounter = new int[GEM_COUNT];
        for (int i = 0; i < n; i++) {
            Stage stage = stages.get(i);
            int biome = biomeOfClass(stage);
            if (biome < 0) {
                // a hub is the checkpoint of whichever biome follows it in the rotation
                stageSanctuary[i] = true;
                int next = biomeOfClass(stages.get(Math.min(i + 1, n - 1)));
                if (next >= 0 && biomeSanctuary[next] < 0) biomeSanctuary[next] = i;
                continue;
            }
            stageBiome[i] = biome;
            if (isBossClass(stage)) {
                stageBoss[i] = true;
                if (biomeBoss[biome] < 0) biomeBoss[biome] = i;
            } else {
                stageLevel[i] = ++levelCounter[biome];
            }
        }

        for (int b = 0; b < GEM_COUNT; b++) {
            if (biomeSanctuary[b] < 0) biomeSanctuary[b] = 0;
            if (biomeBoss[b] < 0) {
                System.err.println("[Gem] No boss stage registered for " + GEMS[b].biomeName
                        + " — its gem cannot gate anything.");
            }
            if (levelCounter[b] != 0 && levelCounter[b] < LEVELS_PER_BIOME) {
                System.err.println("[Gem] " + GEMS[b].biomeName + " has only "
                        + levelCounter[b] + " normal stages but " + LEVELS_PER_BIOME + " are required.");
            }
        }
    }

    /** New run: every gem back to NOT_DROPPED and every world gem gone. */
    public void resetRun() {
        for (int b = 0; b < GEM_COUNT; b++) {
            states[b] = State.NOT_DROPPED;
            levelsCleared[b] = 0;
            dropPos[b] = null;
            dropStage[b] = -1;
            removeWorldNode(b);
            loadFailed[b] = false;
        }
    }

    public State stateOf(int biome) {
        return states[clampBiome(biome)];
    }

    public int levelsClearedIn(int biome) {
        return levelsCleared[clampBiome(biome)];
    }

    public int biomeOf(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stageBiome.length) return -1;
        return stageBiome[stageIndex];
    }

    public int levelOf(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stageLevel.length) return 0;
        return stageLevel[stageIndex];
    }

    public boolean isBossStage(int stageIndex) {
        return stageIndex >= 0 && stageIndex < stageBoss.length && stageBoss[stageIndex];
    }

    public boolean isSanctuaryStage(int stageIndex) {
        return stageIndex >= 0 && stageIndex < stageSanctuary.length && stageSanctuary[stageIndex];
    }

    /** Stage index of the Sanctuary checkpoint serving the given biome. */
    public int sanctuaryIndexOf(int biome) {
        int index = biomeSanctuary[clampBiome(biome)];
        return index >= 0 ? index : 0;
    }

    /**
     * Whether the player may enter this boss arena.
     *
     * <p>Both conditions are checked every time, never cached: the four normal levels
     * of the current biome must be cleared <em>and</em> the gem must actually be in the
     * inventory. The state machine alone is not trusted — the item being present is
     * what the requirement is written in terms of.</p>
     */
    public boolean canEnterBoss(int bossIndex, Inventory inventory) {
        int biome = biomeOf(bossIndex);
        if (biome < 0 || !isBossStage(bossIndex)) return false;
        if (levelsCleared[biome] < LEVELS_PER_BIOME) return false;
        if (states[biome] != State.COLLECTED) return false;
        return inventory != null && inventory.hasItem(GEMS[biome].id, 1);
    }

    /** Why a boss entry was refused, for the player-facing message. */
    public String bossRefusalReason(int bossIndex) {
        int biome = biomeOf(bossIndex);
        Gem gem = GEMS[clampBiome(biome)];
        if (levelsCleared[clampBiome(biome)] < LEVELS_PER_BIOME) {
            return "Finish all " + LEVELS_PER_BIOME + " " + gem.biomeName
                    + " levels before challenging the " + gem.bossName + ".";
        }
        return "Required gem not found. Returning to the Sanctuary — the "
                + gem.name + " is still out there somewhere in " + gem.biomeName + ".";
    }

    /** Called after every stage load (boot, portal advance and new-run reset). */
    public void onStageEntered(int stageIndex) {
        int biome = biomeOf(stageIndex);
        if (biome < 0) return;
        // entering level 1 of a biome starts (or restarts) that biome's four-level run
        if (levelOf(stageIndex) == 1) levelsCleared[biome] = 0;
        // a gem that was dropped but never picked up lives on across level changes:
        // coming back to the level it fell in rebuilds that same single gem, exactly
        // where it was dropped, so a replay of the biome always finds it again
        if (states[biome] == State.DROPPED && worldNodes[biome] == null
                && dropStage[biome] == stageIndex) {
            spawnWorldGem(biome);
        }
    }

    /** Called before a stage is torn down: detaches the world gem, keeping its state. */
    public void onStageUnloaded() {
        for (int b = 0; b < GEM_COUNT; b++) removeWorldNode(b);
    }

    /** Called on every transition out of a stage; clearing a normal level counts. */
    public void onStageLeft(int stageIndex) {
        int biome = biomeOf(stageIndex);
        if (biome < 0 || isBossStage(stageIndex) || isSanctuaryStage(stageIndex)) return;
        levelsCleared[biome] = Math.min(LEVELS_PER_BIOME, levelsCleared[biome] + 1);
    }

    /**
     * The single enemy-death hook. Normal enemies of the active biome may drop this
     * biome's gem — exactly once per run, at {@code DROP_CHANCE}. Bosses never drop it.
     */
    public void onEnemyDefeated(int stageIndex, Vector3f deathPos, boolean boss) {
        if (boss) return;
        int biome = biomeOf(stageIndex);
        if (biome < 0 || levelOf(stageIndex) <= 0) return;
        if (states[biome] != State.NOT_DROPPED) return;
        if (random.nextFloat() >= DROP_CHANCE) return;

        states[biome] = State.DROPPED;
        dropPos[biome] = deathPos.clone();
        dropStage[biome] = stageIndex;
        spawnWorldGem(biome);
        say("Gem dropped: " + GEMS[biome].name + " (" + GEMS[biome].biomeName + ")");
    }

    /**
     * Boss defeat. The gem is only consumed when it is genuinely in the inventory —
     * that consumption is what marks the arc complete and unlocks the next Sanctuary.
     */
    public void onBossDefeated(int bossIndex, Inventory inventory) {
        int biome = biomeOf(bossIndex);
        if (biome < 0 || !isBossStage(bossIndex)) return;
        Gem gem = GEMS[biome];
        if (states[biome] == State.COMPLETED) return;

        if (inventory == null || !inventory.hasItem(gem.id, 1)) {
            say("The " + gem.bossName + " fell, but the " + gem.name + " is missing.");
            return;
        }
        inventory.removeItem(gem.id, 1);
        states[biome] = State.COMPLETED;
        removeWorldNode(biome);
        dropPos[biome] = null;
        dropStage[biome] = -1;
        say(gem.bossName + " defeated! " + gem.name + " consumed. "
                + "Proceeding to the next Sanctuary.");
    }

    /** Per-frame: spins/hovers any live gem and collects it when the player walks in. */
    public void update(float tpf, Vector3f playerPos, Inventory inventory) {
        bobClock += tpf;
        for (int b = 0; b < GEM_COUNT; b++) {
            Node node = worldNodes[b];
            if (node == null) continue;
            node.rotate(0f, SPIN_SPEED * tpf * 60f, 0f);
            node.setLocalTranslation(
                    node.getLocalTranslation().x,
                    dropPos[b].y + GEM_HOVER_HEIGHT + FastMath.sin(bobClock * BOB_SPEED) * BOB_HEIGHT,
                    node.getLocalTranslation().z);

            Vector3f gemPos = node.getWorldTranslation();
            if (playerPos.distance(gemPos) > PICKUP_RANGE) continue;
            if (inventory == null || !inventory.addItem(GEMS[b].id, 1)) continue;

            states[b] = State.COLLECTED;
            dropStage[b] = -1;
            removeWorldNode(b);
            say("Gem acquired: " + GEMS[b].name + "! Find the " + GEMS[b].bossName + ".");
        }
    }

    /** Debug one-liner, e.g. "Darkwood: DROPPED (2/4 levels)". */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < GEM_COUNT; b++) {
            if (b > 0) sb.append(" | ");
            sb.append(GEMS[b].biomeName).append(": ").append(states[b])
                    .append(" (").append(levelsCleared[b]).append('/').append(LEVELS_PER_BIOME)
                    .append(" levels)");
        }
        return sb.toString();
    }

    // ---- internals -----------------------------------------------------------

    private void say(String message) {
        messages.accept(message);
    }

    private static int clampBiome(int biome) {
        return (biome < 0 || biome >= GEM_COUNT) ? 0 : biome;
    }

    private void spawnWorldGem(int biome) {
        if (worldNodes[biome] != null || dropPos[biome] == null) return;
        Node holder = buildGemNode(biome);
        if (holder == null) return;
        holder.setLocalTranslation(dropPos[biome].x, dropPos[biome].y + GEM_HOVER_HEIGHT,
                dropPos[biome].z);
        rootNode.attachChild(holder);
        worldNodes[biome] = holder;
    }

    private void removeWorldNode(int biome) {
        Node node = worldNodes[biome];
        if (node == null) return;
        node.removeFromParent();
        worldNodes[biome] = null;
    }

    /**
     * Loads the biome's real FBX model and normalizes it into a groundable, tinted
     * pickup. Returns null (once, loudly) if the asset cannot be read — the state
     * machine keeps the gem in DROPPED so nothing is silently lost.
     */
    private Node buildGemNode(int biome) {
        Gem gem = GEMS[biome];
        Spatial model;
        try {
            model = StageDecor.loadCached(assetManager, gem.modelPath).clone();
        } catch (Exception e) {
            if (!loadFailed[biome]) {
                loadFailed[biome] = true;
                System.err.println("[Gem] Failed to load " + gem.modelPath + ": " + e.getMessage());
            }
            return null;
        }

        // uniform scale so a 5-unit or 0.05-unit source gem reads the same on screen
        float height = 0f;
        if (model.getWorldBound() instanceof BoundingBox bbox) {
            height = bbox.getExtent(new Vector3f()).y * 2f;
        }
        if (height > 0.0001f) {
            float scale = GEM_TARGET_HEIGHT / height;
            model.setLocalScale(scale, scale, scale);
        }
        // FBX pivots on its own origin, so put the model's own base on the node origin
        model.setLocalTranslation(0f, StageDecor.baseLift(model), 0f);

        // The FBX materials point at an external texture that does not ship with the
        // project, so jME hands back a placeholder image and every gem would render
        // the same flat white. Tint the real geometry instead so each biome's gem
        // keeps its own identity.
        applyGemMaterial(model, gem.color);

        Node holder = new Node("GemDrop_" + gem.id);
        holder.attachChild(model);
        return holder;
    }

    private void applyGemMaterial(Spatial spatial, ColorRGBA color) {
        if (spatial instanceof Geometry geometry) {
            Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            material.setBoolean("UseMaterialColors", true);
            material.setColor("Ambient", ColorRGBA.White);
            material.setColor("Diffuse", color);
            material.setColor("Specular", ColorRGBA.White);
            material.setFloat("Shininess", 48f);
            material.setColor("GlowColor", color.mult(0.45f));
            material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
            geometry.setMaterial(material);
        }
        if (!(spatial instanceof Node)) return;
        for (Spatial child : ((Node) spatial).getChildren()) {
            applyGemMaterial(child, color);
        }
    }

    /** Stage class -&gt; biome index (normal levels and boss arenas alike), -1 for hubs. */
    private static int biomeOfClass(Stage stage) {
        if (stage instanceof DarkwoodStage) return 0;
        if (stage instanceof TreeWardenBossStage) return 0;
        if (stage instanceof AshenWastesStage) return 1;
        if (stage instanceof HellhoundBossStage) return 1;
        if (stage instanceof FrozenDepthsStage) return 2;
        if (stage instanceof FrostGiantBossStage) return 2;
        if (stage instanceof JungleStage) return 3;
        if (stage instanceof BeekeeperBossStage) return 3;
        if (stage instanceof DarkRoyaleKingdomCourtStage) return 4;
        if (stage instanceof FallenKingBossStage) return 4;
        return -1;
    }

    /** Boss class -&gt; biome index, or -1 for anything else. */
    private static boolean isBossClass(Stage stage) {
        return stage instanceof TreeWardenBossStage
                || stage instanceof HellhoundBossStage
                || stage instanceof FrostGiantBossStage
                || stage instanceof BeekeeperBossStage
                || stage instanceof FallenKingBossStage;
    }
}