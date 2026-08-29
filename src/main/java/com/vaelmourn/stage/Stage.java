package com.vaelmourn.stage;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.vaelmourn.EnemyController;

import java.util.List;

/**
 * Interface defining the contract for a stage (biome) in the roguelike game.
 * Each stage manages its own environment, enemies, lighting, and progression logic.
 */
public interface Stage {

    /**
     * Get the name of this stage (e.g., "Darkwood", "Sanctuary").
     */
    String getName();

    /**
     * Get the stage index (0 = Sanctuary, 1 = Darkwood, 2 = Ashen Wastes, 3 = Frozen Depths).
     */
    int getStageIndex();

    /**
     * Build the stage environment: terrain, trees, rocks, lighting, physics bodies.
     * Called once during initialization. Attach all geometry to parentNode.
     * Register all physics bodies with bulletAppState.
     *
     * @param assetManager jME asset manager for loading models
     * @param parentNode the scene graph node to attach this stage's geometry to
     * @param bulletAppState physics engine state
     */
    void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState);

    /**
     * Clean up and remove all stage geometry and physics bodies.
     * Called when transitioning away from this stage or during application shutdown.
     *
     * @param parentNode the parent node this stage was attached to
     * @param bulletAppState physics engine state
     */
    void cleanup(Node parentNode, BulletAppState bulletAppState);

    /**
     * Spawn enemies for this stage, applying difficulty scaling based on loop count.
     * Called each time the player enters the stage.
     *
     * @param assetManager jME asset manager for loading enemy models
     * @param parentNode the scene graph node to attach enemies to
     * @param bulletAppState physics engine state
     * @param loopCount the current roguelike loop (0, 1, 2, ...)
     * @return list of spawned enemies
     */
    List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                       BulletAppState bulletAppState, int loopCount);

    /**
     * Get the player spawn point for this stage.
     */
    Vector3f getPlayerSpawnPoint();

    /**
     * Get the sky color for this stage's atmosphere.
     */
    ColorRGBA getSkyColor();

    /**
     * Get the ambient light color for this stage.
     */
    ColorRGBA getAmbientColor();

    /**
     * Get the sun direction for this stage.
     */
    Vector3f getSunDirection();

    /**
     * Get the half-extent of the stage boundary (used for portal placement and boundary walls).
     */
    float getHalfExtent();
}
