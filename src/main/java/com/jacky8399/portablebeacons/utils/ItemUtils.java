package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.logging.Level;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BEACON)
            return null;
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

    @NotNull
    public static ItemStack createStack(@Nullable Player player, @NotNull BeaconEffects effects) {
        return createItemCopyItemData(player, effects, new ItemStack(Material.BEACON));
    }

    @NotNull
    public static ItemStack createItemCopyItemData(@Nullable Player player, @NotNull BeaconEffects effects, @NotNull ItemStack stack) {
        ItemMeta meta = createMetaCopyItemData(player, effects, Objects.requireNonNull(stack.getItemMeta()));
        ItemStack newStack = new ItemStack(Material.BEACON);
        newStack.setItemMeta(meta);
        newStack.setAmount(stack.getAmount());
        return newStack;
    }

    // preserves item name and things
    @NotNull
    public static ItemMeta createMetaCopyItemData(@Nullable Player player, @NotNull BeaconEffects effects, @NotNull ItemMeta meta) {
        boolean hideEffects = !meta.getItemFlags().contains(ItemFlag.HIDE_POTION_EFFECTS); // can't use HIDE_ENCHANTS

        // copy lore, enchants and set effects
        if (!meta.hasDisplayName() && !Config.itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + Config.itemName);
        }
        if (Config.itemCustomModelData > -1) {
            meta.setCustomModelData(Config.itemCustomModelData);
        }

        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<BaseComponent> effectsLore = effects.toLore(hideEffects, hideEffects);

        if (!Config.itemLore.isEmpty()) {
            List<BaseComponent> lore = new ArrayList<>(effectsLore.size() + Config.itemLore.size() + 1);
            lore.addAll(effectsLore);
            lore.add(new TextComponent());
            boolean placeholderApi = Config.placeholderApi;
            try {
                for (String line : Config.itemLore) {
                    if (placeholderApi) line = PlaceholderAPI.setPlaceholders(player, line);
                    TextComponent text = new TextComponent(TextComponent.fromLegacyText(Config.translateColor(line)));
                    text.setItalic(false);
                    lore.add(text);
                }
            } catch (Exception ignored) {
            }
            ItemUtils.setLore(meta, lore);
        } else {
            ItemUtils.setLore(meta, effectsLore);
        }
        setEffects(meta, effects);
        return meta;
    }

    /*
        Spigot stores the display name and lore as JSON strings internally, but there is no API to set them to a JSON text.
        https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaItem.java?at=64c15270e76475e68b2167d4bfba162a4a827fe0#267
     */
    private static final VarHandle ITEM_META_DISPLAY_NAME;
    private static final VarHandle ITEM_META_LORE;
    static {
        Class<? extends ItemMeta> clazz = Objects.requireNonNull(Bukkit.getItemFactory().getItemMeta(Material.STONE)).getClass();
        VarHandle itemMetaDisplayName = null;
        VarHandle itemMetaLore = null;
        try {
            var privateLookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
            itemMetaDisplayName = privateLookup.findVarHandle(clazz, "displayName", String.class);
            itemMetaLore = privateLookup.findVarHandle(clazz, "lore", List.class);
        } catch (ReflectiveOperationException ex) {
            PortableBeacons.INSTANCE.logger.log(Level.WARNING, "Failed to find displayName/lore field in " + clazz.getName(), ex);
        }
        ITEM_META_DISPLAY_NAME = itemMetaDisplayName;
        ITEM_META_LORE = itemMetaLore;
    }
    public static void setDisplayName(ItemMeta meta, BaseComponent @Nullable... components) {
        if (components == null) {
            meta.setDisplayName(null);
            return;
        }

        if (ITEM_META_DISPLAY_NAME == null) {
            // failed to find displayName field
            meta.setDisplayName(BaseComponent.toLegacyText(components));
        } else {
            String json = ComponentSerializer.toString(components);
            ITEM_META_DISPLAY_NAME.set(meta, json);
        }
    }

    public static void setLore(ItemMeta meta, @Nullable List<BaseComponent> lore) {
        if (lore == null) {
            meta.setLore(null);
            return;
        }

        if (ITEM_META_LORE == null) {
            meta.setLore(lore.stream().map(component -> component.toLegacyText()).toList());
        } else {
            List<String> newLore = lore.stream().map(ComponentSerializer::toString).toList();
            ITEM_META_LORE.set(meta, newLore);
        }
    }

    public static void setRawLore(ItemMeta meta, @Nullable List<String> lore) {
        if (ITEM_META_LORE == null) {
            meta.setLore(lore);
        } else {
            ITEM_META_LORE.set(meta, lore);
        }
    }

    @Nullable
    public static List<String> getRawLore(ItemMeta meta) {
        return ITEM_META_LORE != null ? (List<String>) ITEM_META_LORE.get(meta) : meta.getLore();
    }

}
