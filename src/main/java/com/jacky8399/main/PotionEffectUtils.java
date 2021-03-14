package com.jacky8399.main;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

public class PotionEffectUtils {
    /**
     * A bi-map of Bukkit effect names to vanilla names
     */
    private static final ImmutableBiMap<String, String> VANILLA_EFFECT_NAMES = ImmutableBiMap.<String, String>builder()
            .put("slow", "slowness")
            .put("fast_digging", "haste")
            .put("slow_digging", "mining_fatigue")
            .put("increase_damage", "strength")
            .put("heal", "instant_health")
            .put("harm", "instant_damage")
            .put("jump", "jump_boost")
            .put("confusion", "nausea")
            .put("damage_resistance", "resistance")
            .build();

    @NotNull
    public static Optional<PotionEffectType> parsePotion(String input, boolean allowBukkitNames) {
        input = input.toLowerCase(Locale.US);
        String bukkitName = VANILLA_EFFECT_NAMES.inverse().get(input);
        if (!allowBukkitNames && VANILLA_EFFECT_NAMES.containsKey(input)) // is a Bukkit name
            return Optional.empty();
        return Optional.ofNullable(PotionEffectType.getByName(bukkitName != null ? bukkitName : input));
    }

    private static final ImmutableSet<PotionEffectType> NEGATIVE_EFFECTS =
            ImmutableSet.of(PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.WEAKNESS, PotionEffectType.HARM,
                    PotionEffectType.CONFUSION, PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.POISON,
                    PotionEffectType.WITHER, PotionEffectType.LEVITATION);
    public static boolean isNegative(PotionEffectType potion) {
        return NEGATIVE_EFFECTS.contains(potion);
    }

    @NotNull
    public static String getName(PotionEffectType potion) {
        String name = potion.getName().toLowerCase(Locale.US);
        return VANILLA_EFFECT_NAMES.getOrDefault(name, name);
    }

    @NotNull
    public static String toRomanNumeral(int i) {
        switch (i) {
            case 1:
                return "";
            case 2:
                return " II";
            case 3:
                return " III";
            case 4:
                return " IV";
            case 5:
                return " V";
            case 6:
                return " VI";
            case 7:
                return " VII";
            case 8:
                return " VIII";
            case 9:
                return " IX";
            case 10:
                return " X";
            default:
                return " " + i;
        }
    }

    @NotNull
    public static String getDisplayName(PotionEffectType effect, int amplifier) {
        Config.PotionEffectInfo info = Config.effects.get(effect);
        if (info != null && info.displayName != null) {
            if (info.displayName.contains("{level}"))
                return info.displayName.replace("{level}", toRomanNumeral(amplifier));
            else
                return info.displayName + toRomanNumeral(amplifier);
        } else {
            return (isNegative(effect) ? ChatColor.RED : ChatColor.BLUE) +
                    WordUtils.capitalizeFully(getName(effect)) + toRomanNumeral(amplifier);
        }
    }
}
