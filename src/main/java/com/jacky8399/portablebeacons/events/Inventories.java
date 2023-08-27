package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.inventory.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
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

    public Inventories() {
        Bukkit.getScheduler().runTaskTimer(PortableBeacons.INSTANCE, () -> playerInventories.forEach((player, inv) -> {
            InventoryData data = pluginInventories.get(inv);
            data.provider.update(player, data);
        }), 0, 1);
    }

    private static WeakHashMap<Player, Inventory> playerInventories = new WeakHashMap<>();
    private static WeakHashMap<Inventory, InventoryData> pluginInventories = new WeakHashMap<>();

    public static void openInventory(Player player, InventoryProvider provider) {
        String title = provider.getTitle(player);
        int size = 9 * provider.getRows();
        Inventory inv = Bukkit.createInventory(player, size, title);
        InventoryData data = new InventoryData(inv, provider);
        Inventory oldInv = playerInventories.put(player, inv);
        if (oldInv != null) {
            InventoryData old = pluginInventories.remove(oldInv);
            if (old != null) {
                try {
                    old.provider.close(player);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        pluginInventories.put(inv, data);
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
        Inventory inv = playerInventories.get(player);
        if (e.getInventory() == inv) {
            playerInventories.remove(player);
        }
        InventoryData data = pluginInventories.remove(e.getInventory());
        if (data != null) {
            try {
                data.provider.close(player);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        InventoryData data = pluginInventories.get(inv);
        if (data == null) // not our inventory
            return;
        if (inv != e.getClickedInventory()) {
            InventoryAction action = e.getAction();
            // actions that might influence our inventory
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY || action == InventoryAction.COLLECT_TO_CURSOR ||
                    action == InventoryAction.NOTHING || action == InventoryAction.UNKNOWN)
                e.setResult(Event.Result.DENY);
            return;
        }
        e.setResult(Event.Result.DENY);

        Consumer<InventoryClickEvent> eventHandler = data.eventHandlers.get(e.getSlot());
        if (eventHandler != null) {
            try {
                eventHandler.accept(e);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        Inventory inventory = e.getInventory();
        if (!pluginInventories.containsKey(e.getInventory()))
            return;
        int size = inventory.getSize();
        // check if any of the slots involved is in the top inventory
        for (int slotID : e.getRawSlots()) {
            if (slotID < size) {
                e.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    private static class InventoryData implements InventoryProvider.InventoryAccessor {
        private InventoryData(Inventory inv, InventoryProvider provider) {
            this.inv = inv;
            this.provider = provider;
            this.eventHandlers = new ArrayList<>(Collections.nCopies(inv.getSize(), null));
        }

        private final Inventory inv;
        private final InventoryProvider provider;
        private final ArrayList<Consumer<InventoryClickEvent>> eventHandlers;

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
