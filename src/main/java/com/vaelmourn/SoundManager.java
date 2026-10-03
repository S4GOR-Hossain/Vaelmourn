package com.vaelmourn;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Central audio manager: one-loop BGM per biome (faded between stages), random
 * ambient drones, one-shot SFX with built-in overlap limiting (play() restarts
 * the same buffer instead of stacking copies), boss roars/attacks/deaths, NPC
 * voicelines and shop cues. Static facade over a single instance so gameplay
 * code just calls SoundManager.playX().
 */
public class SoundManager {

    private static final String DIR = "Sounds/";

    private static final float BGM_VOLUME = 0.45f;
    private static final float BGM_FADE_RATE = 5f;
    private static final float BGM_FADE_OUT_RATE = 1.6f;
    private static final float AMBIENT_VOLUME = 0.28f;

    private static final Random RNG = new Random();
    private static SoundManager inst;

    /**
     * User master volume from Settings. Scales every voice (BGM, ambience, SFX)
     * without changing their relative mix, and is clamped so a bad value from the
     * slider can never produce an out-of-range AudioNode volume.
     */
    private static float masterVolume = 1f;

    private final AssetManager assetManager;
    private final Node root;

    private final Map<String, AudioNode> sfx = new HashMap<>();
    /** Unscaled per-cue volumes, so a master-volume change can be re-applied exactly. */
    private final Map<String, Float> sfxBase = new HashMap<>();

    private AudioNode bgm;
    private AudioNode oldBgm;
    private String bgmTrack;
    private float bgmVol = 0f;
    private float oldBgmVol = 0f;

    private boolean ambientEnabled = false;
    private float ambientTimer = 6f;

    private String bossId;
    private boolean bossAlive = false;
    private float roarTimer = 4f;

    private float attackSoundCooldown = 0f;
    private float hitSoundCooldown = 0f;
    private float bossHurtCooldown = 0f;
    private final float[] npcCooldown = new float[3];

    private Stage lastStage;

    private SoundManager(AssetManager am, Node root) {
        this.assetManager = am;
        this.root = root;
    }

    public static void init(AssetManager am, Node root) {
        inst = new SoundManager(am, root);
    }

    public static void update(float tpf, Stage stage) {
        if (inst == null) return;
        inst.tick(tpf, stage);
    }

    public static float getMasterVolume() { return masterVolume; }

    /** Applies a new master volume to everything already playing. */
    public static void setMasterVolume(float v) {
        masterVolume = Math.max(0f, Math.min(1f, v));
        if (inst != null) inst.applyMasterVolume();
    }

    private void applyMasterVolume() {
        if (bgm != null) bgm.setVolume(bgmVol * masterVolume);
        if (oldBgm != null) oldBgm.setVolume(oldBgmVol * masterVolume);
        for (Map.Entry<String, AudioNode> e : sfx.entrySet()) {
            AudioNode node = e.getValue();
            Float base = sfxBase.get(e.getKey());
            if (node != null && base != null) node.setVolume(base * masterVolume);
        }
    }

    private void tick(float tpf, Stage stage) {
        if (bgm != null) {
            bgmVol += (BGM_VOLUME - bgmVol) * Math.min(1f, tpf * BGM_FADE_RATE);
            bgm.setVolume(bgmVol * masterVolume);
        }
        if (oldBgm != null) {
            oldBgmVol -= tpf * BGM_FADE_OUT_RATE;
            if (oldBgmVol <= 0f) {
                oldBgm.stop();
                oldBgm.removeFromParent();
                oldBgm = null;
            } else {
                oldBgm.setVolume(oldBgmVol * masterVolume);
            }
        }

        if (stage != lastStage) {
            lastStage = stage;
            applyStage(stage);
        }

        if (attackSoundCooldown > 0f) attackSoundCooldown -= tpf;
        if (hitSoundCooldown > 0f) hitSoundCooldown -= tpf;
        if (bossHurtCooldown > 0f) bossHurtCooldown -= tpf;
        if (roarTimer > 0f) {
            roarTimer -= tpf;
            if (roarTimer <= 0f) maybeRoar();
        }
        if (npcCooldown[1] > 0f) npcCooldown[1] -= tpf;
        if (npcCooldown[2] > 0f) npcCooldown[2] -= tpf;

        if (ambientEnabled) {
            ambientTimer -= tpf;
            if (ambientTimer <= 0f) {
                play("eerie_ambient" + (1 + RNG.nextInt(4)) + ".wav", AMBIENT_VOLUME);
                ambientTimer = 16f + RNG.nextFloat() * 14f;
            }
        }
    }

