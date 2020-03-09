package com.jacky8399.main;

import com.google.common.collect.Maps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON &&
                stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.STORAGE_KEY, BeaconEffects.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconEffects.STORAGE_KEY, BeaconEffects.STORAGE_TYPE);
    }

    public static void setEffects(ItemStack stack, BeaconEffects effects) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(BeaconEffects.STORAGE_KEY, BeaconEffects.STORAGE_TYPE, effects);
        stack.setItemMeta(meta);
    }

    public static ItemStack createStack(BeaconEffects effects) {
        ItemStack stack = new ItemStack(Material.BEACON);
        ItemMeta meta = Objects.requireNonNull(Bukkit.getItemFactory().getItemMeta(Material.BEACON));
        if (!Config.itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + Config.itemName);
        }
        if (Config.itemCustomModelData > -1) {
            meta.setCustomModelData(Config.itemCustomModelData);
        }
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> lore = effects.toLore();
        if (Config.itemLore.size() != 0) {
            lore.add("");
            lore.addAll(Config.itemLore);
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);

        setEffects(stack, effects);

        return stack;
    }

    public static ItemStack createStackCopyItemData(BeaconEffects effects, ItemStack oldIs) {
        ItemStack newIs = createStack(effects);
        ItemMeta oldMeta = oldIs.getItemMeta(), newMeta = newIs.getItemMeta();
        if (oldMeta.hasDisplayName()) {
            newMeta.setDisplayName(oldMeta.getDisplayName());
        }
        newIs.setItemMeta(newMeta);
        newIs.setAmount(oldIs.getAmount());
        return newIs;
    }

    private static int getCost(BeaconEffects effects) {
        return effects.consolidateEffects().values().stream().mapToInt(aShort -> 1 << aShort).sum();
    }

    public static int calculateCombinationCost(ItemStack is1, ItemStack is2) {
        if (isPortableBeacon(is1) && isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            return getCost(e1) + getCost(e2);
        }
        return 0;
    }

    public static ItemStack combineStack(ItemStack is1, ItemStack is2) {
        if (isPortableBeacon(is1) && isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            HashMap<PotionEffectType, Short> effects = Maps.newHashMap(e1.consolidateEffects());
            e2.consolidateEffects().forEach((pot, count) -> effects.merge(pot, count, Config.anvilCombinationCombineEffectsAdditively ? ItemUtils::sum : ItemUtils::anvilAlgorithm));
            // boundary checks
            if (effects.size() > Config.anvilCombinationMaxEffects ||
                    effects.entrySet().stream().anyMatch(
                            entry -> entry.getValue() > Config.anvilCombinationMaxAmplifier
                    )) {
                return null; // disallow combination
            }

            return createStack(new BeaconEffects(effects));
        }
        return null;
    }

    private static short sum(short s1, short s2) {
        return (short)(s1 + s2);
    }

    private static short anvilAlgorithm(short s1, short s2) {
        if (s1 == s2) {
            return (short)(s1 + 1);
        } else {
            return (short) Math.max(s1, s2);
        }
    }
}
