package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Color-tinted particle aura that clings to the player model while a potion
 * buff is running. One emitter per buff, in the same fixed order as
 * {@link PlayerStats.Buff}, so a stacked potion literally stacks effects.
 * Emitters are turned on/off by the buff timers in {@link #update(PlayerStats)};
 * disabling an emitter lets its few remaining sparks burn out instead of
 * vanishing mid-air.
 */
public class PlayerBuffEffects {

    // one particle hue per buff, mirrored by the HUD timer pills
    private static final ColorRGBA[] BUFF_COLORS = {
            new ColorRGBA(0.35f, 0.85f, 1.00f, 1f),  // Speed - icy cyan
            new ColorRGBA(1.00f, 0.50f, 0.15f, 1f),  // Strength - molten orange
            new ColorRGBA(1.00f, 0.85f, 0.20f, 1f),  // Critical - rich gold
            new ColorRGBA(0.35f, 0.90f, 0.40f, 1f),  // Regen - verdant green
    };

    // aura look & feel
    private static final int PARTICLES_PER_SEC = 26;
    private static final int MAX_PARTICLES = 64;
    private static final float EMITTER_HEIGHT = 1.2f; // mid-torso of the player model
    private static final float START_SIZE = 0.08f;
    private static final float END_SIZE = 0.02f;
    private static final float LIFE_MIN = 0.5f;
    private static final float LIFE_MAX = 1.3f;

    private final Node playerNode;
    private final ParticleEmitter[] emitters = new ParticleEmitter[PlayerStats.Buff.values().length];

    public PlayerBuffEffects(AssetManager assetManager, Node playerNode) {
        this.playerNode = playerNode;
        Texture softDot = buildSoftParticleTexture(assetManager);
        PlayerStats.Buff[] buffs = PlayerStats.Buff.values();
        for (int i = 0; i < buffs.length; i++) {
            ColorRGBA color = BUFF_COLORS[i];
            ParticleEmitter pe = new ParticleEmitter("buff-" + buffs[i].name().toLowerCase(),
                    ParticleMesh.Type.Triangle, MAX_PARTICLES);
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
            mat.setTexture("Texture", softDot);
            pe.setMaterial(mat);
            pe.setImagesX(1);
            pe.setImagesY(1);
            pe.setStartColor(color);
            pe.setEndColor(new ColorRGBA(color.r, color.g, color.b, 0f));
            pe.setStartSize(START_SIZE);
            pe.setEndSize(END_SIZE);
            // gentle upward swirl so sparks drift around the body like embers
            pe.setGravity(0f, 1.0f, 0f);
            pe.setLowLife(LIFE_MIN);
            pe.setHighLife(LIFE_MAX);
            pe.setVelocityVariation(0.8f);
            pe.setLocalTranslation(0f, EMITTER_HEIGHT, 0f);
            pe.setParticlesPerSec(PARTICLES_PER_SEC);
            pe.setEnabled(false);
            playerNode.attachChild(pe);
            emitters[i] = pe;
        }
    }

    /**
     * Pokes each emitter according to its buff timer: active buff = emitting,
     * expired buff = idling (sparks fade on their own within a second).
     */
    public void update(PlayerStats playerStats) {
        PlayerStats.Buff[] buffs = PlayerStats.Buff.values();
        for (int i = 0; i < buffs.length; i++) {
            boolean active = playerStats != null && playerStats.buffFraction(buffs[i]) > 0f;
            ParticleEmitter pe = emitters[i];
            if (active && !pe.isEnabled()) {
                pe.setEnabled(true);
            } else if (!active && pe.isEnabled()) {
                pe.setEnabled(false);
            }
        }
    }

    private static Texture buildSoftParticleTexture(AssetManager assetManager) {
        int size = 32;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float center = size / 2f;
        float radius = size / 2f - 1f;
        RadialGradientPaint paint = new RadialGradientPaint(
                center, center, radius,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(255, 255, 255, 255),
                        new Color(255, 255, 255, 160),
                        new Color(255, 255, 255, 0)});
        g2d.setPaint(paint);
        g2d.fillOval(0, 0, size, size);
        g2d.dispose();

        AWTLoader loader = new AWTLoader();
        Texture2D tex = new Texture2D(loader.load(img, false));
        tex.setMinFilter(Texture.MinFilter.Trilinear);
        tex.setMagFilter(Texture.MagFilter.Bilinear);
        return tex;
    }
}