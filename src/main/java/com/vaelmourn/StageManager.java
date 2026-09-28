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

    private List<EnemyController> activeEnemies = new ArrayList<>();
    private Geometry portalGeo;
    private Node portalNode;
    private Vector3f portalPosition;
    private static final float PORTAL_ACTIVATION_RANGE = 3.5f;

    public StageManager(AssetManager assetManager, Node rootNode, BulletAppState bulletAppState,
                       SimpleApplication app) {
        this.assetManager = assetManager;
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.app = app;
    }

    public void addStage(Stage stage) {
        stages.add(stage);
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
        currentStage = stages.get(currentStageIndex);
        currentStage.build(assetManager, rootNode, bulletAppState);

        // spawn this stage's enemies (safe hubs like the Sanctuary stay empty)
        if (!currentStage.isSafe()) {
            activeEnemies = currentStage.spawnEnemies(assetManager, rootNode, bulletAppState, loopCount);
            System.out.println("Spawned " + activeEnemies.size() + " enemies in " + currentStage.getName());
        }

        Vector3f spawnPoint = currentStage.getPlayerSpawnPoint();
        playerControl.warp(spawnPoint);

        updateLighting();

        System.out.println("Loaded initial stage: " + currentStage.getName());
    }

    /**
     * Tear down whatever stage we're on and boot a completely fresh run: the very
     * first stage (index 0, the Sanctuary) at loop 0 with base difficulty. No
     * run-specific state (old enemies, portal, emitters, stage geometry) may leak
     * over. Deliberately ignores -Dvaelmourn.startStage, which only applies at boot.
     */
    public void resetToFirstStage(BetterCharacterControl playerControl) {
        if (currentStage != null) {
            currentStage.cleanup(rootNode, bulletAppState);
        }
        // detach leftover enemies and their effect nodes (emitters are scene
        // nodes, not stage nodes) so nothing lingers into the fresh run
        for (EnemyController enemy : activeEnemies) {
            enemy.cleanup(bulletAppState);
        }
        activeEnemies.clear();
        if (portalNode != null) {
            portalNode.removeFromParent();
            portalNode = null;
            portalGeo = null;
            portalPosition = null;
        }

        // fresh run: back to stage 0 at base difficulty
        currentStageIndex = 0;
        loopCount = 0;
        difficultyScalar = 1.0f;

        currentStage = stages.get(currentStageIndex);
        currentStage.build(assetManager, rootNode, bulletAppState);

        if (!currentStage.isSafe()) {
            activeEnemies = currentStage.spawnEnemies(assetManager, rootNode, bulletAppState, loopCount);
            System.out.println("Spawned " + activeEnemies.size() + " enemies in " + currentStage.getName());
        }

        Vector3f spawnPoint = currentStage.getPlayerSpawnPoint();
        playerControl.warp(spawnPoint);

        updateLighting();
        System.out.println("Reset to fresh run at: " + currentStage.getName());
    }

    public void advanceStage() {
        if (currentStage == null) return;

        currentStage.cleanup(rootNode, bulletAppState);
        // detach leftover enemies and their effect nodes so nothing lingers
        // into the next biome (emitters are scene nodes, not stage nodes)
        for (EnemyController enemy : activeEnemies) {
            enemy.cleanup(bulletAppState);
        }
        activeEnemies.clear();
        if (portalNode != null) {
            portalNode.removeFromParent();
            portalNode = null;
            portalGeo = null;
            portalPosition = null;
        }

        currentStageIndex++;

        // cleared every combat stage, so loop back and start the next run
        if (currentStageIndex >= stages.size()) {
            currentStageIndex = 0;
            loopCount++;
            updateDifficultyScalar();
            System.out.println("Loop " + loopCount + " started! Difficulty: " + difficultyScalar + "x");
        }

        currentStage = stages.get(currentStageIndex);
        currentStage.build(assetManager, rootNode, bulletAppState);

        if (!currentStage.isSafe()) {
            activeEnemies = currentStage.spawnEnemies(assetManager, rootNode, bulletAppState, loopCount);
            System.out.println("Spawned " + activeEnemies.size() + " enemies in " + currentStage.getName());
        }

        updateLighting();
        System.out.println("Transitioned to: " + currentStage.getName());
    }

    public void update(float tpf, Vector3f playerPos, BetterCharacterControl playerControl) {
        if (currentStage == null) return;

        for (EnemyController enemy : activeEnemies) {
            enemy.update(tpf, playerPos, playerStats);
        }

        // sweep out any enemies that croaked this frame — only once their death
        // effect actually finished playing (canRemove), or the death pop would
        // never be seen
        activeEnemies.removeIf(e -> {
            if (e.canRemove()) {
                e.cleanup(bulletAppState);
                if (playerStats != null) {
                    // boss kills drop a real jackpot; normal kills scale gently per loop
                    int reward = e.isBoss() ? 200 + loopCount * 50 : 10 + loopCount * 5;
                    playerStats.addSoulDust(reward);
                }
                return true;
            }
            return false;
        });

        // Safe stages (any Sanctuary instance in the rotation) always keep an
        // open exit portal; combat stages only open theirs once everything's dead.
        boolean needsPortal = currentStage.isSafe() || activeEnemies.isEmpty();
        if (needsPortal && portalGeo == null) {
            spawnExitPortal(playerPos);
        }

        if (portalGeo != null) {
            // kept the portal non-rotating on purpose — a spinning door looks wrong
            // only care about X/Z distance here; it's a vertical doorway the
            // player walks through on the ground
            Vector3f horizontal = new Vector3f(
                playerPos.x - portalPosition.x, 0, playerPos.z - portalPosition.z);
            if (horizontal.length() < PORTAL_ACTIVATION_RANGE) {
                advanceStage();
            }
        }
    }

    private void spawnExitPortal(Vector3f playerPos) {
        if (portalGeo != null) return;

        // glowing oval portal; its base sits at y=0, so it reads as a tall
        // doorway you walk through
        portalPosition = new Vector3f(0, 4.2f, 25f);

        portalNode = new Node("Portal");
        // went with a stock jME3 Sphere (flattened into an oval) instead of a
        // hand-built triangle-fan mesh — that custom mesh's buffer upload crashes
        // this AMD OpenGL driver (EXCEPTION_ACCESS_VIOLATION in glBufferData, MultiPassLighting)
        com.jme3.scene.shape.Sphere portalMesh = new com.jme3.scene.shape.Sphere(16, 24, 4.2f);
        portalGeo = new Geometry("PortalGeometry", portalMesh);
        portalGeo.setLocalScale(2.2f / 4.2f, 1f, 0.12f);

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

        System.out.println("Portal spawned!");
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

    public float getDifficultyScale() {
        return difficultyScalar;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public List<EnemyController> getActiveEnemies() {
        return activeEnemies;
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
