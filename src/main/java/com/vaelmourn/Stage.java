package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.List;

/**
 * Stage interface — defines the contract for all biome implementations.
 * Each stage handles its own environment, enemies, and physics lifecycle.
 */
public interface Stage {

    /**
     * Load the stage: build geometry, attach to scene, initialize physics.
     * Called once during initialization; stage is not yet active.
     */
    void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState);

    /**
     * Clean up the stage: remove geometry, physics bodies, enemies.
     * Called when transitioning away from this stage.
     */
    void cleanup(Node parentNode, BulletAppState bulletAppState);

    /**
     * Spawn enemies for this stage, scaled by difficulty.
     * @param loopCount the current roguelike loop (0-based)
     * @return list of active enemies
     */
    List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                        BulletAppState bulletAppState, int loopCount);

    /**
     * @return the player's spawn point in this stage
     */
    Vector3f getPlayerSpawnPoint();

    /**
     * @return the viewport background color for this stage's atmosphere
     */
    ColorRGBA getSkyColor();

    /**
     * @return the ambient light color (intensity already baked in)
     */
    ColorRGBA getAmbientColor();

    /**
     * @return the sun direction vector (normalized)
     */
    Vector3f getSunDirection();

    /**
     * @return half-extent of the stage boundaries
     */
    float getHalfExtent();

    /**
     * @return display name of the stage
     */
    String getName();

    /**
     * @return 0-based stage index (0=Sanctuary, 1=Darkwood, 2=Ashen Wastes, 3=Frozen Depths)
     */
    int getStageIndex();
}
