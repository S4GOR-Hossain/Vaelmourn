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

    void build(AssetManager assetManager, Node parentNode, BulletAppState bulletAppState);

    void cleanup(Node parentNode, BulletAppState bulletAppState);

    List<EnemyController> spawnEnemies(AssetManager assetManager, Node parentNode,
                                        BulletAppState bulletAppState, int loopCount);

    Vector3f getPlayerSpawnPoint();

    ColorRGBA getSkyColor();

    /** @return the ambient light color (intensity already baked in) */
    ColorRGBA getAmbientColor();

    Vector3f getSunDirection();

    float getHalfExtent();

    String getName();

    /** @return 0-based stage index (0=Sanctuary, 1=Darkwood, 2=Ashen Wastes, 3=Frozen Depths) */
    int getStageIndex();

    /**
     * @return true for non-combat/preparation stages (Sanctuary-style hubs).
     * Safe stages never spawn enemies and always keep their exit portal open.
     */
    default boolean isSafe() {
        return false;
    }
}
