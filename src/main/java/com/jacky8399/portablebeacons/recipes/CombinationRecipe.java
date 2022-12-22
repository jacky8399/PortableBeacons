package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public record CombinationRecipe(int maxEffects,
                                boolean combineEffectsAdditively,
                                boolean enforceVanillaExpLimits,
                                boolean showErrorPrompt) implements BeaconRecipe {

    @Override
    public @Nullable ItemStack getOutput(Player player, ItemStack beacon, ItemStack input) {
        return ItemUtils.combineStack(player, beacon, input, false);
    }

    @Override
    public int getCost(ItemStack beacon, ItemStack input) {
        int cost = ExpCostCalculator.Dynamic.INSTANCE.getCost(beacon, input);
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
        var map = new LinkedHashMap<String, Object>();
        map.put("type", TYPE);
        map.put("max-effects", maxEffects);
        map.put("combine-effects-additively", combineEffectsAdditively);
        map.put("enforce-vanilla-exp-limit", enforceVanillaExpLimits);
//        map.put("display-error-prompt", showErrorPrompt);
        return map;
    }

    public static CombinationRecipe load(Map<String, Object> map) {
        int maxEffects = ((Number) map.getOrDefault("max-effects", 6)).intValue();
        boolean combineEffectsAdditively = ((Boolean) map.getOrDefault("combine-effects-additively", true));
        boolean enforceVanillaExpLimits = ((Boolean) map.getOrDefault("enforce-vanilla-exp-limit", true));
//        boolean showErrorPrompt = ((Boolean) map.getOrDefault("show-error-prompt", false));
        return new CombinationRecipe(maxEffects, combineEffectsAdditively, enforceVanillaExpLimits, false);
    }
}
