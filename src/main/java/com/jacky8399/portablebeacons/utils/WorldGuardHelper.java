package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WorldGuardHelper {
    public static StateFlag PORTABLE_BEACONS;
    public static SetFlag<String> PB_ALLOWED_EFFECTS;
    public static SetFlag<String> PB_BLOCKED_EFFECTS;
    @SuppressWarnings("unchecked")
    public static boolean tryAddFlag(PortableBeacons plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("allow-portable-beacons", true);
            registry.register(flag);
            PORTABLE_BEACONS = flag;
            SetFlag<String> allowedEffects = new SetFlag<>("allowed-beacon-effects", new BeaconEffectsFilterFlag(null));
            SetFlag<String> blockedEffects = new SetFlag<>("blocked-beacon-effects", new BeaconEffectsFilterFlag(null));
            registry.register(allowedEffects);
            registry.register(blockedEffects);
            PB_ALLOWED_EFFECTS = allowedEffects;
            PB_BLOCKED_EFFECTS = blockedEffects;
        } catch (FlagConflictException | IllegalStateException e) {
            // try to recover flags
            plugin.logger.warning("Failed to register flags; trying to use existing flags");
            PORTABLE_BEACONS = (StateFlag) registry.get("allow-portable-beacons");
            PB_ALLOWED_EFFECTS = (SetFlag<String>) registry.get("allowed-beacon-effects");
            PB_BLOCKED_EFFECTS = (SetFlag<String>) registry.get("blocked-beacon-effects");
        }
        return true;
    }

    /**
     * Checks if the player should bypass WorldGuard flags
     * @param player The player
     * @return Whether the player should bypass WorldGuard flags
     */
    public static boolean canBypass(Player player) {
        var wgPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(wgPlayer, wgPlayer.getWorld());
    }

    /**
     * Checks if the player is in a WorldGuard region where beacon use is disabled
     * @param player The player
     * @return Whether the player can use beacons
     */
    public static boolean canUseBeacons(Player player) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        return query.testState(BukkitAdapter.adapt(loc), WorldGuardPlugin.inst().wrapPlayer(player), PORTABLE_BEACONS);
    }

    /**
     * Filters the given beacon effects
     * @param player The associated player
     * @param effects The effects to filter
     * @return The filtered effects, or the original effects if no filters were applied
     */
    @NotNull
    public static BeaconEffects filterBeaconEffects(Player player, BeaconEffects effects) {
        Location loc = player.getLocation();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        RegionQuery query = container.createQuery();

        var wgLocation = BukkitAdapter.adapt(loc);
        LocalPlayer wgPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        Set<String> allowedEffects = query.queryValue(wgLocation, wgPlayer, PB_ALLOWED_EFFECTS);
        List<BeaconEffectsFilter> allowedFilters = null;
        if (allowedEffects != null) {
            allowedFilters = new ArrayList<>(allowedEffects.size());
            for (String allowed : allowedEffects) {
                allowedFilters.add(BeaconEffectsFilter.fromString(allowed));
            }
        }
        Set<String> blockedEffects = query.queryValue(wgLocation, wgPlayer, PB_BLOCKED_EFFECTS);
        List<BeaconEffectsFilter> blockedFilters = null;
        if (blockedEffects != null) {
            blockedFilters = new ArrayList<>(blockedEffects.size());
            for (String allowed : blockedEffects) {
                blockedFilters.add(BeaconEffectsFilter.fromString(allowed));
            }
        }
        if (allowedFilters != null || blockedFilters != null) {
            BeaconEffects newEffects = new BeaconEffects(effects);
            newEffects.filter(allowedFilters, blockedFilters);
            return newEffects;
        }
        return effects;
    }

    // I forgot why this was changed to Flag<String>
    // surely there is a perfectly valid reason
    public static class BeaconEffectsFilterFlag extends Flag<String> {
        protected BeaconEffectsFilterFlag(String name) {
            super(name);
        }

        @Override
        public String parseInput(FlagContext context) throws InvalidFlagFormat {
            try {
                return unmarshal(context.getUserInput());
            } catch (IllegalArgumentException e) {
                throw new InvalidFlagFormat(e.getMessage());
            }
        }

        @Override
        public String unmarshal(@Nullable Object o) {
            if (o == null)
                return null;
            return BeaconEffectsFilter.fromString(o.toString()).toString();
        }

        @Override
        public Object marshal(String o) {
            return o;
        }
    }
}
