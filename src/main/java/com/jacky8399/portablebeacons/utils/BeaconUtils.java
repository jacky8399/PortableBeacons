package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.events.Events;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BeaconUtils {
    public static int checkBeaconTier(Beacon tileEntity) {
        if (tileEntity.getPrimaryEffect() == null || tileEntity.getTier() == 0) {
            return -1;
        }
        PotionEffect primary = tileEntity.getPrimaryEffect(), secondary = tileEntity.getSecondaryEffect();
        int beaconTier = tileEntity.getTier();
        int requiredTier = Math.max(PotionEffectUtils.getRequiredTier(primary), PotionEffectUtils.getRequiredTier(secondary));
        if (beaconTier >= requiredTier && requiredTier != -1)
            return requiredTier;
        return -1;
    }

    /**
     * Calculates the number of blocks in the lowest layer of a beacon pyramid
     * @param tier The tier of the beacon
     * @return The number of blocks
     */
    public static int getBeaconTierSize(int tier) {
        int side = tier * 2 + 1;
        return side * side;
    }

    /**
     * Calculates the total number of blocks in a beacon pyramid, excluding the beacon itself
     * @param tier The tier of the beacon
     * @return The number of blocks
     */
    public static int getBeaconSize(int tier) {
        int result = 0;
        for (int i = 1; i <= tier; i++) {
            result += getBeaconTierSize(tier);
        }
        return result;
    }

    private static boolean checkBlockEventFail(Player player, Block block) {
        BlockBreakEvent e = new BlockBreakEvent(block, player);
        e.setDropItems(false);
        Bukkit.getPluginManager().callEvent(e);
        return e.isCancelled();
    }

    /**
     * Remove the beacon pyramid for a given beacon
     * @param player The player who triggered the removal
     * @param beaconBlock The beacon block
     * @param tier The tier of the beacon
     * @param modifyBeaconBlock Whether to break the beacon block as well
     * @return The beacon pyramid, or null if removal failed
     */
    public static BeaconPyramid removeBeacon(Player player, Block beaconBlock, int tier, boolean modifyBeaconBlock) {
        record BlockToBreak(Location location, @Nullable Material effect) {
            BlockToBreak(Block block, boolean playEffect) {
                this(block.getLocation(), playEffect ? block.getType() : null);
            }
        }

        List<List<BlockToBreak>> blocksToBreak = new ArrayList<>(tier + 1);
        // beacon block
        if (modifyBeaconBlock) {
            if (beaconBlock.getType() != Material.BEACON)
                return null;
            else if (checkBlockEventFail(player, beaconBlock))
                return null;
            blocksToBreak.add(List.of(new BlockToBreak(beaconBlock, true)));
        } else {
            blocksToBreak.add(List.of());
        }

        var beaconBase = new ArrayList<BeaconPyramid.BeaconBase>(getBeaconSize(tier));
        for (int currentTier = 1; currentTier <= tier; currentTier++) {
            var blocks = new ArrayList<BlockToBreak>(getBeaconTierSize(currentTier));

            for (int x = -currentTier; x <= currentTier; x++) {
                for (int z = -currentTier; z <= currentTier; z++) {
                    Block offset = beaconBlock.getRelative(x, -currentTier, z);
                    // validate block
                    if (!Tag.BEACON_BASE_BLOCKS.isTagged(offset.getType()))
                        return null;
                    else if (checkBlockEventFail(player, offset))
                        return null;
                    // only play block breaking effect for surface blocks
                    blocks.add(new BlockToBreak(offset, Math.abs(x) == currentTier || Math.abs(z) == currentTier));
                    beaconBase.add(new BeaconPyramid.BeaconBase(offset.getBlockData(), x, -currentTier, z));
                }
            }
            blocksToBreak.add(blocks);
        }
        World world = beaconBlock.getWorld();
        BlockData glass = Material.GLASS.createBlockData();
        for (int i = 0; i < blocksToBreak.size(); i++) {
            var blocks = blocksToBreak.get(i);
            for (var block : blocks) {
                world.setBlockData(block.location, glass); // to prevent duping
                Events.ritualTempBlocks.add(block.location);
            }
            // don't break that many blocks at once
            Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                for (var block : blocks) {
                    Location location = block.location;
                    if (block.effect != null)
                        world.playEffect(location, Effect.STEP_SOUND, block.effect);
                    world.setType(location, Material.AIR);
                    Events.ritualTempBlocks.remove(location);
                }
            }, (i + 1) * 4L);
        }
        return new BeaconPyramid(tier, beaconBase);
    }
}
