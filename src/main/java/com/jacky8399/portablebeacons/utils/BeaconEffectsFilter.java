package com.jacky8399.portablebeacons.utils;

import com.google.common.collect.ImmutableMap;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record BeaconEffectsFilter(PotionEffectType type,
                                  @Nullable Operator operator,
                                  int constraint) implements BiPredicate<PotionEffectType, Integer> {

    public static Pattern FILTER_FORMAT = Pattern.compile("^([a-z_:]+)(?:(=|<>|<|<=|>|>=)(\\d+))?$");

    public static BeaconEffectsFilter fromString(String input) {
        PotionEffectType simplePotion = PotionEffectUtils.parsePotion(input);
        if (simplePotion != null)
            return new BeaconEffectsFilter(simplePotion, null, -1);
        Matcher matcher = FILTER_FORMAT.matcher(input);
        if (matcher.matches()) {
            PotionEffectType type = PotionEffectUtils.parsePotion(matcher.group(1));
            if (type == null || matcher.groupCount() != 3) {
                throw new IllegalArgumentException("Invalid potion effect " + matcher.group(1));
            }
            Operator op = Operator.getByString(matcher.group(2));
            if (op == null) {
                throw new IllegalArgumentException("Invalid operator " + matcher.group(2));
            }
            int constraint;
            try {
                constraint = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid constraint " + matcher.group(3), e);
            }
            return new BeaconEffectsFilter(type, op, constraint);
        }
        throw new IllegalArgumentException("Invalid format " + input);
    }

    @Override
    public String toString() {
        return PotionEffectUtils.getName(type) + (operator != null ? operator.operator + constraint : "");
    }

    @Override
    public boolean test(PotionEffectType type, Integer integer) {
        return this.type.equals(type) && (operator == null || operator.predicate.test(integer, constraint));
    }

    public boolean contains(Map<PotionEffectType, Integer> map) {
        Integer val = map.get(type);
        if (val == null)
            return false;
        return operator == null || operator.predicate.test(val, constraint);
    }

    public enum Operator {
        EQ("=", (a, b) -> a == b), NEQ("<>", (a, b) -> a != b),
        LT("<", (a, b) -> a < b), GT(">", (a, b) -> a > b),
        LTE("<=", (a, b) -> a <= b), GTE(">=", (a, b) -> a >= b);

        public final String operator;
        /**
         * arguments: level, constraint
         */
        public final BiIntPredicate predicate;

        Operator(@Nullable String operator, BiIntPredicate predicate) {
            this.operator = operator;
            this.predicate = predicate;
        }

        public static final ImmutableMap<String, Operator> STRING_MAP;

        static {
            ImmutableMap.Builder<String, Operator> builder = ImmutableMap.builder();
            for (Operator op : Operator.values()) {
                builder.put(op.operator, op);
            }
            STRING_MAP = builder.build();
        }

        public static Operator getByString(String operator) {
            return STRING_MAP.get(operator);
        }
    }

    @FunctionalInterface
    public interface BiIntPredicate {
        boolean test(int a, int b);
    }
}
