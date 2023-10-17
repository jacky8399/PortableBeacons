package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.Config;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
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

    @Nullable
    public static PotionEffectType parsePotion(@NotNull String input, boolean allowBukkitNames) {
        if (allowBukkitNames) {
            // convert Bukkit names to vanilla namespaced name
            input = input.toLowerCase(Locale.ENGLISH);
            input = VANILLA_EFFECT_NAMES.getOrDefault(input, input);
            if (input == null)
                return null;
        }
        return PotionEffectType.getByKey(NamespacedKey.fromString(input));
    }

    @Nullable
    public static PotionEffectType parsePotion(String input) {
        return parsePotion(input, false);
    }

    @Nullable
    public static PotionEffectType parsePotion(NamespacedKey key) {
        if (NamespacedKey.BUKKIT.equals(key.getNamespace())) {
            return PotionEffectType.getByName(key.getKey());
        }
        return PotionEffectType.getByKey(key);
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
                    PotionEffectType.WITHER, PotionEffectType.LEVITATION, PotionEffectType.UNLUCK,
                    // neutral according to minecraft wiki
                    PotionEffectType.GLOWING, PotionEffectType.BAD_OMEN);
    // 1.19
    private static final Set<String> NEGATIVE_EFFECT_IDS = Set.of("darkness");

    public static boolean isNegative(PotionEffectType potion) {
        return NEGATIVE_EFFECTS.contains(potion) || NEGATIVE_EFFECT_IDS.contains(potion.getKey().getKey());
    }

    public static int getRequiredTier(PotionEffect potion) {
        if (potion == null) {
            return -1;
        }
        if (potion.getAmplifier() == 1) { // Secondary Power
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
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> "" + i;
        };
    }

    @NotNull
    public static BaseComponent getDisplayName(PotionEffectType effect, int level) {
        Config.PotionEffectInfo info = Config.getInfo(effect);

        String translationKey = effect.getKey().toString().replace(':', '.');
        TranslatableComponent translatable = new TranslatableComponent("potion.withAmplifier");
        BaseComponent potion =
                info.nameOverride() == null ?
                        new TranslatableComponent("effect." + translationKey) :
                        new TextComponent(TextComponent.fromLegacyText(info.nameOverride()));
        Config.EffectFormatter formatter = info.formatOverride() != null ? info.formatOverride() : Config.EffectFormatter.DEFAULT;
        TextComponent amplifier = new TextComponent(formatter.format(level));
        translatable.setColor(ChatColor.of(new Color(effect.getColor().asRGB())));
        translatable.setWith(List.of(potion, amplifier));
        return translatable;
    }

    private static final Map<PotionEffectType, PotionType> POTION_TYPES;
    static {
        // I can't believe turtle master potions would do this to me
        var uniqueEffectTypes = new HashSet<PotionEffectType>();
        POTION_TYPES = Arrays.stream(PotionType.values())
                .filter(type -> type.getEffectType() != null && uniqueEffectTypes.add(type.getEffectType()))
                .collect(Collectors.toUnmodifiableMap(PotionType::getEffectType, type -> type));
    }
    public static PotionType getPotionType(PotionEffectType type) {
        return POTION_TYPES.getOrDefault(type, PotionType.WATER);
    }
}
