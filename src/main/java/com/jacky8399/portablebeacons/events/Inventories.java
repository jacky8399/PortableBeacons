package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.inventory.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class Inventories implements Listener {
    private static WeakHashMap<Player, Inventory> playerInventories = new WeakHashMap<>();
    private static WeakHashMap<Inventory, InventoryData> pluginInventories = new WeakHashMap<>();

    public static void openInventory(Player player, InventoryProvider provider) {
        String title = provider.getTitle(player);
        int size = 9 * provider.getRows();
        Inventory inv = Bukkit.createInventory(player, size, title);
        InventoryData data = new InventoryData(inv, provider);
        pluginInventories.put(inv, data);
        playerInventories.put(player, inv);
        provider.populate(player, data);
        // just to be safe
        Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> player.openInventory(inv));
    }

    @EventHandler
    public void onCleanUp(PluginDisableEvent e) { // i'm lazy
        if (e.getPlugin() instanceof PortableBeacons) {
            playerInventories.keySet().forEach(Player::closeInventory);
            playerInventories.clear();
            pluginInventories.clear();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Player player = (Player) e.getPlayer();
        Inventory inv = playerInventories.remove(player);
        if (inv != null) {
            InventoryData data = pluginInventories.remove(inv);
            data.provider.close(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (pluginInventories.containsKey(e.getInventory())) {
            Inventory inv = e.getInventory();
            if (inv != e.getClickedInventory()) {
                if (e.getClick().isKeyboardClick() || e.getClick().isShiftClick()) {
                    // clicking into the inventory
                    e.setResult(Event.Result.DENY);
                }
                return;
            }
            e.setResult(Event.Result.DENY);

            InventoryData data = pluginInventories.get(inv);
            Consumer<InventoryClickEvent> eventHandler = data.eventHandlers.get(e.getSlot());
            if (eventHandler != null) {
                eventHandler.accept(e);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (pluginInventories.containsKey(e.getInventory())) {
            e.setResult(Event.Result.DENY);
        }
    }

    private static class InventoryData implements InventoryProvider.InventoryAccessor {
        private InventoryData(Inventory inv, InventoryProvider provider) {
            this.inv = inv;
            this.provider = provider;
            this.eventHandlers = new ArrayList<>(Collections.nCopies(inv.getSize(), null));
        }

        private Inventory inv;
        private InventoryProvider provider;
        private ArrayList<Consumer<InventoryClickEvent>> eventHandlers;

        @Override
        public void set(int slot, ItemStack stack, @Nullable Consumer<InventoryClickEvent> eventHandler) {
            inv.setItem(slot, stack);
            eventHandlers.set(slot, eventHandler);
        }

        @Override
        public void requestRefresh(Player player) {
            Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> {
                provider.populate(player, this);
            });
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inv;
        }
    }
}
