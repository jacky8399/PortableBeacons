package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.utils.BeaconModification;
import com.jacky8399.portablebeacons.utils.BeaconPyramid;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record SimpleRecipe(@NotNull InventoryType type,
                           @NotNull ItemStack input,
                           @NotNull List<BeaconModification> modifications,
                           @NotNull ExpCostCalculator expCost,
                           @NotNull EnumSet<SpecialOps> specialOperations) implements BeaconRecipe {

    public SimpleRecipe {
        if (type != InventoryType.ANVIL)
            throw new IllegalArgumentException("Invalid inventory type " + type);
    }

    @Override
    public ItemStack getOutput(Player player, ItemStack beacon, ItemStack input) {
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        for (var modification : modifications) {
            modification.accept(effects);
        }

        if (specialOperations.contains(SpecialOps.SET_SOULBOUND_OWNER))
            effects.soulboundOwner = player.getUniqueId();

        var stack = ItemUtils.createStackCopyItemData(effects, beacon);
        // copy pyramid
        BeaconPyramid pyramid;
        if ((pyramid = ItemUtils.getPyramid(beacon)) != null)
            ItemUtils.setPyramid(stack, pyramid);
        return stack;
    }

    @Override
    public int getCost(ItemStack beacon, ItemStack input) {
        return expCost.getCost(beacon, input);
    }

    @Override
    public boolean isApplicableTo(ItemStack beacon, ItemStack input) {
        return input.isSimilar(this.input);
    }

    @Override
    public Map<String, Object> save() {
        var map = new HashMap<String, Object>();
        map.put("type", type.name().toLowerCase(Locale.ENGLISH));
        map.put("input", input);
        var modMap = new HashMap<String, Object>();
        for (var mod : modifications) {
            modMap.putAll(mod.save());
        }
        map.put("modifications", modMap);
        map.put("exp-cost", expCost.save());
        for (var specialValue : specialOperations) {
            map.put(specialValue.key, true);
        }
        return map;
    }

    public static SimpleRecipe load(Map<String, Object> map) throws IllegalArgumentException {
        InventoryType type = InventoryType.valueOf(map.get("type").toString().toUpperCase(Locale.ENGLISH));
        ItemStack stack;
        Object inputObj = map.get("input");
        if (inputObj instanceof String inputStr)
            stack = Bukkit.getItemFactory().createItemStack(inputStr);
        else
            stack = ItemStack.deserialize((Map<String, Object>) inputObj);

        var modifications = ((Map<String, Map<String, Object>>) map.get("modifications")).entrySet()
                .stream()
                .map(entry -> BeaconModification.load(entry.getKey(), entry.getValue()))
                .toList();
        var expCost = ExpCostCalculator.valueOf(map.get("exp-cost"));
        var specialOps = EnumSet.noneOf(SpecialOps.class);
        for (var special : SpecialOps.values()) {
            if (map.get(special.key) instanceof Boolean bool && bool)
                specialOps.add(special);
        }

        return new SimpleRecipe(type, stack, modifications, expCost, specialOps);
    }

    public enum SpecialOps {
        SET_SOULBOUND_OWNER("__special_set-soulbound-owner");
        public final String key;
        SpecialOps(String key) {
            this.key = key;
        }
    }

}
