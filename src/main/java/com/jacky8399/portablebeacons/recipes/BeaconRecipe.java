package com.jacky8399.portablebeacons.recipes;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public sealed interface BeaconRecipe permits CombinationRecipe, SimpleRecipe {

    record RecipeOutput(ItemStack output, ItemStack[] slots) {}

    RecipeOutput getOutput(Player player, InventoryType type, Inventory inventory);

    boolean isApplicableTo(InventoryType type, Inventory inventory);

    Map<String, Object> save();

    NamespacedKey id();

    ExpCostCalculator expCost();

    static BeaconRecipe load(String id, Map<String, Object> map) {
        String type = map.get("type").toString();
        if (CombinationRecipe.LEGACY_TYPE.equals(type) || CombinationRecipe.TYPES.contains(type)) {
            return CombinationRecipe.load(id, map);
        } else {
            return SimpleRecipe.load(id, map);
        }
    }

}


