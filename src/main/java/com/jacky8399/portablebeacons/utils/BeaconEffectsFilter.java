package com.jacky8399.portablebeacons.utils;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

public class BeaconEffectsFilter implements BiPredicate<PotionEffectType, Integer> {
    public final PotionEffectType type;
    public final Operator operator;
    public final int constraint;

    private BeaconEffectsFilter(PotionEffectType type, Operator operator, int constraint) {
        this.type = type;
        this.operator = operator;
        this.constraint = constraint;
    }

    public static BeaconEffectsFilter fromString(String input) {
        Optional<PotionEffectType> simplePotion = PotionEffectUtils.parsePotion(input, false);
        if (simplePotion.isPresent())
            return new BeaconEffectsFilter(simplePotion.get(), Operator.ANY, -1);
        for (Operator operator : Operator.values()) {
            if (operator == Operator.ANY)
                continue;
            int idx = input.indexOf(operator.operator);
            if (idx == -1)
                continue;
            String potion = input.substring(0, idx - 1);
            String level = input.substring(idx + operator.operator.length(), input.length() - 1);
            try {
                return new BeaconEffectsFilter(
                        PotionEffectUtils.parsePotion(potion, false).orElseThrow(IllegalArgumentException::new),
                        operator, Integer.parseInt(level)
                );
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid format (potion[op]level)", e);
            }
        }
        throw new IllegalArgumentException("Invalid format " + input);
    }

    @Override
    public String toString() {
        return PotionEffectUtils.getName(type) + (operator != Operator.ANY ? operator.operator + constraint : "");
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, operator, constraint);
    }

    @Override
    public boolean test(PotionEffectType type, Integer integer) {
        return this.type == type && operator.predicate.test(integer, constraint);
    }

    public boolean contains(Map<PotionEffectType, Short> map) {
        Short val = map.get(type);
        if (val == null)
            return false;
        return operator.predicate.test(val.intValue(), constraint);
    }

    public enum Operator {
        ANY(null, (a, b) -> true), EQ("=", Integer::equals), NEQ ("<>", EQ.predicate.negate()),
        LT("<", (lvl, req) -> lvl.compareTo(req) < 0), GT(">", (lvl, req) -> lvl.compareTo(req) > 0),
        LTE("<=", GT.predicate.negate()), GTE(">=", LT.predicate.negate());

        @Nullable
        public final String operator;
        /** arguments: level, constraint */
        public final BiPredicate<Integer, Integer> predicate;
        Operator(@Nullable String operator, BiPredicate<Integer, Integer> predicate) {
            this.operator = operator;
            this.predicate = predicate;
        }
    }
}
