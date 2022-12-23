package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

public record CombinationRecipe(String id,
                                int maxEffects,
                                boolean combineEffectsAdditively,
                                ExpCostCalculator expCost,
                                boolean showErrorPrompt) implements BeaconRecipe {

    @Override
    public int getCost(ItemStack beacon, ItemStack input) {
        return expCost.getCost(beacon, input);
    }

    @Override
    public @Nullable RecipeOutput getOutput(Player player, ItemStack beacon, ItemStack input) {
        if (input.getAmount() != 1)
            return null;
        BeaconEffects e2 = ItemUtils.getEffects(input);
        if (e2 == null)
            return null;
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        Map<PotionEffectType, Integer> potions = new HashMap<>(effects.getEffects());
        for (var entry : e2.getEffects().entrySet()) {
            var potionType = entry.getKey();
            int potionLevel = entry.getValue();
            int maxPotionLevel = Config.getInfo(potionType).getMaxAmplifier();
            BinaryOperator<Integer> algorithm = (left, right) -> anvilAlgorithm(left, right, maxPotionLevel);
            potions.merge(potionType, potionLevel, algorithm);
        }

        // check max effects count / overpowered effects
        if (potions.size() > maxEffects) {
            return null;
        }
        effects.expReductionLevel = anvilAlgorithm(effects.expReductionLevel, e2.expReductionLevel, Config.enchExpReductionMaxLevel);
        effects.soulboundLevel = anvilAlgorithm(effects.soulboundLevel, e2.soulboundLevel, Config.enchSoulboundMaxLevel);

        effects.setEffects(potions);
        return RecipeOutput.sacrificeInput(ItemUtils.createStackCopyItemData(effects, beacon));
    }

    private int anvilAlgorithm(int s1, int s2, int max) {
        if (combineEffectsAdditively) {
            if (s1 == s2) {
                return Math.min(s1 + 1, max);
            } else {
                return Math.min(Math.max(s1, s2), max);
            }
        } else {
            return Math.min(s1 + s2, max);
        }
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
        map.put("exp-cost", expCost.save());
//        map.put("display-error-prompt", showErrorPrompt);
        return map;
    }

    public static CombinationRecipe load(String id, Map<String, Object> map) {
        int maxEffects = ((Number) map.getOrDefault("max-effects", 6)).intValue();
        boolean combineEffectsAdditively = ((Boolean) map.getOrDefault("combine-effects-additively", true));
        ExpCostCalculator expCost;
        if (!map.containsKey("exp-cost")) {
            boolean enforceVanillaExpLimits = ((Boolean) map.getOrDefault("enforce-vanilla-exp-limit", true));
            expCost = enforceVanillaExpLimits ?
                    ExpCostCalculator.Dynamic.INSTANCE :
                    ExpCostCalculator.DynamicUnrestricted.INSTANCE;
        } else {
            expCost = ExpCostCalculator.valueOf(map.get("exp-cost"));
        }
//        boolean showErrorPrompt = ((Boolean) map.getOrDefault("show-error-prompt", false));
        return new CombinationRecipe(id, maxEffects, combineEffectsAdditively, expCost, false);
    }
}
