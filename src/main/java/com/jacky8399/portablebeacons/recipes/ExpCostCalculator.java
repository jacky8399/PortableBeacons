package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.inventory.ItemStack;

public sealed interface ExpCostCalculator {
    int getCost(ItemStack left, ItemStack right);

    static ExpCostCalculator valueOf(Object object) {
        if (object instanceof Number number && number.intValue() >= 0) {
            return new Fixed(number.intValue());
        } else if ("dynamic".equals(object)) {
            return Dynamic.INSTANCE;
        } else if ("dynamic-unrestricted".equals(object)) {
            return DynamicUnrestricted.INSTANCE;
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

    default Object save() {
        if (this instanceof Fixed fixed) {
            return fixed.level;
        } else if (this instanceof Dynamic) {
            return "dynamic";
        } else if (this instanceof DynamicUnrestricted) {
            return "dynamic-unrestricted";
        } else {
            throw new Error();
        }
    }

    record Fixed(int level) implements ExpCostCalculator {
        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return level;
        }
    }

    final class DynamicUnrestricted implements ExpCostCalculator {
        public static final DynamicUnrestricted INSTANCE = new DynamicUnrestricted();

        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return ItemUtils.calculateCombinationCost(left, right);
        }

    }

    final class Dynamic implements ExpCostCalculator {
        private static final DynamicUnrestricted unrestricted = DynamicUnrestricted.INSTANCE;
        public static final Dynamic INSTANCE = new Dynamic();
        @Override
        public int getCost(ItemStack left, ItemStack right) {
            int cost = unrestricted.getCost(left, right);
            return cost > 39 ? -1 : cost; // reject if >39
        }
    }
}
