package com.jacky8399.portablebeacons.recipes;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public sealed interface BeaconRecipe permits CombinationRecipe, SimpleRecipe {
    @Nullable
    default ItemStack getPreviewOutput(Player player, ItemStack beacon, ItemStack input) {
        return getOutput(player, beacon, input);
    }

    @Nullable
    ItemStack getOutput(Player player, ItemStack beacon, ItemStack input);

    int getCost(ItemStack beacon, ItemStack input);

    // Unused
    @Deprecated
    boolean isApplicableTo(ItemStack beacon, ItemStack input);

    Map<String, Object> save();

    String id();

    static BeaconRecipe load(String id, Map<String, Object> map) {
        if (CombinationRecipe.TYPE.equals(map.get("type"))) {
            return CombinationRecipe.load(id, map);
        } else {
            return SimpleRecipe.load(id, map);
        }
    }

}


