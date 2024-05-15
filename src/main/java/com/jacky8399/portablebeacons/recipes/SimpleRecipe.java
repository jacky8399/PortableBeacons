package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record SimpleRecipe(@NotNull NamespacedKey id,
                           @NotNull InventoryType type,
                           @NotNull ItemStack input,
                           @Nullable ItemStack template,
                           @Nullable BeaconCondition beaconCondition,
                           @Nullable BeaconCondition resultCondition,
                           @NotNull List<BeaconModification> modifications,
                           @NotNull ExpCostCalculator expCost,
                           @NotNull Set<SpecialOps> specialOperations) implements BeaconRecipe {

    public SimpleRecipe {
        if (type != InventoryType.ANVIL && type != InventoryType.SMITHING)
            throw new IllegalArgumentException("Invalid inventory type " + type);
        modifications = List.copyOf(modifications);
        specialOperations = Set.copyOf(specialOperations);
    }

    public SimpleRecipe(String id,
                        @NotNull InventoryType type,
                        @NotNull ItemStack input,
                        @NotNull List<BeaconModification> modifications,
                        @NotNull ExpCostCalculator expCost,
                        @NotNull Set<SpecialOps> specialOperations) {
        this(Objects.requireNonNull(NamespacedKey.fromString(id, PortableBeacons.INSTANCE), "Invalid key " + id),
                type, input, null, null, null, modifications, expCost, specialOperations);
    }

    @Override
    public RecipeOutput getOutput(Player player, InventoryType type, Inventory inventory) {
        ItemStack[] items = inventory.getContents();
        int beaconSlot = InventoryTypeUtils.getBeaconSlot(type);
        ItemStack beacon = items[beaconSlot];
        int sacrificeSlot = InventoryTypeUtils.getSacrificeSlot(type);
        ItemStack sacrifice = items[sacrificeSlot];
        ItemStack template = type == InventoryType.SMITHING ? items[InventoryTypeUtils.getTemplateSlot(type)] : null;
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        for (var modification : modifications) {
            if (!modification.modify(effects))
                return null;
        }

        if (specialOperations.contains(SpecialOps.SET_SOULBOUND_OWNER))
            effects.soulboundOwner = player.getUniqueId();

        var stack = ItemUtils.createItemCopyItemData(player, effects, beacon);
        // copy pyramid
        BeaconPyramid pyramid;
        if ((pyramid = ItemUtils.getPyramid(beacon)) != null)
            ItemUtils.setPyramid(stack, pyramid);

        // subtract items
        int sacrificeAmt = sacrifice.getAmount() - input.getAmount();
        if (sacrificeAmt <= 0) {
            items[sacrificeSlot] = null;
        } else {
            var newInput = sacrifice.clone();
            newInput.setAmount(sacrificeAmt);
            items[sacrificeSlot] = newInput;
        }

        if (this.template != null && template != null) {
            int templateAmt = template.getAmount() - this.template.getAmount();
            var newTemplate = template.clone();
            newTemplate.setAmount(Math.max(0, templateAmt));
            items[InventoryTypeUtils.getTemplateSlot(type)] = newTemplate;
        }
        return new RecipeOutput(stack, items);
    }

    @Override
    public boolean isApplicableTo(InventoryType type, Inventory inventory) {
        if (this.type != type)
            return false;
        ItemStack beacon = inventory.getItem(InventoryTypeUtils.getBeaconSlot(type));
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        if (effects == null || (beaconCondition != null && !beaconCondition.test(effects)))
            return false;
        modifications.forEach(modification -> modification.modify(effects));
        if (resultCondition != null && !resultCondition.test(effects))
            return false;
        ItemStack sacrifice = inventory.getItem(InventoryTypeUtils.getSacrificeSlot(type));
        if (sacrifice == null)
            return false;
        // special checks for enchanted books
        if (input.getType() == Material.ENCHANTED_BOOK) {
            if (sacrifice.getType() != Material.ENCHANTED_BOOK)
                return false;
            EnchantmentStorageMeta meta = Objects.requireNonNull((EnchantmentStorageMeta) input.getItemMeta());
            EnchantmentStorageMeta other = Objects.requireNonNull((EnchantmentStorageMeta) sacrifice.getItemMeta());
            for (var entry : meta.getStoredEnchants().entrySet()) {
                if (other.getStoredEnchantLevel(entry.getKey()) < entry.getValue())
                    return false;
            }
        }
        if (!(input.isSimilar(sacrifice) && sacrifice.getAmount() >= input.getAmount()))
            return false;

        ItemStack template = type == InventoryType.SMITHING ? inventory.getItem(InventoryTypeUtils.getTemplateSlot(type)) : null;
        return this.template == null || this.template.isSimilar(template);
    }

    @Override
    public Map<String, Object> save() {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", type.name().toLowerCase(Locale.ENGLISH));
        var inputClone = input.clone();
        inputClone.setAmount(1);
        map.put("input", inputClone); // store amount ourselves
        map.put("input-amount", input.getAmount());

        if (type == InventoryType.SMITHING && template != null)
            map.put("template", template);

        if (beaconCondition != null)
            map.putAll(beaconCondition.save("beacon"));
        if (resultCondition != null)
            map.putAll(resultCondition.save("result"));

        var modMap = new HashMap<String, Object>();
        for (var mod : modifications) {
            modMap.putAll(mod.save());
        }
        map.put("modifications", modMap);
        map.put("exp-cost", expCost.serialize());
        for (var specialValue : specialOperations) {
            map.put(specialValue.key, true);
        }
        return map;
    }

    static ItemStack loadStack(@NotNull Object o) {
        if (o instanceof String inputStr)
            return Bukkit.getItemFactory().createItemStack(inputStr);
        else if (o instanceof ItemStack stack1) // apparently it can do that??
            return stack1;
        else
            return ItemStack.deserialize((Map<String, Object>) o);
    }

    public static SimpleRecipe load(String id, Map<String, Object> map) throws IllegalArgumentException {
        NamespacedKey key = NamespacedKey.fromString(id, PortableBeacons.INSTANCE);
        if (key == null) {
            key = new NamespacedKey(PortableBeacons.INSTANCE, UUID.randomUUID().toString().replace("-", ""));
            PortableBeacons.INSTANCE.logger.warning(id + " is not a valid key! The recipe will be loaded as " + key +". To remove this warning, replace the key with a valid one.");
        }

        InventoryType type = InventoryType.valueOf(
                Objects.requireNonNull((String) map.get("type"), "type cannot be null").toUpperCase(Locale.ENGLISH));

        Object inputObj = Objects.requireNonNull(map.get("input"), "input cannot be null");
        ItemStack stack = loadStack(inputObj);
        int amount = (Integer) map.getOrDefault("input-amount", 1);
        stack.setAmount(amount);

        Object templateObj = map.get("template");
        ItemStack template = templateObj != null ? loadStack(templateObj) : null;

        BeaconCondition beaconCondition = BeaconCondition.load(map, "beacon");
        BeaconCondition resultCondition = BeaconCondition.load(map, "result");

        var modificationsMap = (Map<String, Map<String, Object>>) map.get("modifications");
        if (modificationsMap == null || modificationsMap.isEmpty()) {
            throw new IllegalArgumentException("modifications cannot be null or empty");
        }
        var modifications = modificationsMap.entrySet().stream()
                .map(entry -> BeaconModification.load(entry.getKey(), entry.getValue()))
                .toList();
        var expCost = ExpCostCalculator.deserialize(map.getOrDefault("exp-cost", 0));
        var specialOps = EnumSet.noneOf(SpecialOps.class);
        for (var special : SpecialOps.values()) {
            if (map.get(special.key) instanceof Boolean bool && bool)
                specialOps.add(special);
        }
        return new SimpleRecipe(key, type, stack, template, beaconCondition, resultCondition, modifications, expCost, specialOps);
    }

    public enum SpecialOps {
        SET_SOULBOUND_OWNER("set-soulbound-owner");
        public final String key;
        SpecialOps(String key) {
            this.key = key;
        }
    }

}
