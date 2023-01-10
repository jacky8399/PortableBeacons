package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReminderOutline implements Listener {
    public ReminderOutline(PortableBeacons plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerItem, 0, 40);
    }

    private static final Map<Player, Map<BlockVector, FallingBlock>> reminderOutline = new LinkedHashMap<>();

    private static List<Block> findBeaconInRadius(Player player, double radius) {
        double radiusSquared = radius * radius;
        int r = (int) Math.ceil(radius);
        Location location = player.getLocation();
        List<Block> blocks = new ArrayList<>();
        World world = player.getWorld();
        int x = location.getBlockX(), z = location.getBlockZ();
        for (int i = Math.floorDiv(x - r, 16), maxX = Math.floorDiv(x + r, 16); i <= maxX; i++) {
            for (int j = Math.floorDiv(z - r, 16), maxZ = Math.floorDiv(z + r, 16); j <= maxZ; j++) {
                Chunk chunk = world.getChunkAt(i, j);
                if (!chunk.isLoaded())
                    continue;
                for (var tileEntity : chunk.getTileEntities()) {
                    if (!(tileEntity instanceof Beacon beacon))
                        continue;
                    if (beacon.getPrimaryEffect() != null && beacon.getLocation().distanceSquared(location) <= radiusSquared) {
                        blocks.add(beacon.getBlock());
                    }
                }
            }
        }
        return blocks;
    }

    private static FallingBlock spawnFallingBlock(BlockData data, Location location) {
        World world = location.getWorld();
        Location initialLoc = location.clone();
        initialLoc.setY(world.getMaxHeight() - 1);
        FallingBlock ent = world.spawnFallingBlock(initialLoc, data);
        ent.setInvulnerable(true);
        ent.setGlowing(true);
        ent.setDropItem(false);
        ent.setGravity(false);
        ent.setTicksLived(1);
//        ent.teleport(location);
        return ent;
    }

    private void removeOutlines(Player player) {
        Map<BlockVector, FallingBlock> entities = reminderOutline.remove(player);
        if (entities != null)
            entities.values().forEach(Entity::remove);
    }

    public void checkPlayerItem() {
        if (!Config.creationReminder || !Config.ritualEnabled || Config.ritualItem.getType() == Material.AIR) {
            cleanUp();
            return;
        }
        ItemStack ritualItem = Config.ritualItem;
        boolean shouldCheckBeacon = Config.creationReminderDisableIfOwned;
        player:
        for (Player player : Bukkit.getOnlinePlayers()) {
            int ritualItemAmount = 0;

            PlayerInventory inventory = player.getInventory();
            for (ItemStack stack : inventory.getStorageContents()) {
                // check if already own beacon item
                if (shouldCheckBeacon && ItemUtils.isPortableBeacon(stack)) {
                    removeOutlines(player);
                    continue player;
                } else if (ritualItem.isSimilar(stack)) {
                    ritualItemAmount += stack.getAmount();
                }
            }
            // of course getStorageContents() doesn't include offhand
            ItemStack offhand = inventory.getItemInOffHand();
            if (shouldCheckBeacon && ItemUtils.isPortableBeacon(offhand)) {
                removeOutlines(player);
                continue;
            } else if (ritualItem.isSimilar(offhand)) {
                ritualItemAmount += offhand.getAmount();
            }

            if (ritualItemAmount < ritualItem.getAmount()) {
                removeOutlines(player);
                continue;
            }

            List<Block> nearbyBeacons = findBeaconInRadius(player, Config.creationReminderRadius);
            if (nearbyBeacons.size() == 0) {
                removeOutlines(player);
                continue;
            }
            Map<BlockVector, FallingBlock> entities = null;
            for (Block beacon : nearbyBeacons) {
                var vector = new BlockVector(beacon.getX(), beacon.getY(), beacon.getZ());
                // falling block doesn't render if it is in a block of the same type
                Location location = beacon.getLocation().add(0.5, -0.01, 0.5);
                // spawn falling block if none already spawned
                if (entities == null)
                    entities = reminderOutline.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
                boolean showReminderMessage = entities.size() == 0 && !Config.creationReminderMessage.isEmpty();
                entities.computeIfAbsent(vector, ignored -> {
                    if (showReminderMessage) // if first outline for player
                        player.sendMessage(Config.creationReminderMessage);
                    var fallingBlock = spawnFallingBlock(beacon.getBlockData(), location);
                    // hide from other players
                    for (var otherPlayer : Bukkit.getOnlinePlayers()) {
                        if (otherPlayer != player)
                            otherPlayer.hideEntity(PortableBeacons.INSTANCE, fallingBlock);
                    }
                    return fallingBlock;
                }).teleport(location);
                // display particles
                player.spawnParticle(Particle.END_ROD, beacon.getLocation().add(0.5, 1.5, 0.5), 20, 0, 0.5, 0, 0.4);
            }
            player.setCooldown(Config.ritualItem.getType(), 20);
        }
        // validate old entries
        for (var iterator = reminderOutline.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Player player = entry.getKey();
            Map<BlockVector, FallingBlock> entities = entry.getValue();
            if (!player.isOnline() || !player.isValid()) {
                entities.values().forEach(Entity::remove);
                iterator.remove();
            } else {
                for (var entityIterator = entities.values().iterator(); entityIterator.hasNext();) {
                    var block = entityIterator.next();
                    if (!block.isValid()) {
                        entityIterator.remove();
                        continue;
                    } else if (block.getLocation().add(0, 0.1, 0).getBlock().getType() != Material.BEACON) {
                        entityIterator.remove();
                        block.remove();
                        continue;
                    }
                    block.setTicksLived(1);
                    for (Player other : Bukkit.getOnlinePlayers())
                        if (other != player)
                            other.hideEntity(PortableBeacons.INSTANCE, block);
                }
                if (entities.size() == 0)
                    iterator.remove();
            }
        }
    }

    public static void cleanUp() {
        reminderOutline.values().forEach(ents -> ents.values().forEach(Entity::remove));
        reminderOutline.clear();
    }
}
