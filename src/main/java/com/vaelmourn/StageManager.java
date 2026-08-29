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

/**
 * StageManager orchestrates stage transitions, enemy spawning, and roguelike loop progression.
 */
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

    /**
     * Initialize the first stage (Sanctuary). Called from simpleInitApp().
     */
    public void loadInitialStage(BetterCharacterControl playerControl) {
        if (stages.isEmpty()) {
            System.err.println("ERROR: No stages registered!");
            return;
        }

        currentStageIndex = 0;
        currentStage = stages.get(0);
        currentStage.build(assetManager, rootNode, bulletAppState);

        // Position player at spawn
        Vector3f spawnPoint = currentStage.getPlayerSpawnPoint();
        playerControl.warp(spawnPoint);

        // Update lighting for this stage
        updateLighting();

        System.out.println("Loaded initial stage: " + currentStage.getName());
    }

    /**
     * Advance to the next stage. Called when player enters portal or via other triggers.
     */
    public void advanceStage() {
        if (currentStage == null) return;

        // Clean up current stage
        currentStage.cleanup(rootNode, bulletAppState);
        activeEnemies.clear();
        if (portalNode != null) {
            portalNode.removeFromParent();
            portalNode = null;
            portalGeo = null;
            portalPosition = null;
        }

        // Move to next stage
        currentStageIndex++;

        // Check if we've completed all combat stages and need to loop back
        if (currentStageIndex >= stages.size()) {
            currentStageIndex = 0;
            loopCount++;
            updateDifficultyScalar();
            System.out.println("Loop " + loopCount + " started! Difficulty: " + difficultyScalar + "x");
        }

        currentStage = stages.get(currentStageIndex);
        currentStage.build(assetManager, rootNode, bulletAppState);

        // Spawn enemies (unless Sanctuary)
        if (currentStageIndex > 0) {
            activeEnemies = currentStage.spawnEnemies(assetManager, rootNode, bulletAppState, loopCount);
            System.out.println("Spawned " + activeEnemies.size() + " enemies in " + currentStage.getName());
        }

        updateLighting();
        System.out.println("Transitioned to: " + currentStage.getName());
    }

    /**
     * Update the stage each frame. Called from simpleUpdate().
     */
    public void update(float tpf, Vector3f playerPos, BetterCharacterControl playerControl) {
        if (currentStage == null) return;

        // Update enemies
        for (EnemyController enemy : activeEnemies) {
            enemy.update(tpf, playerPos, playerStats);
        }

        // Remove dead enemies
        activeEnemies.removeIf(e -> {
            if (e.isDead()) {
                e.cleanup(bulletAppState);
                if (playerStats != null) {
                    playerStats.addSoulDust(10); // TODO: scale by difficulty
                }
                return true;
            }
            return false;
        });

        // Check if the stage should have an exit portal: the Sanctuary hub always
        // has an exit so the player can leave, and combat stages get one when
        // cleared (all enemies dead).
        boolean needsPortal = currentStageIndex == 0
                || (currentStageIndex > 0 && activeEnemies.isEmpty());
        if (needsPortal && portalGeo == null) {
            spawnExitPortal(playerPos);
        }

        // Update portal
        if (portalGeo != null) {
            // The oval portal is intentionally non-rotating.

            // Check player proximity to portal (horizontal distance, since it's a
            // vertical doorway the player walks through on the ground).
            Vector3f horizontal = new Vector3f(
                playerPos.x - portalPosition.x, 0, playerPos.z - portalPosition.z);
            if (horizontal.length() < PORTAL_ACTIVATION_RANGE) {
                advanceStage();
            }
        }
    }

    private void spawnExitPortal(Vector3f playerPos) {
        if (portalGeo != null) return;

        // Create a glowing, non-rotating oval portal at center of the stage.
        // The oval's base rests on the ground (y=0) like a tall doorway.
        portalPosition = new Vector3f(0, 4.2f, 25f);

        portalNode = new Node("Portal");
        // Use a standard jME3 Sphere (flattened into an oval) instead of a hand-built
        // triangle-fan mesh: the custom mesh's buffer upload crashes this AMD OpenGL
        // driver (EXCEPTION_ACCESS_VIOLATION in glBufferData during MultiPassLighting).
        com.jme3.scene.shape.Sphere portalMesh = new com.jme3.scene.shape.Sphere(16, 24, 4.2f);
        portalGeo = new Geometry("PortalGeometry", portalMesh);
        portalGeo.setLocalScale(2.2f / 4.2f, 1f, 0.12f);

        Material portalMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        portalMat.setBoolean("UseMaterialColors", true);
        portalMat.setColor("Diffuse", new ColorRGBA(0.2f, 1f, 0.8f, 1f));
        portalMat.setColor("GlowColor", new ColorRGBA(0f, 0.8f, 0.5f, 1f));
        // The oval is a flat disc; make it visible from both sides.
        portalMat.getAdditionalRenderState().setFaceCullMode(com.jme3.material.RenderState.FaceCullMode.Off);
        portalGeo.setMaterial(portalMat);

        portalNode.attachChild(portalGeo);
        portalNode.setLocalTranslation(portalPosition);
        rootNode.attachChild(portalNode);

        System.out.println("Portal spawned!");
    }

    private void updateLighting() {
        // Remove old lights
        for (com.jme3.light.Light light : rootNode.getLocalLightList()) {
            rootNode.removeLight(light);
        }

        ColorRGBA skyColor = currentStage.getSkyColor();
        app.getViewPort().setBackgroundColor(skyColor);

        // Add new lights from stage
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(currentStage.getSunDirection());
        sun.setColor(ColorRGBA.White.mult(1.0f));
        rootNode.addLight(sun);

        AmbientLight ambient = new AmbientLight();
        ambient.setColor(currentStage.getAmbientColor());
        rootNode.addLight(ambient);
    }

    private void updateDifficultyScalar() {
        // 1.0x + 0.2x per loop, capped at 3.0x
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

    // Placeholder: inject PlayerStats via setter
    private PlayerStats playerStats;
    public void setPlayerStats(PlayerStats stats) {
        this.playerStats = stats;
    }
}