    private void applyStage(Stage stage) {
        if (stage == null) {
            setBgm(null);
            ambientEnabled = false;
            return;
        }
        boolean bossFight = stage instanceof BossStage;
        ambientEnabled = !stage.isSafe() && !bossFight;
        if (bossFight) {
            // the boss itself registers the moment it spawns, a frame after this
            setBgm(null);
        } else {
            setBgm(bgmFor(stage));
            clearBoss();
        }
    }

    private String bgmFor(Stage stage) {
        if (stage.isSafe()) return "sanctuary_bgm.wav";
        if (stage instanceof DarkwoodStage) return "darkwood_forest_bgm.wav";
        if (stage instanceof AshenWastesStage) return "ashen_waste_bgm.wav";
        if (stage instanceof FrozenDepthsStage) return "frozen_depths_bgm.wav";
        if (stage instanceof JungleStage) return "forest_bgm.wav";
        if (stage instanceof DarkRoyaleKingdomCourtStage) return "kingdom_court_bgm.wav";
        return null;
    }

    private void setBgm(String file) {
        if (file == null ? bgmTrack == null : file.equals(bgmTrack)) return;
        if (bgm != null) {
            oldBgm = bgm;
            oldBgmVol = bgmVol;
        }
        bgm = null;
        bgmVol = 0f;
        bgmTrack = file;
        if (file == null) return;
        try {
            bgm = makeNode(DIR + file, true);
            root.attachChild(bgm);
            bgm.play();
        } catch (Exception e) {
            System.err.println("SoundManager: BGM '" + file + "' failed: " + e.getMessage());
            bgm = null;
            bgmTrack = null;
        }
    }

    private void play(String file, float volume) {
        if (file == null) return;
        try {
            AudioNode node = sfx.get(file);
            if (node == null) {
                node = makeNode(DIR + file, false);
                sfx.put(file, node);
                sfxBase.put(file, volume);
                root.attachChild(node);
            }
            node.setVolume(volume * masterVolume);
            node.play();
        } catch (Exception e) {
            System.err.println("SoundManager: '" + file + "' failed: " + e.getMessage());
        }
    }

    /** Buffer-loaded, head-space (stereo-friendly) audio node; loops only for BGM. */
    private AudioNode makeNode(String file, boolean loop) {
        AudioData data = assetManager.loadAudio(file);
        AudioNode node = new AudioNode();
        node.setPositional(false);
        node.setLooping(loop);
        node.setReverbEnabled(false);
        node.setAudioData(data, new AudioKey(file, false));
        return node;
    }

    public static void playSwordSwing(boolean heavy) {
        if (inst == null) return;
        inst.play(heavy ? "player_sword_heavy.wav" : "player_sword_light.wav", heavy ? 0.8f : 0.6f);
    }

    public static void playPlayerHit() {
        if (inst == null) return;
        if (inst.hitSoundCooldown > 0f) return;
        inst.hitSoundCooldown = 0.08f;
        inst.play("player_hitting_enemy.wav", 0.7f);
    }

    public static void playPlayerHurt() {
        if (inst == null) return;
        inst.play(RNG.nextBoolean() ? "player_hurt.wav" : "player_hurt2.wav", 0.85f);
    }

    public static void playPlayerDeath() {
        if (inst == null) return;
        inst.play("player_death.wav", 1f);
    }

    public static void playEnemyAttack() {
        if (inst == null) return;
        if (inst.attackSoundCooldown > 0f) return;
        inst.attackSoundCooldown = 0.12f;
        inst.play("enemy_attack" + (1 + RNG.nextInt(3)) + ".wav", 0.7f);
    }

    public static void playEnemyDeath() {
        if (inst == null) return;
        inst.play("enemy_death1.wav", 0.8f);
    }

    public static void playShopOpen() {
        if (inst == null) return;
        inst.play("shop_open.wav", 0.8f);
    }

    /** Bomb leave-the-hand whoosh, played on a successful Q throw. */
    public static void playBombThrow() {
        if (inst == null) return;
        inst.play("bomb_throwing_sound.wav", 0.75f);
    }

    /** Detonation boom, played once per bomb when the fuse expires. */
    public static void playBombExplosion() {
        if (inst == null) return;
        inst.play("bomb_explosion_sound.wav", 1f);
    }

    /** Gulp when a potion is actually consumed. */
    public static void playPotionDrink() {
        if (inst == null) return;
        inst.play("potion_drinking.wav", 0.7f);
    }

    public static void registerBoss(String id) {
        if (inst == null) return;
        inst.bossId = id;
        inst.bossAlive = true;
        inst.roarTimer = 3f + RNG.nextFloat() * 4f;
    }

    public static void unregisterBoss(String id) {
        if (inst == null) return;
        if (id == null || id.equals(inst.bossId)) {
            inst.bossAlive = false;
        }
    }

