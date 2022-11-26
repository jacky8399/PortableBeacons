package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.Config;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class PotionEffectUtils {
    public static final Comparator<PotionEffectType> POTION_COMPARATOR = Comparator
            .comparing(PotionEffectUtils::isNegative)
            .thenComparing(PotionEffectUtils::getName);

    /**
     * A bi-map of Bukkit effect names to vanilla names
     */
    private static final Map<String, String> VANILLA_EFFECT_NAMES = Map.of(
            "slow", "slowness",
            "fast_digging", "haste",
            "slow_digging", "mining_fatigue",
            "increase_damage", "strength",
            "heal", "instant_health",
            "harm", "instant_damage",
            "jump", "jump_boost",
            "confusion", "nausea",
            "damage_resistance", "resistance"
    );

    public static Optional<PotionEffectType> parsePotion(@NotNull String input, boolean allowBukkitNames) {
        if (allowBukkitNames) {
            // convert Bukkit names to vanilla namespaced name
            input = input.toLowerCase(Locale.ENGLISH);
            input = VANILLA_EFFECT_NAMES.getOrDefault(input, input);
            if (input == null)
                return Optional.empty();
        }
        return Optional.ofNullable(PotionEffectType.getByKey(NamespacedKey.fromString(input)));
    }

    @NotNull
    public static Optional<PotionEffectType> parsePotion(String input) {
        return parsePotion(input, false);
    }

    private static final Set<String> VALID_POTION_NAMES = Arrays.stream(PotionEffectType.values())
            .map(PotionEffectUtils::getName)
            .collect(Collectors.toUnmodifiableSet());
    public static Set<String> getValidPotionNames() {
        return VALID_POTION_NAMES;
    }

    private static final Set<PotionEffectType> NEGATIVE_EFFECTS =
            Set.of(PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.WEAKNESS, PotionEffectType.HARM,
                    PotionEffectType.CONFUSION, PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.POISON,
                    PotionEffectType.WITHER, PotionEffectType.LEVITATION,
                    // neutral according to minecraft wiki
                    PotionEffectType.GLOWING, PotionEffectType.BAD_OMEN, PotionEffectType.UNLUCK);
    public static boolean isNegative(PotionEffectType potion) {
        return NEGATIVE_EFFECTS.contains(potion);
    }

    public static int getRequiredTier(PotionEffect potion) {
        if (potion == null) {
            return -1;
        } if (potion.getAmplifier() == 1) { // Secondary Power
            return 4;
        }
        PotionEffectType type = potion.getType();
        if (type.equals(PotionEffectType.SPEED) || type.equals(PotionEffectType.FAST_DIGGING)) {
            return 1;
        } else if (type.equals(PotionEffectType.DAMAGE_RESISTANCE) || type.equals(PotionEffectType.JUMP)) {
            return 2;
        } else if (type.equals(PotionEffectType.INCREASE_DAMAGE)) {
            return 3;
        } else if (type.equals(PotionEffectType.REGENERATION)) {
            return 4;
        }
        return -1;
    }

    @NotNull
    public static String getName(@NotNull PotionEffectType potion) {
        var key = potion.getKey();
        if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
            return key.getKey();
        }
        return key.toString();
    }

    @NotNull
    public static String toRomanNumeral(int i) {
        return switch (i) {
            case 1 -> "";
            case 2 -> " II";
            case 3 -> " III";
            case 4 -> " IV";
            case 5 -> " V";
            case 6 -> " VI";
            case 7 -> " VII";
            case 8 -> " VIII";
            case 9 -> " IX";
            case 10 -> " X";
            default -> " " + i;
        };
    }

    @NotNull
    public static String getDisplayName(PotionEffectType effect, int level) {
        Config.PotionEffectInfo info = Config.effects.get(effect);
        if (info != null && info.displayName != null) {
            return ItemUtils.replacePlaceholders(null, info.displayName, level);
        } else {
            String levelString = toRomanNumeral(level);

            NamespacedKey key = effect.getKey();
            StringBuilder name = new StringBuilder(key.getKey().length() + levelString.length() + 3);
            name.append(key.getKey());

            // capitalize string
            name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
            for (int i = 1; i < name.length() - 1; i++) {
                char chr = name.charAt(i);
                if (chr == ' ')
                    name.setCharAt(i + 1, Character.toUpperCase(name.charAt(i + 1)));
            }
            name.insert(0, isNegative(effect) ? ChatColor.RED : ChatColor.BLUE).append(' ').append(levelString);
            return name.toString();
        }
    }
}
