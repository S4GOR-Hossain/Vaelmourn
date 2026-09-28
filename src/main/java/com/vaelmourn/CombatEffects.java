package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.BillboardControl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Combat-feel layer: floating damage numbers, camera shake, hit/death/hurt sounds
 * and the player hurt-flash. Nothing here permanently changes the camera position.
 */
public class CombatEffects {

    // tuning knobs (gameplay feel lives here, not scattered around)
    private static final int MAX_DAMAGE_NUMBERS = 40;
    private static final float DAMAGE_NUMBER_DURATION = 0.95f;
    private static final float DAMAGE_NUMBER_RISE = 1.6f;   // meters it floats up in its life
    private static final float DAMAGE_NUMBER_SIZE = 1.6f;   // world-space text size

    private static final float HIT_SHAKE_INTENSITY = 0.09f;
    private static final float HIT_SHAKE_DURATION = 0.14f;
    private static final float KILL_SHAKE_INTENSITY = 0.09f;
    private static final float KILL_SHAKE_DURATION = 0.20f;

    private static final float PLAYER_HURT_FLASH_DURATION = 0.15f;
    private static final float PLAYER_HURT_SHAKE_INTENSITY = 0.13f;
    private static final float PLAYER_HURT_SHAKE_DURATION = 0.24f;

    private static final float HIT_SOUND_VOLUME = 0.85f;
    private static final float DEATH_SOUND_VOLUME = 1.0f;
    private static final float HURT_SOUND_VOLUME = 0.9f;
    private static final float SWING_SOUND_VOLUME = 0.6f;
    private static final float SWING_SOUND_VOLUME_HEAVY = 0.8f;
    private static final float HEAVY_SWING_SHAKE_INTENSITY = 0.06f;
    private static final float HEAVY_SWING_SHAKE_DURATION = 0.12f;

    private final AssetManager assetManager;
    private final Node worldRoot;
    private BitmapFont font;

    private final Random random = new Random();
    private float shakeTimer = 0f;
    private float shakeSpan = 0f;
    private float shakeMag = 0f;

    private final List<DmgText> numbers = new ArrayList<>();

    private final Map<String, AudioNode> audioCache = new HashMap<>();

    private final Spatial playerModel;
    private final List<Geometry> playerGeos = new ArrayList<>();
    private final Map<Geometry, Material> playerOriginalMats = new HashMap<>();
    private final Map<Geometry, Material> playerFlashMats = new HashMap<>();
    private float playerFlashTimer = 0f;
    private boolean playerFlashOn = false;

    // reuse scratch objects so the per-frame loops never allocate
    private final ColorRGBA textColorScratch = new ColorRGBA(1f, 0.95f, 0.45f, 1f);
    private final Vector3f shakeOut = new Vector3f();

    private static class DmgText {
        final BitmapText text;
        final Vector3f start;
        final float life;
        final float driftX;
        float age = 0f;

        DmgText(BitmapText text, Vector3f start, float driftX) {
            this.text = text;
            this.start = start;
            this.life = DAMAGE_NUMBER_DURATION;
            this.driftX = driftX;
        }
    }

    public CombatEffects(AssetManager assetManager, Node worldRoot, Spatial playerModel) {
        this.assetManager = assetManager;
        this.worldRoot = worldRoot;
        this.playerModel = playerModel;
        try {
            this.font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        } catch (Exception e) {
            System.err.println("CombatEffects: no font, damage numbers disabled: " + e.getMessage());
        }
        gatherPlayerGeometries(playerModel);
    }

    private void gatherPlayerGeometries(Spatial spatial) {
        if (spatial == null) return;
        if (spatial instanceof Geometry g) {
            playerGeos.add(g);
            playerOriginalMats.put(g, g.getMaterial());
        } else if (spatial instanceof Node n) {
            for (Spatial child : n.getChildren()) {
                gatherPlayerGeometries(child);
            }
        }
    }

    public void onEnemyHit(EnemyController enemy, float damage, boolean killed, float shakeAmp) {
        if (damage > 0f && font != null && enemy != null) {
            spawnDamageNumber(enemy.getPosition().add(0f, 1.85f, 0f), Math.round(damage));
        }
        startShake(killed ? KILL_SHAKE_INTENSITY : shakeAmp,
                killed ? KILL_SHAKE_DURATION : HIT_SHAKE_DURATION);
        playSound(killed ? "Sounds/kill.wav" : "Sounds/hit.wav",
                enemy != null ? enemy.getPosition() : null,
                killed ? DEATH_SOUND_VOLUME : HIT_SOUND_VOLUME);
    }

