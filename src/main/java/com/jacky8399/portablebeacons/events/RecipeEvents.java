package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.recipes.BeaconRecipe;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.recipes.SimpleRecipe;
import com.jacky8399.portablebeacons.utils.AdventureCompat;
import com.jacky8399.portablebeacons.utils.InventoryTypeUtils;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RecipeEvents implements Listener {

    public static final TranslatableComponent TOO_EXPENSIVE = Component.translatable()
            .key("container.repair.expensive")
            .color(NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .build();

    public RecipeEvents() {

    }

    public static final Logger LOGGER = PortableBeacons.INSTANCE.logger;

    private static ItemStack showError(Component component) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        AdventureCompat.setDisplayName(meta, component);
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

        // template slot in 1.20
        int slotOffset = inv instanceof SmithingInventory ? 1 : 0;

        ItemStack beacon = inv.getItem(slotOffset), right = inv.getItem(slotOffset + 1);

        if (beacon == null || beacon.getAmount() != 1 || right == null || right.getType() == Material.AIR)
            return false;
        BeaconEffects beaconEffects = ItemUtils.getEffects(beacon);
        if (beaconEffects == null)
            return false;
        if (Config.enchSoulboundOwnerUsageOnly && beaconEffects.soulboundOwner != null &&
                !beaconEffects.soulboundOwner.equals(player.getUniqueId())) {
            resultSetter.accept(null);
            return false; // owner only
        }
        BeaconRecipe recipe = RecipeManager.findRecipeFor(view.getType(), inv);
        if (recipe == null) {
            resultSetter.accept(null);
            return false; // no recipe
        }
        if (recipe instanceof SimpleRecipe simpleRecipe &&
                inv instanceof SmithingInventory smithingInventory) {
            // check template
            ItemStack template = simpleRecipe.template();
            ItemStack stack = smithingInventory.getItem(0);
            if (template == null) {
                if (stack != null && stack.getType() != Material.AIR) {
                    resultSetter.accept(null);
                    return false;
                }
            } else if (!template.isSimilar(stack)) {
                resultSetter.accept(null);
                return false;
            }
        }

        int cost = (int) Math.ceil(recipe.expCost().getCost(player, beacon, right));
        if (cost < 0) { // disallowed
            if (preview) { // Too expensive!
                if (inv instanceof AnvilInventory anvilInventory) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(40));
                } else {
                    resultSetter.accept(showError(TOO_EXPENSIVE));
                    return false;
                }
            }
            resultSetter.accept(null);
            return false;
        }
        var recipeOutput = recipe.getOutput(player, inv.getType(), inv);

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
            List<Component> lore = meta.hasLore() ? new ArrayList<>(AdventureCompat.getLore(meta)) : new ArrayList<>();
            if (cost > 0) {
                if (inv instanceof AnvilInventory anvilInventory &&
                        (cost < anvilInventory.getMaximumRepairCost() || player.getGameMode() == GameMode.CREATIVE)) {
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> anvilInventory.setRepairCost(cost));
                } else {
                    lore.add(Component.empty());
                    lore.add(Component.translatable("container.repair.cost", Style.style(NamedTextColor.GREEN, TextDecoration.BOLD), List.of(Component.text(cost))));
                }
            }
            if (Config.debug) {
                lore.add(Component.text("Recipe: " + recipe.id(), NamedTextColor.GRAY));
            }
            AdventureCompat.setLore(meta, lore);
            result.setItemMeta(meta);
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
            inv.setContents(recipeOutput.slots());
            inv.setItem(InventoryTypeUtils.getBeaconSlot(inv.getType()), null);
            resultSetter.accept(recipeOutput.output());
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAnvilClick(InventoryClickEvent e) {
        Inventory inv = e.getClickedInventory();
        if (!(inv instanceof AnvilInventory || inv instanceof SmithingInventory))
            return;
        Player player = (Player) e.getWhoClicked();
        if (Config.debug)
            LOGGER.info("InventoryClick for %s in %s.%d, click type: %s, action: %s".formatted(
                    player.getName(), e.getClickedInventory().getType(), e.getSlot(),
                    e.getClick(), e.getAction()
            ));
        boolean isSmithing = inv instanceof SmithingInventory;
        int slotOffset = isSmithing ? 1 : 0;
        if (e.getSlot() != 2 + slotOffset)
            return;

        ItemStack beacon = inv.getItem(slotOffset), right = inv.getItem(slotOffset + 1);
        if (!ItemUtils.isPortableBeacon(beacon))
            return;
        if ((right == null || right.getType().isAir()) &&
                inv instanceof AnvilInventory anvilInventory &&
                anvilInventory.getRenameText() != null && !anvilInventory.getRenameText().isEmpty())
            return; // renaming, don't care

        // handle the event ourselves
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
            case DROP, CONTROL_DROP -> dropItemAs(player, result);
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

    // allow placing any beacon into the base item slot
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onSmithingClick(InventoryClickEvent e) {
        if (!(e.getInventory() instanceof SmithingInventory inv))
            return;
        HumanEntity p = e.getWhoClicked();

        if (e.getClickedInventory() == inv) {
            // put beacons in base or sacrifice item slot
            if (e.getSlot() == 1 || e.getSlot() == 2) {
                if ((e.getAction() == InventoryAction.PLACE_ALL || e.getAction() == InventoryAction.PLACE_ONE) &&
                        (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) &&
                        ItemUtils.isPortableBeacon(e.getCursor())) {
                    ItemStack source = e.getCursor(), dest = inv.getItem(e.getSlot());
                    if (dest != null && dest.getAmount() == 1) {
                        // destination already occupied
                        e.setCancelled(true);
                        return;
                    }
                    // only place one
                    dest = source.clone();
                    dest.setAmount(1);
                    source.setAmount(source.getAmount() - 1);
                    e.setCancelled(true);
                    e.setCurrentItem(dest);
                    p.setItemOnCursor(source);
                }
            }
        } else {
            // shift click
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && ItemUtils.isPortableBeacon(e.getCurrentItem())) {
                int destSlot = 1;
                ItemStack source = e.getCurrentItem(), dest = inv.getItem(1);
                if (dest != null && dest.getAmount() == 1) { // slot 1 already occupied
                    destSlot = 2;
                    dest = inv.getItem(2);
                }
                if (dest != null && dest.getAmount() == 1) {
                    // both slots occupied
                    e.setCancelled(true);
                    return;
                }
                // only place one
                dest = source.clone();
                source.setAmount(source.getAmount() - 1);
                dest.setAmount(1);
                e.setCancelled(true);
                inv.setItem(destSlot, dest);
                e.setCurrentItem(source);
            }
        }

    }
}