    public static void playBossAttack(String id) {
        if (inst == null) return;
        BossSounds s = soundsFor(id);
        if (s != null && s.attacks != null && s.attacks.length > 0) {
            inst.play(s.attacks[RNG.nextInt(s.attacks.length)], 0.85f);
        }
        // a roar right as the boss swings would double up â€” push it back
        inst.roarTimer = Math.max(inst.roarTimer, 5f + RNG.nextFloat() * 3f);
    }

    public static void playBossHurt(String id) {
        if (inst == null) return;
        if (inst.bossHurtCooldown > 0f) return;
        inst.bossHurtCooldown = 0.14f;
        BossSounds s = soundsFor(id);
        if (s != null && s.hurt != null) inst.play(s.hurt, 0.85f);
    }

    /** Immediate roar (phase transitions); also pushes ambient roars well back. */
    public static void playBossRoar(String id) {
        if (inst == null) return;
        BossSounds s = soundsFor(id);
        if (s != null && s.roars != null && s.roars.length > 0) {
            inst.play(s.roars[RNG.nextInt(s.roars.length)], 0.9f);
        }
        inst.roarTimer = Math.max(inst.roarTimer, 10f + RNG.nextFloat() * 6f);
    }

    public static void playBossDamageDeal(String id) {
        if (inst == null) return;
        BossSounds s = soundsFor(id);
        if (s != null && s.deal != null) inst.play(s.deal, 0.9f);
    }

    public static void onBossDefeated(String id) {
        if (inst == null) return;
        BossSounds s = soundsFor(id);
        if (s != null && s.defeat != null) inst.play(s.defeat, 1f);
        inst.play("boss_defeat.wav", 1f);
        inst.bossAlive = false;
    }

    public static void maybeNpcVoice(int pool, Vector3f npcPos, Vector3f playerPos,
                                     Vector3f facing, float tpf) {
        if (inst == null || pool < 1 || pool > 2 || npcPos == null
                || playerPos == null || facing == null) return;
        if (inst.npcCooldown[pool] > 0f) {
            inst.npcCooldown[pool] -= tpf;
            return;
        }
        Vector3f to = npcPos.subtract(playerPos);
        if (to.length() > 6f) return;
        to.y = 0f;
        if (to.lengthSquared() < 1e-4f) return;
        if (facing.dot(to.normalizeLocal()) < 0.85f) return;
        int lines = pool == 1 ? 2 : 3;
        inst.play("npc" + pool + "_voiceline_" + (1 + RNG.nextInt(lines)) + ".wav", 0.8f);
        inst.npcCooldown[pool] = 14f + RNG.nextFloat() * 10f;
    }

    private void maybeRoar() {
        if (!bossAlive || bossId == null) return;
        BossSounds s = soundsFor(bossId);
        if (s == null || s.roars == null || s.roars.length == 0) return;
        play(s.roars[RNG.nextInt(s.roars.length)], 0.9f);
        roarTimer = 11f + RNG.nextFloat() * 9f;
    }

    private void clearBoss() {
        bossId = null;
        bossAlive = false;
    }

    private static BossSounds soundsFor(String id) {
        if (id == null) return null;
        return switch (id) {
            case "tree_warden" -> new BossSounds(
                    new String[]{"tree_warden_smash.wav"}, "tree_warden_damage_take.wav",
                    "tree_warden_damage_deal.wav", null,
                    new String[]{"tree_warden_roar.wav", "tree_warden_roar2.wav"});
            case "hell_hound" -> new BossSounds(
                    new String[]{"hell_hound_attack.wav"}, "hell_hound_damage_take.wav",
                    null, "hell_hound_defeat.wav",
                    new String[]{"hell_hound_roar.wav"});
            case "bee_keeper" -> new BossSounds(
                    new String[]{"bee_keeper_attack.wav"}, null,
                    null, "bee_keeper_death.wav",
                    new String[]{"bee_keeper_roar.wav"});
            case "frost_giant" -> new BossSounds(
                    null, null, null, "frost_giant_defeat.wav",
                    new String[]{"frost_giant_roar.wav"});
            case "fallen_king" -> new BossSounds(
                    new String[]{"fallen_king_attack.wav", "fallen_king_attack2.wav"}, null,
                    null, null,
                    new String[]{"fallen_king_roar.wav", "fallen_king_roar2.wav"});
            default -> null;
        };
    }

    private static class BossSounds {
        final String[] attacks;
        final String hurt;
        final String deal;
        final String defeat;
        final String[] roars;

        BossSounds(String[] attacks, String hurt, String deal, String defeat, String[] roars) {
            this.attacks = attacks;
            this.hurt = hurt;
            this.deal = deal;
            this.defeat = defeat;
            this.roars = roars;
        }
    }
}