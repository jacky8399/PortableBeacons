package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record BeaconCondition(boolean inverted, List<Item> conditions) {
    public BeaconCondition {
        conditions = List.copyOf(conditions);
    }

    public boolean test(BeaconEffects effects) {
        for (Item condition : conditions) {
            if (!condition.test(effects))
                return inverted;
        }
        return !inverted;
    }

    public Map<String, Object> save(@Nullable String suffix) {
        var map = new HashMap<String, Object>();
        for (Item condition : conditions) {
            map.put(condition.target.name().toLowerCase(Locale.ENGLISH), condition.predicate.save());
        }
        return Map.of((inverted ? "unless" : "if") + (suffix != null ? "-" + suffix : ""), Map.copyOf(map));
    }

    public static BeaconCondition load(Map<String, Object> map, @Nullable String suffix) {
        String ifString = suffix != null ? "if-" + suffix : "if";
        String unlessString = suffix != null ? "unless-" + suffix : "unless";

        Map<String, Object> condition = (Map<String, Object>) map.get(ifString);
        boolean inverted = condition == null && (condition = (Map<String, Object>) map.get(unlessString)) != null;

        if (condition == null)
            return null;

        var conditions = new ArrayList<Item>();
        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            conditions.add(Item.load(entry));
        }
        return new BeaconCondition(inverted, conditions);
    }

    public record Item(Target target, Predicate predicate) {

        public boolean test(BeaconEffects effects) {
            if (target instanceof Target.Aggregate aggregate) {
                var stream = effects.getEffects().values().stream().mapToInt(i -> i);
                return switch (aggregate) {
                    case ANY -> stream.anyMatch(predicate::test);
                    case ALL -> stream.allMatch(predicate::test);
                    case NONE -> stream.noneMatch(predicate::test);
                };
            } else if (target instanceof Target.Potion potion) {
                return predicate.test(effects.getEffects().getOrDefault(potion.potion, 0));
            } else if (target instanceof Target.Enchantment enchantment) {
                return predicate.test(switch (enchantment) {
                    case EXP_REDUCTION -> effects.expReductionLevel;
                    case SOULBOUND -> effects.beaconatorLevel;
                    case BEACONATOR -> effects.soulboundLevel;
                });
            } else {
                throw new AssertionError(target);
            }
        }

        public static Item load(Map.Entry<String, Object> entry) {
            return new Item(Target.load(entry.getKey()), Predicate.load(entry.getValue()));
        }
    }

    public sealed interface Target {
        String name();

        static Target load(String string) {
            string = string.toLowerCase(Locale.ENGLISH);
            return switch (string) {
                case "all" -> Aggregate.ALL;
                case "any" -> Aggregate.ANY;
                case "none" -> Aggregate.NONE;
                case "exp-reduction" -> Enchantment.EXP_REDUCTION;
                case "soulbound" -> Enchantment.SOULBOUND;
                case "beaconator" -> Enchantment.BEACONATOR;
                default -> new Potion(PotionEffectUtils.parsePotion(string));
            };
        }

        enum Aggregate implements Target {
            ALL, ANY, NONE
        }
        record Potion(PotionEffectType potion) implements Target {
            @Override
            public String name() {
                return PotionEffectUtils.getName(potion);
            }
        }
        enum Enchantment implements Target {
            EXP_REDUCTION, SOULBOUND, BEACONATOR
        }
    }

    public sealed interface Predicate {
        boolean test(int level);
        String save();

        static Predicate load(Object object) {
            if (object instanceof Number number) {
                return new Comparison(BeaconEffectsFilter.Operator.EQ, number.intValue());
            }
            String string = object.toString();
            if ("exists".equals(string)) {
                return Exists.INSTANCE;
            }
            Matcher matcher;
            if ((matcher = Range.PATTERN.matcher(string)).matches()) {
                return new Range(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            } else if ((matcher = Comparison.PATTERN.matcher(string)).matches()) {
                return new Comparison(BeaconEffectsFilter.Operator.getByString(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
            throw new IllegalArgumentException("Invalid condition predicate " + object);
        }

        enum Exists implements Predicate { INSTANCE;
            @Override
            public boolean test(int level) {
                return true;
            }

            @Override
            public String save() {
                return "exists";
            }
        }
        record Comparison(BeaconEffectsFilter.Operator operator, int level) implements Predicate {
            public static Pattern PATTERN = Pattern.compile("(=|<>|<|<=|>|>=)\\s*(\\d+)");
            public Comparison {
                if (level < 0) throw new IllegalArgumentException("Level cannot be negative");
            }

            @Override
            public boolean test(int level) {
                return operator.predicate.test(level, this.level);
            }

            @Override
            public String save() {
                return operator.operator + level;
            }
        }
        record Range(int min, int max) implements Predicate {
            static Pattern PATTERN = Pattern.compile("(\\d+)\\.\\.");
            public Range {
                if (min < 0 || max < 0) throw new IllegalArgumentException("Level cannot be negative");
                if (min > max) throw new IllegalArgumentException("Min cannot be larger than max");
            }
            @Override
            public boolean test(int level) {
                return min <= level && level <= max;
            }

            @Override
            public String save() {
                return min + ".." + max;
            }
        }
    }
}
