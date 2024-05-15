package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.BeaconCondition;
import com.jacky8399.portablebeacons.utils.BeaconModification;
import com.jacky8399.portablebeacons.utils.InventoryTypeUtils;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record CombinationRecipe(NamespacedKey id,
                                @NotNull InventoryType type,
                                @Nullable ItemStack template,
                                @Nullable BeaconCondition beaconCondition,
                                @Nullable BeaconCondition sacrificeCondition,
                                @NotNull List<BeaconModification> modifications,
                                @Nullable BeaconCondition resultCondition,
                                int maxEffects,
                                boolean combineEffectsAdditively,
                                @NotNull ExpCostCalculator expCost) implements BeaconRecipe {

    public CombinationRecipe {
        modifications = List.copyOf(modifications);
    }

    private BeaconEffects doCombine(BeaconEffects effects, BeaconEffects otherEffects) {
        effects = new BeaconEffects(effects);
        var potions = new HashMap<>(effects.getEffects());
        for (var entry : otherEffects.getEffects().entrySet()) {
            var potionType = entry.getKey();
            int potionLevel = entry.getValue();
            int maxPotionLevel = Config.getInfo(potionType).getMaxAmplifier();
            potions.merge(potionType, potionLevel, (left, right) -> anvilAlgorithm(left, right, maxPotionLevel));
        }

        effects.expReductionLevel = anvilAlgorithm(effects.expReductionLevel, otherEffects.expReductionLevel, Config.enchExpReductionMaxLevel);
        effects.soulboundLevel = anvilAlgorithm(effects.soulboundLevel, otherEffects.soulboundLevel, Config.enchSoulboundMaxLevel);
        effects.beaconatorLevel = anvilAlgorithm(effects.beaconatorLevel, otherEffects.soulboundLevel, Config.enchBeaconatorLevels.size());

        for (var modification : modifications) {
            modification.modify(effects);
        }

        effects.setEffects(potions);
        return effects;
    }

    @Override
    public RecipeOutput getOutput(Player player, InventoryType type, Inventory inventory) {
        ItemStack[] items = inventory.getContents();
        ItemStack beacon = items[InventoryTypeUtils.getBeaconSlot(type)];
        ItemStack sacrifice = items[InventoryTypeUtils.getSacrificeSlot(type)];
        if (sacrifice.getAmount() != 1)
            return null;
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        BeaconEffects otherEffects = ItemUtils.getEffects(sacrifice);

        BeaconEffects newEffects = doCombine(effects, otherEffects);
        items[InventoryTypeUtils.getSacrificeSlot(type)] = null;
        if (template != null) {
            var templateStack = items[InventoryTypeUtils.getTemplateSlot(type)].clone();
            templateStack.setAmount(templateStack.getAmount() - template.getAmount());
            items[InventoryTypeUtils.getTemplateSlot(type)] = templateStack;
        }
        return new RecipeOutput(ItemUtils.createItemCopyItemData(player, newEffects, beacon), items);
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
    public boolean isApplicableTo(InventoryType type, Inventory inventory) {
        if (type != this.type)
            return false;
        ItemStack beacon = inventory.getItem(InventoryTypeUtils.getBeaconSlot(type));
        BeaconEffects beaconEffects = ItemUtils.getEffects(beacon);
        if (beaconEffects == null || beacon.getAmount() != 1)
            return false;
        if (beaconCondition != null && !beaconCondition.test(beaconEffects))
            return false;
        ItemStack sacrifice = inventory.getItem(InventoryTypeUtils.getSacrificeSlot(type));
        BeaconEffects sacrificeEffects = ItemUtils.getEffects(sacrifice);
        if (sacrificeEffects == null || sacrifice.getAmount() != 1)
            return false;
        if (sacrificeCondition != null && !sacrificeCondition.test(sacrificeEffects))
            return false;
        if (type == InventoryType.SMITHING && template != null &&
                !template.isSimilar(inventory.getItem(InventoryTypeUtils.getTemplateSlot(type))))
            return false;
        BeaconEffects result = doCombine(beaconEffects, sacrificeEffects);
        if (result.getEffects().size() > maxEffects)
            return false;
        return resultCondition == null || resultCondition.test(result);
    }

    public static final String LEGACY_TYPE = "__special_combination";
    public static final Set<String> TYPES = Set.of("anvil-combination", "smithing-combination");
    @Override
    public Map<String, Object> save() {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type == InventoryType.SMITHING ? "smithing-combination" : "anvil-combination");
        map.put("max-effects", maxEffects);
        map.put("combine-effects-additively", combineEffectsAdditively);
        map.put("exp-cost", expCost.serialize());
        if (resultCondition != null)
            map.putAll(resultCondition.save("result"));
        if (beaconCondition != null && beaconCondition == sacrificeCondition) {
            map.putAll(beaconCondition.save("inputs"));
        } else {
            if (beaconCondition != null)
                map.putAll(beaconCondition.save("beacon"));
            if (sacrificeCondition != null)
                map.putAll(sacrificeCondition.save("sacrifice"));
        }

        if (type == InventoryType.SMITHING && template != null)
            map.put("template", template);
        return map;
    }

    public static CombinationRecipe load(String id, Map<String, Object> map) {
        String type = map.get("type").toString();
        int maxEffects = ((Number) map.getOrDefault("max-effects", 6)).intValue();
        boolean combineEffectsAdditively = ((Boolean) map.getOrDefault("combine-effects-additively", true));
        ExpCostCalculator expCost;
        if (!map.containsKey("exp-cost")) {
            boolean enforceVanillaExpLimits = ((Boolean) map.getOrDefault("enforce-vanilla-exp-limit", true));
            expCost = enforceVanillaExpLimits ?
                    ExpCostCalculator.Dynamic.VANILLA :
                    ExpCostCalculator.DynamicUnrestricted.INSTANCE;
        } else {
            expCost = ExpCostCalculator.deserialize(map.get("exp-cost"));
        }
        NamespacedKey key = Objects.requireNonNull(NamespacedKey.fromString(id, PortableBeacons.INSTANCE), "Invalid key " + id);

        BeaconCondition beaconCondition = com.jacky8399.portablebeacons.utils.BeaconCondition.load(map, "beacon");
        BeaconCondition sacrificeCondition = com.jacky8399.portablebeacons.utils.BeaconCondition.load(map, "sacrifice");

        var modificationsMap = (Map<String, Map<String, Object>>) map.get("modifications");
        List<BeaconModification> modifications = List.of();
        if (modificationsMap != null && !modificationsMap.isEmpty())
            modifications = modificationsMap.entrySet().stream()
                    .map(entry -> BeaconModification.load(entry.getKey(), entry.getValue()))
                    .toList();
        BeaconCondition resultCondition = com.jacky8399.portablebeacons.utils.BeaconCondition.load(map, "result");

        if (sacrificeCondition == null && beaconCondition == null) {
            sacrificeCondition = beaconCondition = com.jacky8399.portablebeacons.utils.BeaconCondition.load(map, "inputs");
        }

        Object template = map.get("template");
        ItemStack templateStack = template != null ? SimpleRecipe.loadStack(template) : null;

        return new CombinationRecipe(key,
                // also handles __special_combination legacy value
                type.equals("smithing-combination") ? InventoryType.SMITHING : InventoryType.ANVIL,
                templateStack, beaconCondition, sacrificeCondition,
                modifications, resultCondition,
                maxEffects, combineEffectsAdditively, expCost);
    }
}
