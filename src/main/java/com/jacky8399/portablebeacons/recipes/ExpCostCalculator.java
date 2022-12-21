package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.inventory.ItemStack;

public sealed interface ExpCostCalculator {
    int getCost(ItemStack left, ItemStack right);


    static ExpCostCalculator valueOf(Object object) {
        if (object instanceof Number number && number.intValue() >= 0) {
            return new Fixed(number.intValue());
        } else if ("default".equals(object)) {
            return Default.INSTANCE;
        } else {
            throw new IllegalArgumentException(object + " is not a valid exp-cost");
        }
    }

    default Object save() {
        if (this instanceof Fixed fixed) {
            return fixed.level;
        } else if (this instanceof Default) {
            return "default";
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

    final class Default implements com.jacky8399.portablebeacons.recipes.ExpCostCalculator {
        static final Default INSTANCE = new Default();

        @Override
        public int getCost(ItemStack left, ItemStack right) {
            return ItemUtils.calculateCombinationCost(left, right);
        }
    }
}
