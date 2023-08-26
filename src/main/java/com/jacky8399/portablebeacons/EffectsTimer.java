package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class EffectsTimer implements Runnable {
    public static final double CYCLE_TIME_MULTIPLIER = 0.5;

    public void register() {
        Bukkit.getScheduler().runTaskTimer(PortableBeacons.INSTANCE, this, 0, (int) (150 * CYCLE_TIME_MULTIPLIER));
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyEffects(player);
        }
    }

    private static final int[] HOTBAR_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 40};
    public void applyEffects(Player player) {
        boolean doWorldGuard = Config.worldGuard && PortableBeacons.INSTANCE.worldGuardInstalled &&
                !WorldGuardHelper.canBypass(player);
        // world check
        if (Config.nerfDisabledWorlds.contains(player.getWorld().getName()))
            return;

        if (doWorldGuard && !WorldGuardHelper.canUseBeacons(player))
            return;

        boolean checkSoulbound = Config.enchSoulboundEnabled && Config.enchSoulboundOwnerUsageOnly;

        PlayerInventory inventory = player.getInventory();
        if (Config.nerfOnlyApplyInHotbar) {
            for (int i : HOTBAR_SLOTS) {
                ItemStack stack = inventory.getItem(i);
                tickItem(stack, player, inventory, i, doWorldGuard, checkSoulbound);
            }
        } else {
            // inventory slots (excluding armor and offhand)
            ItemStack[] items = inventory.getStorageContents();
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                tickItem(stack, player, inventory, i, doWorldGuard, checkSoulbound);
            }
            // armor (inventory slots 36-39)
            items = inventory.getArmorContents();
            for (int i = 0; i < items.length; i++) {
                ItemStack stack = items[i];
                tickItem(stack, player, inventory, 36 + i, doWorldGuard, checkSoulbound);
            }
            // offhand (inventory slot 40)
            tickItem(inventory.getItemInOffHand(), player, inventory, 40, doWorldGuard, checkSoulbound);
        }
    }

    private static void tickItem(ItemStack stack, Player player, PlayerInventory inventory, int index,
                          boolean doWorldGuard, boolean checkSoulbound) {
        BeaconEffects beaconEffects = ItemUtils.getEffects(stack);
        if (beaconEffects == null)
            return;
        // owner check
        if (checkSoulbound && !beaconEffects.isOwner(player))
            return;

        if (beaconEffects.shouldUpdate()) {
            // downgrade OP effects
            if (Config.nerfForceDowngrade)
                beaconEffects.validateEffects();
            beaconEffects.customDataVersion = Config.itemCustomVersion; // actually update custom data version
            inventory.setItem(index, ItemUtils.createStackCopyItemData(player, beaconEffects, stack));
            if (Config.debug)
                PortableBeacons.INSTANCE.logger.info("Updated obsolete beacon item in " + player.getName() + "'s inventory.");
        }

        // filtered effects
        BeaconEffects actualEffects = beaconEffects;
        // filter effects
        if (doWorldGuard)
            actualEffects = WorldGuardHelper.filterBeaconEffects(player, beaconEffects);

        // check levels
        if (!tryDeductExp(player, actualEffects))
            return;

        player.addPotionEffects(actualEffects.toEffects());

    }

    static boolean tryDeductExp(Player player, BeaconEffects effects) {
        // don't deduct xp from creative players
        if (player.getGameMode() == GameMode.CREATIVE)
            return true;

        double expPerCycle = effects.calcExpPerMinute() * CYCLE_TIME_MULTIPLIER * (1/16d);
        if (expPerCycle != 0) {
            double xp = player.getExp() - expPerCycle;
            if (xp < 0) { // deduct levels
                int required = Math.abs((int) Math.floor(xp));
                int newLevel = player.getLevel() - required;
                if (newLevel < 0)
                    return false;
                xp += required;
                player.setLevel(newLevel);
            }
            player.setExp((float) xp);
        }
        return true;
    }
}
