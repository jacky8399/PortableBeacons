package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.BeaconModification;
import com.jacky8399.portablebeacons.utils.BeaconPyramid;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public record SimpleRecipe(@NotNull NamespacedKey id,
                           @NotNull InventoryType type,
                           @NotNull ItemStack input,
                           @Nullable ItemStack template,
                           @NotNull List<BeaconModification> modifications,
                           @NotNull ExpCostCalculator expCost,
                           @NotNull Set<SpecialOps> specialOperations) implements BeaconRecipe {

    public SimpleRecipe {
        if (type != InventoryType.ANVIL && type != InventoryType.SMITHING)
            throw new IllegalArgumentException("Invalid inventory type " + type);
    }

    public SimpleRecipe(String id,
                        @NotNull InventoryType type,
                        @NotNull ItemStack input,
                        @NotNull List<BeaconModification> modifications,
                        @NotNull ExpCostCalculator expCost,
                        @NotNull Set<SpecialOps> specialOperations) {
        this(Objects.requireNonNull(NamespacedKey.fromString(id, PortableBeacons.INSTANCE), "Invalid key " + id),
                type, input, null, modifications, expCost, specialOperations);
    }

    @Override
    public RecipeOutput getOutput(Player player, ItemStack beacon, ItemStack input) {
        BeaconEffects effects = ItemUtils.getEffects(beacon);
        for (var modification : modifications) {
            if (!modification.modify(effects))
                return null;
        }

        if (specialOperations.contains(SpecialOps.SET_SOULBOUND_OWNER))
            effects.soulboundOwner = player.getUniqueId();

        var stack = ItemUtils.createMetaCopyItemData(player, effects, beacon);
        // copy pyramid
        BeaconPyramid pyramid;
        if ((pyramid = ItemUtils.getPyramid(beacon)) != null)
            ItemUtils.setPyramid(stack, pyramid);

        int amount = input.getAmount() - this.input.getAmount();
        if (amount <= 0) {
            return new RecipeOutput(stack, null);
        } else {
            var newInput = input.clone();
            newInput.setAmount(amount);
            return new RecipeOutput(stack, newInput);
        }
    }

    @Override
    public boolean isApplicableTo(ItemStack beacon, ItemStack input) {
        // enchanted_book isSimilar behaves weirdly
        if (this.input.getType() == Material.ENCHANTED_BOOK) {
            if (input.getType() != Material.ENCHANTED_BOOK)
                return false;
            EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) this.input.getItemMeta();
            if (enchantMeta == null || !enchantMeta.hasStoredEnchants())
                return true;
            if (!(input.getItemMeta() instanceof EnchantmentStorageMeta other))
                return false;
            for (var entry : enchantMeta.getStoredEnchants().entrySet()) {
                if (other.getStoredEnchantLevel(entry.getKey()) < entry.getValue())
                    return false;
            }
            return true;
        }
        return this.input.isSimilar(input) && input.getAmount() >= this.input.getAmount();
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

    private static ItemStack loadStack(Object o) {
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

//        if (type == InventoryType.SMITHING) {
//            PortableBeacons.INSTANCE.logger.warning("Loading SMITHING recipe " + id + ". Smithing recipes are deprecated due to changes to Smithing Tables in 1.20.");
//        }
        return new SimpleRecipe(key, type, stack, template, modifications, expCost, specialOps);
    }

    public enum SpecialOps {
        SET_SOULBOUND_OWNER("set-soulbound-owner");
        public final String key;
        SpecialOps(String key) {
            this.key = key;
        }
    }

}
