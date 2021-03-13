package com.jacky8399.main;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ListIterator;

public class EffectsTimer extends BukkitRunnable {
    public static final double CYCLE_TIME_MULTIPLIER = 0.5;

    public void register() {
        this.runTaskTimer(PortableBeacons.INSTANCE, 0, (int) (150 * CYCLE_TIME_MULTIPLIER));
    }


    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyEffects(player);
        }
    }

    public void applyEffects(Player player) {
        boolean doWorldGuard = Config.worldGuard && PortableBeacons.INSTANCE.worldGuardInstalled;
        // world check
        if (Config.itemNerfsDisabledWorlds.contains(player.getWorld().getName()))
            return;

        if (doWorldGuard && !player.hasPermission("portablebeacons.bypass.world-guard-limit") &&
                !WorldGuardHelper.canUseBeacons(player))
            return;

        ListIterator<ItemStack> iterator = player.getInventory().iterator();
        while (iterator.hasNext()) {
            int nextIdx = iterator.nextIndex();
            ItemStack is = iterator.next(); // lol
            if (nextIdx > 8 && Config.itemNerfsOnlyApplyInHotbar) {
                continue; // out of hotbar
            }
            if (!ItemUtils.isPortableBeacon(is))
                continue;
            BeaconEffects beaconEffects = ItemUtils.getEffects(is);
            // effects for calculation purposes
            BeaconEffects actualEffects = beaconEffects;
            // owner check
            if (Config.customEnchantSoulboundEnabled && Config.customEnchantSoulboundOwnerUsageOnly &&
                    beaconEffects.soulboundOwner != null && !player.getUniqueId().equals(beaconEffects.soulboundOwner)) {
                continue;
            }

            // filter effects
            if (doWorldGuard)
                actualEffects = WorldGuardHelper.filterBeaconEffects(player, beaconEffects);

            // check levels
            if (!tryDeductExp(player, actualEffects))
                continue;

            actualEffects.applyEffects(player);

            boolean needsUpdate = beaconEffects.shouldUpdate();
            if (needsUpdate) {
                BeaconEffects newEffects;
                // force downgrade
                if (Config.itemNerfsForceDowngrade)
                    newEffects = beaconEffects.fixOpEffects();
                else
                    newEffects = beaconEffects.clone();
                iterator.set(ItemUtils.createStackCopyItemData(newEffects, is));
                if (Config.debug)
                    PortableBeacons.INSTANCE.logger.info("Updated obsolete beacon item in " + player.getName() + "'s inventory.");
            }
        }
    }

//    private static final BigDecimal CYCLE_TIME_MULTIPLIER_PRECISION = BigDecimal.valueOf(CYCLE_TIME_MULTIPLIER);
    boolean tryDeductExp(Player player, BeaconEffects effects) {
        double expPerCycle = effects.calcExpPerCycle() * CYCLE_TIME_MULTIPLIER;
        if (expPerCycle != 0) {
            double xp = player.getExp() - expPerCycle;
            if (xp < 0) { // deduct levels
                int required = Math.abs((int) Math.floor(xp));
                int newLevel = player.getLevel() - required;
                if (newLevel < 0)
                    return false;
                xp += required;
                player.setLevel(newLevel);
                player.setExp((float) xp);
            }
        }
        return true;
//        BigDecimal expPerCycle = BigDecimal.valueOf(effects.calcExpPerCycle());
//        BigDecimal xp = expPerCycle.multiply(CYCLE_TIME_MULTIPLIER_PRECISION);
//        if (xp.doubleValue() != 0) {
//            BigDecimal combined = BigDecimal.valueOf(player.getLevel() + player.getExp());
//            BigDecimal result = combined.subtract(xp);
//            if (result.doubleValue() < 0)
//                return false;
//            int newLevel = result.intValue();
//            player.setLevel(newLevel);
//            BigDecimal resultDecimal = result.subtract(BigDecimal.valueOf(result.intValue()));
//            player.setExp(resultDecimal.floatValue());
//        }
//        return true;
    }
}
