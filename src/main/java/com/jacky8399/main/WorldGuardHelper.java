package com.jacky8399.main;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class WorldGuardHelper {
    public static StateFlag PORTABLE_BEACONS;
    public static boolean tryAddFlag(PortableBeacons plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("allow-portable-beacons", true);
            registry.register(flag);
            PORTABLE_BEACONS = flag;
            return true;
        } catch (FlagConflictException e) {
            throw new IllegalStateException("Flag 'allow-portable-beacons' already exists!", e);
        }
    }

    public static boolean canUseBeacons(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager != null) {
            ApplicableRegionSet set = manager.getApplicableRegions(BukkitAdapter.adapt(loc).toVector().toBlockPoint());
            if (!set.isVirtual()) {
                return set.testState(WorldGuardPlugin.inst().wrapPlayer(player), PORTABLE_BEACONS);
            }
        }
        return true;
    }
}
