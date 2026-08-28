package com.vaelmourn;

/**
 * Tracks the player's core RPG statistics: health, mana, experience,
 * level and the soul dust currency.
 */
public class PlayerStats {

    private String playerName = "Vael";

    // Health
    private float maxHealth = 100f;
    private float health = maxHealth;

    // Mana
    private float maxMana = 100f;
    private float mana = maxMana;

    // Experience / level
    private int level = 1;
    private float experience = 0f;
    private float experienceToNext = 100f;

    // Currency
    private int soulDust = 0;

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getMana() {
        return mana;
    }

    public float getMaxMana() {
        return maxMana;
    }

    public int getLevel() {
        return level;
    }

    public float getExperience() {
        return experience;
    }

    public float getExperienceToNext() {
        return experienceToNext;
    }

    public int getSoulDust() {
        return soulDust;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName == null || playerName.isEmpty() ? "Player" : playerName;
    }

    public void setHealth(float health) {
        this.health = Math.max(0f, Math.min(maxHealth, health));
    }

    public void damage(float amount) {
        setHealth(health - amount);
    }

    public void heal(float amount) {
        setHealth(health + amount);
    }

    public void setMana(float mana) {
        this.mana = Math.max(0f, Math.min(maxMana, mana));
    }

    public float getManaFraction() {
        return maxMana <= 0f ? 0f : mana / maxMana;
    }

    public float getHealthFraction() {
        return maxHealth <= 0f ? 0f : health / maxHealth;
    }

    public float getExperienceFraction() {
        return experienceToNext <= 0f ? 0f : experience / experienceToNext;
    }

    /**
     * Adds experience; levels up (and resets the bar) as many times as needed.
     */
    public void addExperience(float amount) {
        experience += amount;
        while (experience >= experienceToNext) {
            experience -= experienceToNext;
            level++;
            experienceToNext = experienceToNext * 1.3f;
        }
    }

    public void addSoulDust(int amount) {
        soulDust = Math.max(0, soulDust + amount);
    }

    public void spendSoulDust(int amount) {
        soulDust = Math.max(0, soulDust - amount);
    }
}
