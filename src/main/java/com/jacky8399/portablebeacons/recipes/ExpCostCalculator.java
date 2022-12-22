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
        } else {
            throw new Error();
        }
    }

    record Fixed(int level) implements com.jacky8399.portablebeacons.recipes.ExpCostCalculator {
        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return level;
        }
    }

    final class Dynamic implements com.jacky8399.portablebeacons.recipes.ExpCostCalculator {
        public static final Dynamic INSTANCE = new Dynamic();

        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return ItemUtils.calculateCombinationCost(left, right);
        }
    }
}
