package com.vaelmourn;

/**
 * Tracks the player's core RPG statistics: health, mana, experience,
 * level and the soul dust currency.
 */
public class PlayerStats {

    private String playerName = "Vael";

    // health pool
    private float maxHealth = 100f;
    private float health = maxHealth;

    // mana pool
    private float maxMana = 100f;
    private float mana = maxMana;

    // xp and leveling
    private int level = 1;
    private float experience = 0f;
    private float experienceToNext = 100f;

    // currency
    private int soulDust = 0;

    // combat / movement stats that the stats bar reads
    private float averageDamage = 8f;    // rough damage per hit
    private float armorPoints = 10f;     // shield + armor combined
    private float movementSpeed = 5f;    // walking speed
    private float attackSpeed = 1.2f;    // swings per second, roughly

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

    public float getAverageDamage() {
        return averageDamage;
    }

    public void setAverageDamage(float averageDamage) {
        this.averageDamage = Math.max(0f, averageDamage);
    }

    public float getArmorPoints() {
        return armorPoints;
    }

    public void setArmorPoints(float armorPoints) {
        this.armorPoints = Math.max(0f, armorPoints);
    }

    public float getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(float movementSpeed) {
        this.movementSpeed = Math.max(0f, movementSpeed);
    }

    public float getAttackSpeed() {
        return attackSpeed;
    }

    public void setAttackSpeed(float attackSpeed) {
        this.attackSpeed = Math.max(0f, attackSpeed);
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
