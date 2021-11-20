package com.jacky8399.portablebeacons.events;

import com.google.common.collect.Maps;
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
import org.bukkit.util.Vector;

import java.util.*;

public class ReminderOutline implements Listener {
    public ReminderOutline(PortableBeacons plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerItem, 0, 40);
    }

    private static Map<Player, HashMap<Vector, FallingBlock>> reminderOutline = new WeakHashMap<>();

    private static List<Block> findBeaconInRadius(Player player, double radius) {
        int r = (int) Math.ceil(radius);
        Block block = player.getLocation().getBlock();
        List<Block> blocks = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block relative = block.getRelative(x, y, z);
                    if (relative.getType() == Material.BEACON && ((Beacon) relative.getState()).getPrimaryEffect() != null) {
                        blocks.add(relative);
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
        ent.teleport(location);
        return ent;
    }

    private void removeOutlines(Player player) {
        HashMap<Vector, FallingBlock> entities = reminderOutline.remove(player);
        if (entities != null)
            entities.values().forEach(Entity::remove);
    }

    public void checkPlayerItem() {
        if (!Config.creationReminder || Config.ritualItem.getType() == Material.AIR) {
            cleanUp();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Config.creationReminderDisableIfOwned) {
                // check if already own beacon item
                if (Arrays.stream(player.getInventory().getStorageContents()).anyMatch(ItemUtils::isPortableBeacon)) {
                    // clear old entities
                    removeOutlines(player);
                    continue;
                }
            }

            if (player.getInventory().containsAtLeast(Config.ritualItem, Config.ritualItem.getAmount())) {
                List<Block> nearbyBeacons = findBeaconInRadius(player, Config.creationReminderRadius);
                if (nearbyBeacons.size() != 0) {
                    HashMap<Vector, FallingBlock> entities = reminderOutline.computeIfAbsent(player, ignored-> Maps.newHashMap());
                    for (Block nearbyBeacon : nearbyBeacons) {
                        Vector vector = nearbyBeacon.getLocation().toVector();
                        Location location = nearbyBeacon.getLocation().add(0.5, -0.01, 0.5);
                        // spawn or ensure there is a falling block
                        FallingBlock oldEnt = entities.get(vector);
                        if (oldEnt == null) {
                            if (entities.size() == 0 && !Config.creationReminderMessage.isEmpty()) // if first outline for player
                                player.sendMessage(Config.creationReminderMessage);
                            entities.put(vector, spawnFallingBlock(nearbyBeacon.getBlockData(), location));
                        } else {
                            oldEnt.teleport(location);
                        }
                        // display particles
                        nearbyBeacon.getWorld().spawnParticle(Particle.END_ROD, nearbyBeacon.getLocation().add(0.5, 1.5, 0.5), 20, 0, 0.5, 0, 0.4);
                    }
                    player.setCooldown(Config.ritualItem.getType(), 20);
                } else {
                    removeOutlines(player);
                }
            } else {
                removeOutlines(player);
            }
        }
        // remove old entries
        Iterator<Map.Entry<Player, HashMap<Vector, FallingBlock>>> iterator = reminderOutline.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, HashMap<Vector, FallingBlock>> entry = iterator.next();
            HashMap<Vector, FallingBlock> ents = entry.getValue();
            if (!entry.getKey().isOnline()) {
                ents.values().forEach(Entity::remove);
                iterator.remove();
            } else {
                ents.values().removeIf(block -> {
                    if (block.isDead()) {
                        return true;
                    } else if (block.getLocation().add(0, 0.1, 0).getBlock().getType() != Material.BEACON) {
                        block.remove();
                        return true;
                    } else {
                        block.setTicksLived(1);
                        return false;
                    }
                });
                if (ents.size() == 0)
                    iterator.remove();
            }
        }
    }

    public static void cleanUp() {
        reminderOutline.values().forEach(ents -> ents.values().forEach(Entity::remove));
        reminderOutline.clear();
    }
}
