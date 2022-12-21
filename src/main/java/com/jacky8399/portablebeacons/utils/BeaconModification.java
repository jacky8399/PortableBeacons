package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;

public record BeaconModification(Type type, BeaconEffects virtualEffects, boolean bypassRestrictions)
        implements Consumer<BeaconEffects> {
    public enum Type {
        ADD(Integer::sum),
        SET((left, right) -> right),
        SUBTRACT((left, right) -> left - right);

        public final IntBinaryOperator merger;
        Type(IntBinaryOperator merger) {
            this.merger = merger;
        }

        Integer mapMerge(Integer oldValue, Integer newValue) {
            int value = merger.applyAsInt(oldValue, newValue);
            return value <= 0 ? null : value;
        }

        public static Type parseType(String string) {
            return switch (string) {
                case "add" -> ADD;
                case "set" -> SET;
                case "subtract", "remove" -> SUBTRACT;
                default -> throw new IllegalArgumentException(string);
            };
        }
    }
    @Override
    public void accept(BeaconEffects effects) {
        var map = new TreeMap<>(effects.getEffects());
        virtualEffects.getEffects().forEach((potion, level) -> map.merge(potion, level, type::mapMerge));
        effects.setEffects(map);
        if (virtualEffects.expReductionLevel != -1) {
            int newLevel = type.merger.applyAsInt(effects.expReductionLevel, virtualEffects.expReductionLevel);
            effects.expReductionLevel = Math.max(newLevel, 0);
        }
        if (virtualEffects.soulboundLevel != -1) {
            int newLevel = type.merger.applyAsInt(effects.soulboundLevel, virtualEffects.soulboundLevel);
            effects.soulboundLevel = Math.max(newLevel, 0);
        }
    }

    private static final String BYPASS_RESTRICTIONS_KEY = "__special_bypass-restrictions";
    public Map<String, Object> save() {
        var effectsMap = virtualEffects.save();
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
