package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.recipes.BeaconRecipe;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;
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

    private static ItemStack showError(String error) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.RED + error);
        stack.setItemMeta(meta);
        return stack;
    }
    private static ItemStack showLocalizedError(String error) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        var translatable = new TranslatableComponent(error);
        translatable.setColor(ChatColor.RED);
        translatable.setItalic(false);
        ItemUtils.setDisplayName(meta, translatable);
        stack.setItemMeta(meta);
        return stack;
    }

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
        BeaconRecipe recipe = RecipeManager.findRecipeFor(view.getType(), beacon, right);
        if (recipe == null) {
            resultSetter.accept(null);
            return false; // no recipe
        }
        int cost = recipe.expCost().getCost(beacon, right);
        if (cost < 0) { // disallowed
            if (preview) { // Too expensive!
                if (inv instanceof AnvilInventory anvilInventory) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(40));
                } else {
                    resultSetter.accept(showLocalizedError("container.repair.expensive"));
                    return false;
                }
            }
            resultSetter.accept(null);
            return false;
        }
        var recipeOutput = preview ?
                recipe.getPreviewOutput(player, beacon, right) :
                recipe.getOutput(player, beacon, right);

        if (Config.debug)
            PortableBeacons.INSTANCE.logger.info("handleRecipe (preview: %b) for %s in %s: recipe %s, cost %d".formatted(
                    preview, player.getName(), view.getType(), recipe.id(), cost
            ));
        if (recipeOutput == null) {
            resultSetter.accept(null);
            return false;
        }

        int level = player.getLevel();
        boolean canAfford = player.getGameMode() == GameMode.CREATIVE || level >= cost;

        if (preview) {
            var result = recipeOutput.output().clone();
            ItemMeta meta = result.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(ItemUtils.getRawLore(meta)) : new ArrayList<>();
            int oldSize = lore.size();
            if (cost > 0) {
                if (inv instanceof AnvilInventory anvilInventory &&
                        (cost < anvilInventory.getMaximumRepairCost() || player.getGameMode() == GameMode.CREATIVE)) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(cost));
                } else {
                    var translatable = new TranslatableComponent("container.repair.cost", Integer.toString(cost));
                    translatable.setColor(canAfford ? ChatColor.GREEN : ChatColor.RED);
                    translatable.setItalic(false);
                    translatable.setBold(true);

                    lore.add(ComponentSerializer.toString(new TextComponent()));
                    lore.add(ComponentSerializer.toString(translatable));
                }
            }
            if (Config.debug) {
                var recipeComponent = new ComponentBuilder("Recipe: " + recipe.id())
                        .color(ChatColor.GRAY).italic(false).create();
                lore.add(ComponentSerializer.toString(recipeComponent));
            }
            if (lore.size() != oldSize) {
                ItemUtils.setRawLore(meta, lore);
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
        if (result == null)
            return;

        Inventory craftingInventory = e.getInventory();
        Sound sound = switch (craftingInventory.getType()) {
            case ANVIL -> Sound.BLOCK_ANVIL_USE;
            case SMITHING -> Sound.BLOCK_SMITHING_TABLE_USE;
            default -> throw new IllegalStateException("Invalid inventory type" + craftingInventory.getType());
        };

        Player player = (Player) e.getWhoClicked();
        PlayerInventory inventory = player.getInventory();

        // play crafting station sound
        player.getWorld().playSound(player, sound, SoundCategory.BLOCKS, 1, 1);

        ItemStack cursor = e.getCursor();
        switch (e.getClick()) {
            case LEFT, RIGHT -> {
                if (cursor == null) {
                    player.setItemOnCursor(result);
                } else if (!cursor.isSimilar(result)) {
                    player.setItemOnCursor(result);
                    dropItemAs(player, cursor);
                } else {
                    cursor.setAmount(cursor.getAmount() + result.getAmount());
                    player.setItemOnCursor(cursor);
                }
            }
            // DROP, CONTROL_DROP, default
            default -> dropItemAs(player, result);
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                var leftOver = inventory.addItem(result);
                leftOver.forEach((ignored, stack) -> dropItemAs(player, stack));
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
                leftOver.forEach((ignored, stack) -> dropItemAs(player, stack));
            }
            case MIDDLE, CREATIVE -> {
                if (player.getGameMode() == GameMode.CREATIVE) {
                    var clone = result.clone();
                    clone.setAmount(clone.getMaxStackSize());
                    player.setItemOnCursor(clone);
                } else {
                    dropItemAs(player, result);
                }
            }
        }
    }

    private static void dropItemAs(Player player, ItemStack stack) {
        Location location = player.getEyeLocation();
        player.getWorld().dropItem(location, stack, item -> {
            item.setThrower(player.getUniqueId());
            item.setOwner(player.getUniqueId());
            item.setVelocity(location.getDirection());
        });
    }
}