    public void spawnDamageNumber(Vector3f worldPos, int amount) {
        if (font == null) return;
        if (numbers.size() >= MAX_DAMAGE_NUMBERS) {
            DmgText oldest = numbers.remove(0);
            oldest.text.removeFromParent();
        }

        BitmapText bt = new BitmapText(font, false);
        bt.setText("" + amount);
        bt.setSize(DAMAGE_NUMBER_SIZE);
        bt.setColor(ColorRGBA.White);
        bt.addControl(new BillboardControl());

        float jitterX = (random.nextFloat() - 0.5f) * 0.5f;
        bt.setLocalTranslation(worldPos);
        worldRoot.attachChild(bt);

        numbers.add(new DmgText(bt, worldPos.clone(), jitterX));
    }

    public void onPlayerDamaged() {
        flashPlayer(PLAYER_HURT_FLASH_DURATION);
        startShake(PLAYER_HURT_SHAKE_INTENSITY, PLAYER_HURT_SHAKE_DURATION);
        playSound("Sounds/hurt.wav",
                playerModel != null ? playerModel.getWorldTranslation() : null, HURT_SOUND_VOLUME);
    }

    public void flashPlayer(float seconds) {
        playerFlashTimer = Math.max(playerFlashTimer, seconds);
        applyPlayerFlashMaterials();
    }

    private void applyPlayerFlashMaterials() {
        if (playerFlashOn || playerGeos.isEmpty()) return;
        playerFlashOn = true;
        for (Geometry g : playerGeos) {
            if (g.getMaterial() == null) continue;
            Material flash = playerFlashMats.get(g);
            if (flash == null) {
                flash = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                flash.setColor("Color", new ColorRGBA(1f, 0.75f, 0.75f, 1f));
                playerFlashMats.put(g, flash);
            }
            g.setMaterial(flash);
        }
    }

    private void restorePlayerMaterials() {
        if (!playerFlashOn) return;
        playerFlashOn = false;
        for (Geometry g : playerGeos) {
            if (g == null) continue;
            Material orig = playerOriginalMats.get(g);
            if (orig != null) {
                g.setMaterial(orig);
            }
        }
    }

    public void onPlayerAttack(Vector3f atPos, boolean heavy) {
        playSound("Sounds/swing.wav", atPos, heavy ? SWING_SOUND_VOLUME_HEAVY : SWING_SOUND_VOLUME);
        if (heavy) startShake(HEAVY_SWING_SHAKE_INTENSITY, HEAVY_SWING_SHAKE_DURATION);
    }

    public void update(float tpf) {
        for (int i = numbers.size() - 1; i >= 0; i--) {
            DmgText nt = numbers.get(i);
            nt.age += tpf;
            float k = nt.age / nt.life;
            nt.text.setLocalTranslation(
                    nt.start.x + nt.driftX * k,
                    nt.start.y + DAMAGE_NUMBER_RISE * k,
                    nt.start.z);
            float alpha = FastMath.clamp(1f - k * 1.4f, 0f, 1f);
            textColorScratch.setAlpha(alpha);
            nt.text.setColor(textColorScratch);
            if (nt.age >= nt.life) {
                nt.text.removeFromParent();
                numbers.remove(i);
            }
        }

        if (playerFlashTimer > 0f) {
            playerFlashTimer -= tpf;
            if (playerFlashTimer <= 0f) {
                restorePlayerMaterials();
            }
        }
    }

    /** Positional offset to add to the camera each frame; zero when idle, so follow is never overridden. */
    public Vector3f getShakeOffset(float tpf) {
        if (shakeTimer <= 0f) {
            shakeTimer = 0f;
            shakeMag = 0f;
            return Vector3f.ZERO;
        }
        shakeTimer -= tpf;
        float intensity = shakeMag * FastMath.clamp(shakeTimer / shakeSpan, 0f, 1f);
        shakeOut.set((random.nextFloat() * 2f - 1f) * intensity,
                (random.nextFloat() * 2f - 1f) * intensity * 0.65f, 0f);
        if (shakeTimer <= 0f) {
            shakeTimer = 0f;
            shakeMag = 0f;
        }
        return shakeOut;
    }

    public void startShake(float magnitude, float seconds) {
        // refresh rather than stack so rapid hits don't whip the camera around
        shakeMag = Math.max(shakeMag, Math.abs(magnitude));
        shakeSpan = Math.max(shakeSpan, seconds);
        shakeTimer = shakeSpan;
    }

    private void playSound(String name, Vector3f position, float volume) {
        try {
            AudioNode node = audioCache.get(name);
            if (node == null) {
                node = new AudioNode(assetManager, name, AudioData.DataType.Buffer);
                node.setPositional(true);
                node.setDirectional(false);
                node.setReverbEnabled(false);
                audioCache.put(name, node);
            }
            node.setVolume(volume);
            if (position != null) {
                node.setLocalTranslation(position);
            }
            node.playInstance();
        } catch (Exception e) {
            System.err.println("CombatEffects: sound '" + name + "' failed: " + e.getMessage());
        }
    }
}