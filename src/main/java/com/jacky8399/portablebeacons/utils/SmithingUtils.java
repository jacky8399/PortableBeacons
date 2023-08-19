package com.jacky8399.portablebeacons.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;

import java.util.List;

public class SmithingUtils {

    public static final boolean IS_1_20;
    static {
        IS_1_20 = InventoryType.SMITHING.getDefaultSize() == 4;
    }

    public static SmithingRecipe makeSmithingRecipe(NamespacedKey key, RecipeChoice template, RecipeChoice addition, List<BeaconModification> modifications) {
        ItemStack result = RecipeUtils.getBeaconResult(modifications);
        if (IS_1_20) {
            return new SmithingTransformRecipe(key, result, template, RecipeUtils.getBeaconPlaceholder(), addition);
        } else {
            @SuppressWarnings("deprecation")
            SmithingRecipe recipe = new SmithingRecipe(key, result, RecipeUtils.getBeaconPlaceholder(), addition);
            return recipe;
        }
    }

}
