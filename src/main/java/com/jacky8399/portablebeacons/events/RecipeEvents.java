package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.recipes.BeaconRecipe;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        handleRecipe(e.getView(), true, e::setResult);
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        handleRecipe(e.getView(), true, e::setResult);
    }

    private boolean handleRecipe(InventoryView view, boolean preview, Consumer<ItemStack> resultSetter) {
        Player player = (Player) view.getPlayer();
        Inventory inv = view.getTopInventory();
        ItemStack beacon = inv.getItem(0), right = inv.getItem(1);
        if (beacon == null || beacon.getAmount() != 1 || right == null || right.getType().isAir())
            return false;
        BeaconEffects beaconEffects = ItemUtils.getEffects(beacon);
        if (beaconEffects == null)
            return false;
        if (Config.enchSoulboundOwnerUsageOnly && beaconEffects.soulboundOwner != null &&
                !beaconEffects.soulboundOwner.equals(player.getUniqueId())) {
            resultSetter.accept(null);
            return false; // owner only
        }
        if (Config.debug)
            PortableBeacons.INSTANCE.logger.info("handleRecipe (preview: %b) for %s in %s: %s".formatted(
                    preview, player.getName(), view.getType(), right.getType()
            ));
        BeaconRecipe recipe = RecipeManager.findRecipeFor(view.getType(), beacon, right);
        if (recipe == null) {
            resultSetter.accept(null);
            return false; // no recipe
        }
        int cost = recipe.getCost(beacon, right);
        if (cost < 0) { // disallowed
            resultSetter.accept(null);
            return false;
        }
        var recipeOutput = preview ?
                recipe.getPreviewOutput(player, beacon, right) :
                recipe.getOutput(player, beacon, right);

        if (Config.debug)
            PortableBeacons.INSTANCE.logger.info("handleRecipe (preview: %b) for %s in %s: recipe %s, cost %d, output %s".formatted(
                    preview, player.getName(), view.getType(), recipe.id(), cost, recipeOutput
            ));
        if (recipeOutput == null) {
            resultSetter.accept(null);
            return false;
        }

        int level = player.getLevel();
        boolean canAfford = player.getGameMode() == GameMode.CREATIVE || level >= cost;

        if (preview) {
            var result = recipeOutput.output();
            if (cost > 0) {
                ItemMeta meta = result.getItemMeta();
                @SuppressWarnings("ConstantConditions")
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if (inv instanceof AnvilInventory anvilInventory && anvilInventory.getMaximumRepairCost() > cost) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(cost));
                } else {
                    lore.add("");
                    lore.add("" + (canAfford ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD + "Enchantment cost: " + cost);
                }
                if (Config.debug) {
                    lore.add("" + ChatColor.GRAY + "Recipe: " + recipe.id());
                }
                meta.setLore(lore);
                result.setItemMeta(meta);
            }
            resultSetter.accept(result);
        } else {
            if (!canAfford) {
                // not enough experience
                return false;
            }

            // subtract levels
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.setLevel(level - cost);
            }
            // update input items
            inv.setItem(0, null);
            inv.setItem(1, recipeOutput.right());
            inv.setItem(2, null);
            resultSetter.accept(recipeOutput.output());
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAnvilClick(InventoryClickEvent e) {
        Inventory inv = e.getClickedInventory();
        if (!(inv instanceof AnvilInventory || inv instanceof SmithingInventory) ||
                e.getSlot() != 2)
            return;
        Player player = (Player) e.getWhoClicked();
        ItemStack beacon = inv.getItem(0), right = inv.getItem(1);
        if (!ItemUtils.isPortableBeacon(beacon))
            return;
        if ((right == null || right.getType().isAir()) &&
                inv instanceof AnvilInventory anvilInventory &&
                anvilInventory.getRenameText() != null && !anvilInventory.getRenameText().isEmpty())
            return; // renaming, don't care

        // handle the event ourselves
        if (Config.debug)
            PortableBeacons.INSTANCE.logger.info("InventoryClick for %s in %s, click type: %s, action: %s".formatted(
                    player.getName(), e.getClickedInventory().getType(),
                    e.getClick(), e.getAction()
            ));
        e.setResult(Event.Result.DENY);
        if (!isValidClick(e)) // disallow unhandled clicks
            return;

        handleRecipe(e.getView(), false, result -> handleClick(e, result));

    }

    private static boolean isValidClick(InventoryClickEvent e) {
        return true;
//        return switch (e.getAction()) {
//            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF,
//                    PICKUP_ONE, DROP_ALL_SLOT, DROP_ONE_SLOT,
//                    MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, CLONE_STACK -> true;
//            default -> false;
//        };
    }

    // replicate vanilla behavior
    private static void handleClick(InventoryClickEvent e, ItemStack result) {
        Inventory craftingInventory = e.getInventory();
        Sound sound = switch (craftingInventory.getType()) {
            case ANVIL -> Sound.BLOCK_ANVIL_USE;
            case SMITHING -> Sound.BLOCK_SMITHING_TABLE_USE;
            default -> throw new IllegalStateException("Invalid inventory type" + craftingInventory.getType());
        };

        Player player = (Player) e.getWhoClicked();
        PlayerInventory inventory = player.getInventory();
        World world = player.getWorld();
        Location location = player.getLocation();

        // play crafting station sound
        player.playSound(player, sound, SoundCategory.BLOCKS, 1, 1);

        ItemStack cursor = e.getCursor();
        switch (e.getClick()) {
            case LEFT, RIGHT -> {
                if (cursor == null || !cursor.isSimilar(result)) {
                    player.setItemOnCursor(result);
                } else {
                    cursor.setAmount(cursor.getAmount() + result.getAmount());
                    player.setItemOnCursor(cursor);
                }
            }
            case DROP, CONTROL_DROP -> world.dropItemNaturally(location, result);
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                var leftOver = inventory.addItem(result);
                leftOver.forEach((ignored, stack) -> world.dropItemNaturally(location, stack));
            }
            case SWAP_OFFHAND, NUMBER_KEY -> {
                int slot = e.getHotbarButton();
                Map<Integer, ItemStack> leftOver = Map.of();
                if (slot == -1) { // offhand swap
                    leftOver = inventory.addItem(inventory.getItemInOffHand());
                    inventory.setItemInOffHand(result);
                } else {
                    var original = inventory.getItem(slot);
                    if (original != null)
                        leftOver = inventory.addItem(original);
                    inventory.setItem(slot, result);
                }
                leftOver.forEach((ignored, stack) -> world.dropItemNaturally(location, stack));
            }
            case MIDDLE, CREATIVE -> {
                if (player.getGameMode() == GameMode.CREATIVE) {
                    var clone = result.clone();
                    clone.setAmount(clone.getMaxStackSize());
                    player.setItemOnCursor(clone);
                } else {
                    world.dropItemNaturally(location, result);
                }
            }
        }
    }
}
