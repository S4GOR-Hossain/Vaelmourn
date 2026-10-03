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
public interface Stage extends Bombs.GroundSampler {

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

    /**
     * Where the exit portal should stand. The portal is a flat oval whose base sits at
     * the local origin, so the returned y is the ground it rests on and the manager
     * raises it by {@code PORTAL_BASE_HEIGHT}.
     *
     * <p>Default suits flat stages. Stages built on a height field must override this
     * and return the sampled terrain height, otherwise the portal is buried in high
     * ground or floating over dips.</p>
     */
    default Vector3f getExitPortalGround() {
        return new Vector3f(0f, 0f, 25f);
    }

    /**
     * Terrain surface height at a world XZ, for anything that has to sit on the ground
     * without going through physics - thrown bombs, mainly, which integrate their own
     * gravity and would otherwise sink into any level with a height difference.
     *
     * <p>Default suits flat stages. Stages built on a height field must override this
     * and return the same value their own {@code heightAt} gives, so a projectile and
     * the terrain under it can never disagree.</p>
     */
    default float groundHeightAt(float x, float z) {
        return 0f;
    }

    /**
     * Enemy count for a stage, doubled once per completed cycle.
     *
     * <p>A cycle ends when the Fallen King falls; the run then wraps to a fresh
     * Sanctuary and the player walks the whole rotation again. Every level doubles its
     * roster each cycle, so cycle 1 is 2x the first run, cycle 2 is 4x, and so on.</p>
     *
     * <p>The multiplier is capped at {@link #MAX_ENEMY_COUNT_MULTIPLIER}. HP and damage
     * keep doubling without limit because they cost nothing to apply, but an uncapped
     * roster would put hundreds of live bodies in one arena and stall the physics step,
     * which reads as a crash rather than as difficulty.</p>
     *
     * @param baseCount  the roster the stage would use on the first cycle
     * @param loopCount  completed cycles, i.e. {@code 0} on the opening run
     */
    static int loopedEnemyCount(int baseCount, int loopCount) {
        if (loopCount <= 0) return baseCount;
        // 2x on the first wrap, 4x from the second wrap onward
        int multiplier = loopCount == 1 ? 2 : MAX_ENEMY_COUNT_MULTIPLIER;
        return baseCount * multiplier;
    }

    /** Ceiling on the per-cycle roster multiplier. 4 means cycles 2 and up all run 4x. */
    int MAX_ENEMY_COUNT_MULTIPLIER = 4;
}
