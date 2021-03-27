package com.jacky8399.portablebeacons.utils;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.jacky8399.portablebeacons.Config;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PotionEffectUtils {
    public static final Comparator<PotionEffectType> POTION_COMPARATOR = Comparator
            .comparing(PotionEffectUtils::isNegative)
            .thenComparing(PotionEffectUtils::getName);

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

    @NotNull
    public static Optional<PotionEffectType> parsePotion(String input) {
        return parsePotion(input, false);
    }

    private static final ImmutableSet<String> VALID_POTION_NAMES = Arrays.stream(PotionEffectType.values()).map(PotionEffectUtils::getName).collect(ImmutableSet.toImmutableSet());
    public static ImmutableSet<String> getValidPotionNames() {
        return VALID_POTION_NAMES;
    }

    private static final ImmutableSet<PotionEffectType> NEGATIVE_EFFECTS =
            ImmutableSet.of(PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.WEAKNESS, PotionEffectType.HARM,
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
    public static String getDisplayName(PotionEffectType effect, int level) {
        Config.PotionEffectInfo info = Config.effects.get(effect);
        if (info != null && info.displayName != null) {
            return replacePlaceholders(null, info.displayName, level);
        } else {
            return (isNegative(effect) ? ChatColor.RED : ChatColor.BLUE) +
                    WordUtils.capitalizeFully(getName(effect).replace('_', ' ')) + toRomanNumeral(level);
        }
    }

    // also capture preceding space to prevent awkward problems with level 1
    private static Pattern LEVEL_PATTERN = Pattern.compile("\\s?\\{level}");
    private static Pattern LEVEL_NUMBER_PATTERN = Pattern.compile("\\{level\\|number}");
    private static Pattern SOULBOUND_PATTERN = Pattern.compile("\\{soulbound-player(?:\\|(.+?))?}");

    public static String replacePlaceholders(@Nullable Player player, String input, int level, @Nullable String soulboundOwner) {
        if (input.indexOf('{') == -1) {
            return input + toRomanNumeral(level);
        }
        input = LEVEL_PATTERN.matcher(input).replaceAll(toRomanNumeral(level));
        input = LEVEL_NUMBER_PATTERN.matcher(input).replaceAll(Integer.toString(level));
        Matcher matcher = SOULBOUND_PATTERN.matcher(input);
        if (matcher.find()) {
            // thanks Java
            StringBuffer buffer = new StringBuffer();
            do {
                String replacement = soulboundOwner != null ? soulboundOwner : matcher.group(1) != null ? matcher.group(1) : "no-one";
                matcher.appendReplacement(buffer, replacement);
            } while (matcher.find());
            matcher.appendTail(buffer);
            input = buffer.toString();
        }
        return input;
    }

    public static String replacePlaceholders(Player player, String input, int level) {
        return replacePlaceholders(player, input, level, null);
    }
}
