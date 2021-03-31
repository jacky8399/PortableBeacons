package com.jacky8399.portablebeacons.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface InventoryProvider {
    String getTitle(Player player);
    int getRows();

    void populate(Player player, InventoryAccessor inventory);
    default void close(Player player) {}

    interface InventoryAccessor {
        void set(int slot, ItemStack stack, @Nullable Consumer<InventoryClickEvent> eventHandler);
        default void set(int slot, ItemStack stack) {
            set(slot, stack, null);
        }

        void requestRefresh(Player player);

        @NotNull
        Inventory getInventory();
    }
}
