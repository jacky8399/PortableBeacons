package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class WorldGuardHelper {
    public static StateFlag PORTABLE_BEACONS;
    public static SetFlag<BeaconEffectsFilter> PB_ALLOWED_EFFECTS;
    public static SetFlag<BeaconEffectsFilter> PB_BLOCKED_EFFECTS;
    @SuppressWarnings("unchecked")
    public static boolean tryAddFlag(PortableBeacons plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("allow-portable-beacons", true);
            registry.register(flag);
            PORTABLE_BEACONS = flag;
            SetFlag<BeaconEffectsFilter> allowedEffects = new SetFlag<>("allowed-beacon-effects", new BeaconEffectsFilterFlag(null));
            SetFlag<BeaconEffectsFilter> blockedEffects = new SetFlag<>("blocked-beacon-effects", new BeaconEffectsFilterFlag(null));
            registry.register(allowedEffects);
            registry.register(blockedEffects);
            PB_ALLOWED_EFFECTS = allowedEffects;
            PB_BLOCKED_EFFECTS = blockedEffects;
        } catch (FlagConflictException | IllegalStateException e) {
            // try to recover flags
            plugin.logger.warning("Failed to register flags; trying to use existing flags");
            PORTABLE_BEACONS = (StateFlag) registry.get("allow-portable-beacons");
            PB_ALLOWED_EFFECTS = (SetFlag<BeaconEffectsFilter>) registry.get("allowed-beacon-effects");
            PB_BLOCKED_EFFECTS = (SetFlag<BeaconEffectsFilter>) registry.get("blocked-beacon-effects");
        }
        return true;
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

    public static BeaconEffects filterBeaconEffects(Player player, BeaconEffects effects) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager != null) {
            ApplicableRegionSet set = manager.getApplicableRegions(BukkitAdapter.adapt(loc).toVector().toBlockPoint());
            if (!set.isVirtual()) {
                LocalPlayer wgPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
                BeaconEffects newEffects = effects.clone();
                Set<BeaconEffectsFilter> allowedEffects = set.queryValue(wgPlayer, PB_ALLOWED_EFFECTS);
                if (allowedEffects != null) {
                    newEffects.filter(allowedEffects, true);
                }
                Set<BeaconEffectsFilter> blockedEffects = set.queryValue(wgPlayer, PB_BLOCKED_EFFECTS);
                if (blockedEffects != null) {
                    newEffects.filter(blockedEffects, false);
                }
                return newEffects;
            }
        }
        return effects;
    }

    public static class BeaconEffectsFilterFlag extends Flag<BeaconEffectsFilter> {
        protected BeaconEffectsFilterFlag(String name) {
            super(name);
        }

        @Override
        public BeaconEffectsFilter parseInput(FlagContext context) throws InvalidFlagFormat {
            try {
                return unmarshal(context.getUserInput());
            } catch (IllegalArgumentException e) {
                throw new InvalidFlagFormat(e.getMessage());
            }
        }

        @Override
        public BeaconEffectsFilter unmarshal(@Nullable Object o) {
            if (o == null)
                return null;
            return BeaconEffectsFilter.fromString(o.toString());
        }

        @Override
        public Object marshal(BeaconEffectsFilter o) {
            return o.toString();
        }
    }
}
