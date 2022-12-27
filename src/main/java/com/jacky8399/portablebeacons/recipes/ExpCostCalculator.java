package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.inventory.ItemStack;

public sealed interface ExpCostCalculator {
    int getCost(ItemStack left, ItemStack right);

    static ExpCostCalculator deserialize(Object object) {
        if (object instanceof Number number && number.intValue() >= 0) {
            return new Fixed(number.intValue());
        } else if ("dynamic-unrestricted".equals(object)) {
            return DynamicUnrestricted.INSTANCE;
        } else if (object instanceof String str && str.startsWith("dynamic")) {
            return Dynamic.deserialize(str);
        } else {
            int level;
            try {
                level = Integer.parseInt(object.toString());
                return new Fixed(level);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException(object + " is not a valid exp-cost");
            }
        }
    }

    Object serialize();

    record Fixed(int level) implements ExpCostCalculator {
        public Fixed {
            if (level < 0 || level > 1000000)
                throw new IllegalArgumentException("level must be between 0-1000000");
        }
        
        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return level;
        }

        @Override
        public Object serialize() {
            return level;
        }
    }

    final class DynamicUnrestricted implements ExpCostCalculator {
        public static final DynamicUnrestricted INSTANCE = new DynamicUnrestricted();

        private static int getCost(BeaconEffects effects) {
            int cost = 0;
            for (int level : effects.getEffects().values()) {
                if (level > 20)
                    return -1;
                cost += 1 << level;
            }
            if (effects.expReductionLevel > 20 || effects.soulboundLevel > 20)
                return -1;
            cost += 1 << effects.expReductionLevel;
            cost += 1 << effects.soulboundLevel;
            return cost;
        }

        @Override
        public int getCost(ItemStack left, ItemStack right) {
            BeaconEffects leftEffects = ItemUtils.getEffects(left);
            BeaconEffects rightEffects = ItemUtils.getEffects(right);
            if (leftEffects == null)
                return 0;
            int cost = getCost(leftEffects);
            int otherCost = rightEffects != null ? getCost(rightEffects) : 0;
            if (cost == -1 || otherCost == -1)
                return -1;
            return cost + otherCost;
        }

        @Override
        public Object serialize() {
            return "dynamic-unrestricted";
        }
    }

    record Dynamic(int maxLevel) implements ExpCostCalculator {
        private static final DynamicUnrestricted unrestricted = DynamicUnrestricted.INSTANCE;
        public static final Dynamic VANILLA = new Dynamic(39);
        @Override
        public int getCost(ItemStack left, ItemStack right) {
            int cost = unrestricted.getCost(left, right);
            return cost > maxLevel ? -1 : cost;
        }

        @Override
        public Object serialize() {
            return maxLevel == 39 ? "dynamic" : "dynamic-max" + maxLevel;
        }

        public static Dynamic deserialize(String input) {
            if ("dynamic".equals(input)) {
                return VANILLA;
            } else if (input.startsWith("dynamic-max")) {
                String substring = input.substring(11);
                return new Dynamic(Integer.parseInt(substring));
            }
            throw new IllegalArgumentException(input);
        }
    }
}
