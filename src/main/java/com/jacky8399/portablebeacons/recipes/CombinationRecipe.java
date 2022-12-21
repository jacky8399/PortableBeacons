package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public record CombinationRecipe(int maxEffects,
                                boolean combineAdditively,
                                boolean enforceVanillaExpLimits,
                                boolean showErrorPrompt) implements BeaconRecipe {


    @Override
    public @Nullable ItemStack getOutput(Player player, ItemStack beacon, ItemStack input) {
        return ItemUtils.combineStack(player, beacon, input, false);
    }

    @Override
    public int getCost(ItemStack beacon, ItemStack input) {
        int cost = ExpCostCalculator.Default.INSTANCE.getCost(beacon, input);
        if (enforceVanillaExpLimits && cost > 39) {
            return -1; // disallow
        }
        return cost;
    }

    @Override
    public boolean isApplicableTo(ItemStack beacon, ItemStack input) {
        return ItemUtils.isPortableBeacon(input);
    }

    public static final String TYPE = "__special_combination";
    @Override
    public Map<String, Object> save() {
        var map = new HashMap<String, Object>();
        map.put("max-effects", maxEffects);
        map.put("combine-additively", combineAdditively);
        map.put("enforce-vanilla-exp-limit", enforceVanillaExpLimits);
        map.put("show-error-prompt", showErrorPrompt);
        return map;
    }

    public static CombinationRecipe load(Map<String, Object> map) {
        int maxEffects = ((Number) map.getOrDefault("max-effects", 6)).intValue();
        boolean combineAdditively = ((Boolean) map.getOrDefault("combine-additively", true));
        boolean enforceVanillaExpLimits = ((Boolean) map.getOrDefault("enforce-vanilla-exp-limit", true));
        boolean showErrorPrompt = ((Boolean) map.getOrDefault("show-error-prompt", false));
        return new CombinationRecipe(maxEffects, combineAdditively, enforceVanillaExpLimits, showErrorPrompt);
    }
}
