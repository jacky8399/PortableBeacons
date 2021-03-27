package com.jacky8399.portablebeacons.inventory;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class InventoryTogglePotion implements IInventory {

    private ItemStack stack;
    private Map<PotionEffectType, Integer> effects;
    private HashSet<PotionEffectType> disabledEffects;
    public InventoryTogglePotion(ItemStack stack) {
        if (!ItemUtils.isPortableBeacon(stack)) {
            throw new IllegalArgumentException("stack is not beacon");
        }
        this.stack = stack;
        BeaconEffects beaconEffects = ItemUtils.getEffects(stack);
        effects = beaconEffects.getNormalizedEffects();
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
        ItemUtils.setEffects(stack, effects);
    }

    @Override
    public String getTitle(Player player) {
        return "";
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
        inventory.fill(border);

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = effects.entrySet().iterator();
        outer:
        for (int i = 1; i < 8; i++) { // use slots 1 - 7 in every row
            for (int j = 1, rows = getRows() - 1; j < rows; j++) {
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
                    ((PotionMeta) meta).setColor(type.getColor());
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                meta.setLore(Arrays.asList(display, ChatColor.YELLOW + "Click to toggle effect!"));
                stack.setItemMeta(meta);
                inventory.set(i + j * 9, stack, e -> {
                    if (!disabledEffects.add(type))
                        disabledEffects.remove(type);
                    updateItem();
                    inventory.requestRefresh(player);
                });
            }
        }
    }

    @Override
    public void close(Player player) {
        IInventory.super.close(player);
    }
}
