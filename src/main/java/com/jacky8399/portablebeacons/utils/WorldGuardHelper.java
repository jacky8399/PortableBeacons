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
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Set;

public class WorldGuardHelper {
    public static StateFlag PORTABLE_BEACONS;
    public static SetFlag<PotionEffectType> PB_ALLOWED_EFFECTS;
    public static SetFlag<PotionEffectType> PB_BLOCKED_EFFECTS;
    @SuppressWarnings("unchecked")
    public static boolean tryAddFlag(PortableBeacons plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("allow-portable-beacons", true);
            registry.register(flag);
            PORTABLE_BEACONS = flag;
            SetFlag<PotionEffectType> allowedEffects = new SetFlag<>("allowed-beacon-effects", new PotionEffectTypeFlag(null));
            SetFlag<PotionEffectType> blockedEffects = new SetFlag<>("blocked-beacon-effects", new PotionEffectTypeFlag(null));
            registry.register(allowedEffects);
            registry.register(blockedEffects);
            PB_ALLOWED_EFFECTS = allowedEffects;
            PB_BLOCKED_EFFECTS = blockedEffects;
        } catch (FlagConflictException | IllegalStateException e) {
            // try to recover flags
            plugin.logger.warning("Failed to register flags; trying to use existing flags");
            PORTABLE_BEACONS = (StateFlag) registry.get("allow-portable-beacons");
            PB_ALLOWED_EFFECTS = (SetFlag<PotionEffectType>) registry.get("allowed-beacon-effects");
            PB_BLOCKED_EFFECTS = (SetFlag<PotionEffectType>) registry.get("blocked-beacon-effects");
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
                HashMap<PotionEffectType, Short> map = new HashMap<>(effects.getEffects());
                Set<PotionEffectType> allowedEffects = set.queryValue(wgPlayer, PB_ALLOWED_EFFECTS);
                if (allowedEffects != null) {
                    map.keySet().retainAll(allowedEffects);
                }
                Set<PotionEffectType> blockedEffects = set.queryValue(wgPlayer, PB_BLOCKED_EFFECTS);
                if (blockedEffects != null) {
                    map.keySet().removeAll(blockedEffects);
                }
                BeaconEffects newEffects = effects.clone();
                newEffects.setEffects(map);
                return newEffects;
            }
        }
        return effects;
    }

    public static class PotionEffectTypeFlag extends Flag<PotionEffectType> {
        protected PotionEffectTypeFlag(String name) {
            super(name);
        }

        @Override
        public PotionEffectType parseInput(FlagContext context) throws InvalidFlagFormat {
            return PotionEffectUtils.parsePotion(context.getUserInput(), false)
                    .orElseThrow(()->new InvalidFlagFormat(context.getUserInput() + "is not a valid potion effect"));
        }

        @Override
        public PotionEffectType unmarshal(@Nullable Object o) {
            return PotionEffectUtils.parsePotion(String.valueOf(o), true).orElse(null);
        }

        @Override
        public Object marshal(PotionEffectType o) {
            return PotionEffectUtils.getName(o);
        }
    }
}
