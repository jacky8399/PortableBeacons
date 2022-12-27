package com.jacky8399.portablebeacons.recipes;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public sealed interface BeaconRecipe permits CombinationRecipe, SimpleRecipe {

    record RecipeOutput(@NotNull ItemStack output, @Nullable ItemStack right) {
        public static RecipeOutput sacrificeInput(@NotNull ItemStack output) {
            return new RecipeOutput(output, null);
        }
    }
    @Nullable
    default RecipeOutput getPreviewOutput(Player player, ItemStack beacon, ItemStack input) {
        return getOutput(player, beacon, input);
    }

    @Nullable
    RecipeOutput getOutput(Player player, ItemStack beacon, ItemStack input);

    boolean isApplicableTo(ItemStack beacon, ItemStack input);

    Map<String, Object> save();

    String id();

    ExpCostCalculator expCost();

    static BeaconRecipe load(String id, Map<String, Object> map) {
        if (CombinationRecipe.TYPE.equals(map.get("type"))) {
            return CombinationRecipe.load(id, map);
        } else {
            return SimpleRecipe.load(id, map);
        }
    }

}


