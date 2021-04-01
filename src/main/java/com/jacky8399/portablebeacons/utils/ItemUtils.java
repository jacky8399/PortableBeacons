package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.BinaryOperator;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static void setEffects(ItemStack stack, BeaconEffects effects) {
        ItemMeta meta = stack.getItemMeta();
        setEffects(meta, effects);
        stack.setItemMeta(meta);
    }

    public static void setEffects(ItemMeta meta, BeaconEffects effects) {
        meta.getPersistentDataContainer().set(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE, effects);
    }

    public static boolean isPyramid(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static BeaconPyramid getPyramid(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static void setPyramid(ItemStack stack, BeaconPyramid pyramid) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE, pyramid);
        stack.setItemMeta(meta);
    }

    public static ItemMeta createMeta(BeaconEffects effects) {
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
        setEffects(meta, effects);
        return meta;
    }

    public static ItemStack createStack(BeaconEffects effects) {
        ItemStack stack = new ItemStack(Material.BEACON);
        ItemMeta meta = createMeta(effects);
        stack.setItemMeta(meta);
        return stack;
    }

    // preserves item name and things
    public static ItemStack createStackCopyItemData(BeaconEffects effects, ItemStack oldIs) {
        ItemMeta newMeta = createMeta(effects);

        ItemMeta oldMeta = oldIs.getItemMeta(), actualMeta = oldMeta.clone();

        // copy lore, enchants and set effects
        if (newMeta.hasLore())
            actualMeta.setLore(newMeta.getLore());
        for (Map.Entry<Enchantment, Integer> enchants : newMeta.getEnchants().entrySet()) {
            actualMeta.addEnchant(enchants.getKey(), enchants.getValue(), true);
        }
        actualMeta.addItemFlags(newMeta.getItemFlags().toArray(new ItemFlag[0]));
        if (newMeta.hasCustomModelData())
            actualMeta.setCustomModelData(newMeta.getCustomModelData());
        setEffects(actualMeta, effects);

        ItemStack newIs = new ItemStack(Material.BEACON);
        newIs.setItemMeta(actualMeta);
        newIs.setAmount(oldIs.getAmount());
        return newIs;
    }

    private static int getCost(BeaconEffects effects) {
        return effects.getEffects().values().stream().mapToInt(level -> 1 << level).sum();
    }

    public static int calculateCombinationCost(ItemStack is1, ItemStack is2) {
        if (!isPortableBeacon(is1))
            return 0;
        if (isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            return getCost(e1) + getCost(e2);
        } else if (is2 != null && is2.getType() == Material.ENCHANTED_BOOK && is2.hasItemMeta()) {
            BeaconEffects effects = getEffects(is1);
            Map<Enchantment, Integer> enchants = ((EnchantmentStorageMeta) is2.getItemMeta()).getStoredEnchants();
            int numOfEnchantsApplicable = 0;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null && enchants.containsKey(Config.enchSoulboundEnchantment))
                numOfEnchantsApplicable++;
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null && enchants.containsKey(Config.enchExpReductionEnchantment))
                numOfEnchantsApplicable++;

            return getCost(effects) * numOfEnchantsApplicable;
        }
        return 0;
    }

    public static ItemStack createMessageStack(String message) {
        ItemStack messageStack = new ItemStack(Material.BARRIER);
        ItemMeta meta = messageStack.getItemMeta();
        meta.setDisplayName(ChatColor.RED + message);
        messageStack.setItemMeta(meta);
        return messageStack;
    }

    public static ItemStack combineStack(Player player, ItemStack is1, ItemStack is2, boolean visual) {
        if (!isPortableBeacon(is1) || is2 == null)
            return null;
        if (isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            HashMap<PotionEffectType, Integer> effects = new HashMap<>(e1.getEffects());
            BinaryOperator<Integer> algorithm = Config.anvilCombinationCombineEffectsAdditively ?
                    Integer::sum : ItemUtils::anvilAlgorithm;
            e2.getEffects().forEach((pot, count) -> effects.merge(pot, count, algorithm));
            // soulbound owner check
            // check if both beacons are unclaimed/opwned
            UUID playerUuid = player.getUniqueId();
            if (Config.enchSoulboundOwnerUsageOnly &&
                    ((e1.soulboundOwner != null && !playerUuid.equals(e1.soulboundOwner)) ||
                    (e2.soulboundOwner != null && !playerUuid.equals(e2.soulboundOwner)))) {
                return visual ? createMessageStack("You do not own the portable beacons") : null;
            }

            // check max effects count / overpowered effects
            if (effects.size() > Config.anvilCombinationMaxEffects ||
                    effects.entrySet().stream().anyMatch(entry -> {
                        Config.PotionEffectInfo info = Config.effects.get(entry.getKey());
                        return entry.getValue() > (info != null ? info.getMaxAmplifier() : Config.effectsDefault.maxAmplifier);
                    })) {
                return visual ? createMessageStack("Overpowered effects") : null;
            }

            return createStack(new BeaconEffects(effects));
        } else if (is2.getType() == Material.ENCHANTED_BOOK && is2.hasItemMeta()) {
            BeaconEffects effects = getEffects(is1);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) is2.getItemMeta();
            Map<Enchantment, Integer> enchants = meta.getStoredEnchants();
            boolean allowCombination = false;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null && enchants.containsKey(Config.enchSoulboundEnchantment)) {
                allowCombination = true;
                if (++effects.soulboundLevel > Config.enchSoulboundMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Soulbound") : null;
                if (effects.soulboundOwner == null || effects.soulboundOwner.equals(player.getUniqueId()))
                    effects.soulboundOwner = player.getUniqueId();
            }
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null && enchants.containsKey(Config.enchExpReductionEnchantment)) {
                allowCombination = true;
                if (++effects.expReductionLevel > Config.enchExpReductionMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Experience Efficiency") : null;
            }

            return allowCombination ?
                    createStackCopyItemData(effects, is1) :
                    visual ? createMessageStack("Incompatible enchantments") : null;
        }
        return visual ? createMessageStack("Invalid combination") : null;
    }

    private static int anvilAlgorithm(int s1, int s2) {
        if (s1 == s2) {
            return s1 + 1;
        } else {
            return Math.max(s1, s2);
        }
    }
}
