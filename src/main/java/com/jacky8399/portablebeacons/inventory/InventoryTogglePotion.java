package com.jacky8399.portablebeacons.inventory;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.function.Consumer;

public class InventoryTogglePotion implements InventoryProvider {

    private ItemStack stack;
    private Map<PotionEffectType, Integer> effects;
    private HashSet<PotionEffectType> disabledEffects;
    public InventoryTogglePotion(ItemStack stack) {
        if (!ItemUtils.isPortableBeacon(stack)) {
            throw new IllegalArgumentException("stack is not beacon");
        }
        this.stack = stack.clone();
        BeaconEffects beaconEffects = ItemUtils.getEffects(stack);
        effects = new TreeMap<>(PotionEffectUtils.POTION_COMPARATOR);
        effects.putAll(beaconEffects.getEffects());
        disabledEffects = new HashSet<>(beaconEffects.getDisabledEffects());
    }

    private void updateItem() {
        BeaconEffects effects = ItemUtils.getEffects(stack);
        HashMap<PotionEffectType, Integer> effectsMap = new HashMap<>(effects.getEffects());
        for (Map.Entry<PotionEffectType, Integer> entry : effectsMap.entrySet()) {
            int level = Math.abs(entry.getValue());
            if (disabledEffects.contains(entry.getKey()))
                entry.setValue(-level);
            else
                entry.setValue(level);
        }
        effects.setEffects(effectsMap);
        // mildly inefficient
        ItemStack temp = ItemUtils.createStackCopyItemData(effects, stack);
        stack.setItemMeta(temp.getItemMeta());
    }

    @Override
    public String getTitle(Player player) {
        return Config.effectsToggleTitle;
    }

    @Override
    public int getRows() {
        return Math.min(6, effects.size() / 9 + 3);
    }

    @Override
    public void populate(Player player, InventoryAccessor inventory) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(ChatColor.BLACK.toString());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inventory.set(4, stack);
            } else {
                inventory.set(i, border);
            }
        }

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = effects.entrySet().iterator();
        outer:
        for (int row = 1, rows = getRows(); row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                if (!iterator.hasNext())
                    break outer;

                Map.Entry<PotionEffectType, Integer> entry = iterator.next();
                PotionEffectType type = entry.getKey();
                int level = entry.getValue();
                String display = PotionEffectUtils.getDisplayName(type, level);
                ItemStack stack;
                ItemMeta meta;
                if (disabledEffects.contains(type)) {
                    stack = new ItemStack(Material.GLASS_BOTTLE);
                    meta = stack.getItemMeta();
                    // strip colour and add strikethrough
                    display = ChatColor.stripColor(display);
                    display = ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + display;
                } else {
                    stack = new ItemStack(Material.POTION);
                    meta = stack.getItemMeta();
                    PotionMeta potionMeta = (PotionMeta) meta;
                    potionMeta.setColor(type.getColor());
                    potionMeta.setBasePotionData(new PotionData(PotionType.LUCK)); // to make the potion glow
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                meta.setDisplayName(display);
                stack.setItemMeta(meta);
                Consumer<InventoryClickEvent> handler = null;
                if (Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type)) {
                    if (!Config.effectsToggleFineTunePerms ||
                            player.hasPermission("portablebeacons.effect-toggle." + PotionEffectUtils.getName(type))) {
                        handler = e -> {
                            if (!disabledEffects.add(type))
                                disabledEffects.remove(type);
                            updateItem();
                            inventory.requestRefresh(player);
                        };
                    }
                }
                inventory.set(row * 9 + column, stack, handler);
            }
        }
    }

    @Override
    public void close(Player player) {
        InventoryProvider.super.close(player);
    }
}
