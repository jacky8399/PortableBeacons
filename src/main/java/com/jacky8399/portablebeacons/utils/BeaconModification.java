package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

public record BeaconModification(Type type, BeaconEffects virtualEffects, boolean bypassRestrictions)
        implements Predicate<BeaconEffects> {

    public BeaconModification(Type type, BeaconEffects virtualEffects) {
        this(type, virtualEffects, false);
    }

    @Override
    public boolean test(BeaconEffects effects) {
        return modify(effects.clone());
    }

    public enum Type {
        ADD(Integer::sum, false),
        SET((left, right) -> right, true),
        SUBTRACT((left, right) -> left - right, false);

        public final IntBinaryOperator merger;
        public final boolean allowVirtual;
        Type(IntBinaryOperator merger, boolean allowVirtual) {
            this.merger = merger;
            this.allowVirtual = allowVirtual;
        }

        Integer mapMerge(Integer oldValue, Integer newValue) {
            int value = merger.applyAsInt(oldValue, newValue);
            return value <= 0 ? null : value;
        }

        public static Type parseType(String string) {
            return switch (string.toLowerCase(Locale.ENGLISH)) {
                case "add" -> ADD;
                case "set" -> SET;
                case "subtract", "remove" -> SUBTRACT;
                default -> throw new IllegalArgumentException(string);
            };
        }
    }

    public boolean modify(BeaconEffects effects) {
        var map = new TreeMap<>(effects.getEffects());
        for (var entry : virtualEffects.getEffects().entrySet()) {
            PotionEffectType potion = entry.getKey();
            Integer level = entry.getValue();
            Integer newLevel = map.merge(potion, level, type::mapMerge);
            // check restrictions
            if (!bypassRestrictions && newLevel != null && newLevel > Config.getInfo(potion).getMaxAmplifier()) {
                return false;
            }
        }
        effects.setEffects(map);
        if (virtualEffects.expReductionLevel != -1) {
            int newLevel = type.merger.applyAsInt(effects.expReductionLevel, virtualEffects.expReductionLevel);
            effects.expReductionLevel = Math.max(newLevel, 0);
            // check restrictions
            if (!bypassRestrictions && effects.expReductionLevel > Config.enchExpReductionMaxLevel) {
                return false;
            }
        }
        if (virtualEffects.soulboundLevel != -1) {
            int newLevel = type.merger.applyAsInt(effects.soulboundLevel, virtualEffects.soulboundLevel);
            effects.soulboundLevel = Math.max(newLevel, 0);
            // check restrictions
            if (!bypassRestrictions && effects.soulboundLevel > Config.enchSoulboundMaxLevel) {
                return false;
            }
        }
        return true;
    }

    // subject to changes
    private static final String BYPASS_RESTRICTIONS_KEY = "__special_bypass-restrictions";
    public Map<String, Object> save() {
        var effectsMap = virtualEffects.save(true);
        if (bypassRestrictions)
            effectsMap.put(BYPASS_RESTRICTIONS_KEY, true);
        return Map.of(type.name().toLowerCase(Locale.ENGLISH), effectsMap);
    }

    public static BeaconModification load(String typeString, Map<String, Object> map) throws IllegalArgumentException {
        var type = Type.parseType(typeString);
        Map<String, Object> tempMap = new HashMap<>(map);
        boolean bypassRestrictions = tempMap.remove(BYPASS_RESTRICTIONS_KEY) instanceof Boolean bypassBool && bypassBool;
        BeaconEffects effects = BeaconEffects.load(tempMap, true);
        return new BeaconModification(type, effects, bypassRestrictions);
    }

}
