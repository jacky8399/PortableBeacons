package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AnvilRecipe implements Listener {

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        AnvilInventory inv = e.getInventory();
        ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
        if (is1 == null || is1.getAmount() != 1 || is2 == null || is2.getAmount() != 1)
            return;
        ItemStack newIs = ItemUtils.combineStack((Player) e.getView().getPlayer(), is1, is2, false);
        int cost = ItemUtils.calculateCombinationCost(is1, is2);
        if (newIs != null) {
            if (!Config.anvilCombinationEnforceVanillaExpLimit && cost > inv.getMaximumRepairCost()) {
                ItemMeta meta = newIs.getItemMeta();
                @SuppressWarnings("ConstantConditions")
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(""+ ChatColor.GREEN + ChatColor.BOLD + "Enchantment cost: " + cost);
                meta.setLore(lore);
                newIs.setItemMeta(meta);
            } else {
                Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> e.getInventory().setRepairCost(cost));
            }
            e.setResult(newIs);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAnvilClick(InventoryClickEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        if (e.getClickedInventory() instanceof AnvilInventory) {
            Player player = (Player) e.getWhoClicked();
            Logger logger = PortableBeacons.INSTANCE.logger;
            if (e.getSlot() != 2) { // not target slot, ignoring
                return;
            } else if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.SHIFT_LEFT) { // not valid click
                // it is the correct slot still,
                // so prevent mayhem if it is a portable beacon
                if (ItemUtils.isPortableBeacon(e.getClickedInventory().getItem(0))) {
                    e.setResult(Event.Result.DENY);
                }
                return;
            }

            AnvilInventory inv = (AnvilInventory) e.getClickedInventory();
            ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
            // item validation
            if (!ItemUtils.isPortableBeacon(is1) ||
                    (is2 == null || !(ItemUtils.isPortableBeacon(is2) || is2.getType() == Material.ENCHANTED_BOOK)) ||
                    inv.getRenameText() != null && !inv.getRenameText().isEmpty()) {
                // slot 0 not portable beacon
                // or slot 1 not book and not beacon
                // or textbox not empty (vanilla behavior)
                return;
            } else if (is1.getAmount() != 1 || is2.getAmount() != 1) {
                // invalid amount
                e.setResult(Event.Result.DENY);
                return;
            }

            int levelRequired = ItemUtils.calculateCombinationCost(is1, is2);
            if (Config.debug) {
                logger.info("Anvil combination for " + player);
                logger.info("Required levels: " + levelRequired + ", max repair cost: " + inv.getMaximumRepairCost() + ", enforce: " + Config.anvilCombinationEnforceVanillaExpLimit);
            }
            if (player.getGameMode() != GameMode.CREATIVE && (player.getLevel() < levelRequired ||
                    levelRequired >= inv.getMaximumRepairCost() && Config.anvilCombinationEnforceVanillaExpLimit)) {
                e.setResult(Event.Result.DENY);
                return;
            }
            ItemStack newIs = ItemUtils.combineStack(player, is1, is2, false);
            if (Config.debug) logger.info("IS1: " + is1 + ", IS2: " + is2 + ", result: " + newIs);
            if (newIs != null) {
                if (e.getClick().isShiftClick()) {
                    inv.setItem(0, null);
                    inv.setItem(1, null);
                    player.getInventory().addItem(newIs);
                } else if (e.getCursor() != null) {
                    inv.setItem(0, null);
                    inv.setItem(1, null);
                    player.setItemOnCursor(newIs);
                } else {
                    e.setResult(Event.Result.DENY);
                    return;
                }
                player.updateInventory();
                if (player.getGameMode() != GameMode.CREATIVE)
                    player.setLevel(player.getLevel() - levelRequired);
                // play sound too
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1, 1);
            } else {
                e.setResult(Event.Result.DENY);
            }
        }
    }
}
