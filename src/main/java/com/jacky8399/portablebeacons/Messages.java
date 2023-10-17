package com.jacky8399.portablebeacons;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public class Messages {
    // Effects Toggle GUI
    public static String effectsToggleBeaconatorName;
    public static String effectsToggleBeaconatorOff;
    public static String effectsToggleBeaconatorLevel;
    public static String effectsToggleBeaconatorSelected;
    public static String effectsToggleBeaconatorNotSelected;

    // Breakdown
    public static String effectsToggleBreakdownEffect;
    public static String effectsToggleBreakdownEffectsGrouped;
    public static String effectsToggleBreakdownBeaconatorBase;
    public static String effectsToggleBreakdownBeaconatorInRange;
    public static String effectsToggleBreakdownExpReduction;
    public static String effectsToggleBreakdownSum;

    public static void loadMessages(Configuration messages) {

        var effectsToggle = messages.getConfigurationSection("effects-toggle");
        var beaconator = effectsToggle.getConfigurationSection("beaconator");
        effectsToggleBeaconatorName = get(beaconator, "name");
        effectsToggleBeaconatorOff = get(beaconator, "disabled"); // fucking YAML
        effectsToggleBeaconatorLevel = get(beaconator, "level");
        effectsToggleBeaconatorSelected = get(beaconator, "selected");
        effectsToggleBeaconatorNotSelected = get(beaconator, "not-selected");

        var breakdown = effectsToggle.getConfigurationSection("breakdown");
        effectsToggleBreakdownEffect = get(breakdown, "effect");
        effectsToggleBreakdownEffectsGrouped = get(breakdown, "effects");
        effectsToggleBreakdownBeaconatorBase = get(breakdown, "beaconator-base");
        effectsToggleBreakdownBeaconatorInRange = get(breakdown, "beaconator-in-range");
        effectsToggleBreakdownExpReduction = get(breakdown, "exp-reduction");
        effectsToggleBreakdownSum = get(breakdown, "sum");
    }

    private static String get(ConfigurationSection section, String path) {
        return Objects.requireNonNull(Config.translateColor(section.getString(path)));
    }
}
