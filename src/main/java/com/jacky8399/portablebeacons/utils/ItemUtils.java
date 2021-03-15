package com.jacky8399.portablebeacons.utils;

import com.google.common.collect.Maps;
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
        meta.getPersistentDataContainer().set(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE, effects);
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
        return effects.getEffects().values().stream().mapToInt(aShort -> 1 << aShort).sum();
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
            if (Config.customEnchantSoulboundEnabled && Config.customEnchantSoulboundEnchantment != null && enchants.containsKey(Config.customEnchantSoulboundEnchantment))
                numOfEnchantsApplicable++;
            if (Config.customEnchantExpReductionEnabled && Config.customEnchantExpReductionEnchantment != null && enchants.containsKey(Config.customEnchantExpReductionEnchantment))
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
            HashMap<PotionEffectType, Short> effects = Maps.newHashMap(e1.getEffects());
            BinaryOperator<Short> algorithm = Config.anvilCombinationCombineEffectsAdditively ?
                    ItemUtils::sum : ItemUtils::anvilAlgorithm;
            e2.getEffects().forEach((pot, count) -> effects.merge(pot, count, algorithm));
            // soulbound owner check
            // check if both beacons are unclaimed/opwned
            UUID playerUuid = player.getUniqueId();
            if (Config.customEnchantSoulboundOwnerUsageOnly && 
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
            if (Config.customEnchantSoulboundEnabled && Config.customEnchantSoulboundEnchantment != null && enchants.containsKey(Config.customEnchantSoulboundEnchantment)) {
                allowCombination = true;
                if (++effects.soulboundLevel > Config.customEnchantSoulboundMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Soulbound") : null;
                if (effects.soulboundOwner == null || effects.soulboundOwner.equals(player.getUniqueId()))
                    effects.soulboundOwner = player.getUniqueId();
            }
            if (Config.customEnchantExpReductionEnabled && Config.customEnchantExpReductionEnchantment != null && enchants.containsKey(Config.customEnchantExpReductionEnchantment)) {
                allowCombination = true;
                if (++effects.expReductionLevel > Config.customEnchantExpReductionMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Experience Efficiency") : null;
            }

            return allowCombination ?
                    createStackCopyItemData(effects, is1) :
                    visual ? createMessageStack("Incompatible enchantments") : null;
        }
        return visual ? createMessageStack("Invalid combination") : null;
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
