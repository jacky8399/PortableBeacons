package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.recipes.BeaconRecipe;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RecipeEvents implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onGrindstoneItem(InventoryClickEvent e) {
        if (e.getClickedInventory() instanceof GrindstoneInventory ||
                (e.getInventory() instanceof GrindstoneInventory && (e.getClick().isShiftClick() || e.getClick().isKeyboardClick()))) {
            if (ItemUtils.isPortableBeacon(e.getCurrentItem()) || ItemUtils.isPortableBeacon(e.getCursor())) {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        onRecipe((Player) e.getView().getPlayer(), e.getView(), true, e::setResult);
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        onRecipe((Player) e.getView().getPlayer(), e.getView(), true, e::setResult);
    }

    private void onRecipe(Player player, InventoryView view, boolean preview, Consumer<ItemStack> resultSetter) {
        Inventory inv = view.getTopInventory();
        ItemStack beacon = inv.getItem(0), right = inv.getItem(1);
        if (beacon == null || beacon.getAmount() != 1 || right == null || right.getType().isAir())
            return;
        BeaconEffects beaconEffects = ItemUtils.getEffects(beacon);
        if (beaconEffects == null)
            return;
        if (Config.enchSoulboundOwnerUsageOnly && !beaconEffects.soulboundOwner.equals(player.getUniqueId()))
            return; // owner only
        BeaconRecipe recipe = RecipeManager.findRecipeFor(view.getType(), right);
        if (recipe == null)
            return; // no recipe

        int cost = recipe.getCost(beacon, right);
        if (cost < 0) // disallowed
            return;
        ItemStack result = preview ?
                recipe.getPreviewOutput(player, beacon, right) :
                recipe.getOutput(player, beacon, right);
        if (result == null)
            return;
        if (preview) {
            if (cost > 0) {
                ItemMeta meta = result.getItemMeta();
                @SuppressWarnings("ConstantConditions")
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (inv instanceof AnvilInventory anvilInventory && anvilInventory.getMaximumRepairCost() > cost) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(cost));
                } else {
                    lore.add("");
                    lore.add("" + ChatColor.GREEN + ChatColor.BOLD + "Enchantment cost: " + cost);
                }
                if (Config.debug) {
                    lore.add("" + ChatColor.GRAY + "Recipe: " + recipe.id());
                }
                meta.setLore(lore);
                result.setItemMeta(meta);
            }
            resultSetter.accept(result);
        } else {
            // subtract levels
            if (player.getGameMode() == GameMode.CREATIVE) {
                // no exp cost
                resultSetter.accept(result);
            } else if (player.getLevel() >= cost) {
                player.setLevel(player.getLevel() - cost);
                resultSetter.accept(result);
            }
            // no experience
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAnvilClick(InventoryClickEvent e) {
        Inventory inv = e.getClickedInventory();
        if (inv == null ||
                (inv.getType() != InventoryType.ANVIL && inv.getType() != InventoryType.SMITHING) ||
                e.getSlot() != 2)
            return;
        Player player = (Player) e.getWhoClicked();
        ItemStack beacon = inv.getItem(0), right = inv.getItem(1);
        if (!ItemUtils.isPortableBeacon(beacon))
            return;
        if (right == null && inv instanceof AnvilInventory anvilInventory &&
                anvilInventory.getRenameText() != null && !anvilInventory.getRenameText().isEmpty())
            return; // renaming, don't care
        // deny by default
        inv.setItem(2, null);
        e.setResult(Event.Result.DENY);

        onRecipe(player, e.getView(), false, result -> {
            if (inv instanceof AnvilInventory anvilInventory)
                anvilInventory.setRepairCost(0);
            inv.setItem(2, result);
            e.setResult(Event.Result.ALLOW);
        });
    }
}
