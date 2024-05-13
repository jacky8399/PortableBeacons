package com.jacky8399.portablebeacons.utils;

import org.bukkit.event.inventory.InventoryType;

public class InventoryTypeUtils {
    public static int getBeaconSlot(InventoryType type) {
        return switch (type) {
            case SMITHING -> 1;
            case ANVIL -> 0;
            default -> throw new IllegalArgumentException(type + " is not supported");
        };
    }

    public static int getSacrificeSlot(InventoryType type) {
        return switch (type) {
            case SMITHING -> 2;
            case ANVIL -> 1;
            default -> throw new IllegalArgumentException(type + " is not supported");
        };
    }

    public static int getTemplateSlot(InventoryType type) {
        if (type != InventoryType.SMITHING)
            throw new IllegalArgumentException(type + " does not have a template input");
        return 0;
    }
}
