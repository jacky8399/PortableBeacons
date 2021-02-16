package com.jacky8399.main;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Predicate;

public class Events implements Listener {
    public Events(PortableBeacons plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::doItemLocationCheck, 0, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyEffects, 0, 150);
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerItem, 0, 40);
    }

    public void applyEffects() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            // world check
            if (Config.itemNerfsDisabledWorlds.contains(p.getWorld().getName()))
                return;

            if (Config.worldGuard && PortableBeacons.INSTANCE.worldGuard && !WorldGuardHelper.canUseBeacons(p) && !p.hasPermission("portablebeacons.bypass.world-guard-limit"))
                return;

            ListIterator<ItemStack> iterator = p.getInventory().iterator();
            while (iterator.hasNext()) {
                int nextIdx = iterator.nextIndex();
                if (nextIdx > 8 && Config.itemNerfsOnlyApplyInHotbar) {
                    return; // out of hotbar
                }
                ItemStack is = iterator.next();
                if (ItemUtils.isPortableBeacon(is)) {
                    BeaconEffects beaconEffects = ItemUtils.getEffects(is);
                    // deduct exp first
                    double xp = beaconEffects.calcExpPerCycle();
                    if (xp != 0) {
                        double playerXpPercentage = p.getExp();
                        int levels = p.getLevel();
                        while (playerXpPercentage < xp) {
                            if (--levels < 0)
                                return; // do not apply
                            playerXpPercentage += 1;
                        }
                        playerXpPercentage -= xp;
                        p.setLevel(levels);
                        p.setExp((float) playerXpPercentage);
                    }

                    PotionEffect[] effects = beaconEffects.toEffects();
                    for (PotionEffect effect : effects) {
                        p.addPotionEffect(effect);
                    }
                    boolean needsUpdate = beaconEffects.shouldUpdate();
                    if (needsUpdate) {
                        BeaconEffects newEffects;
                        // force downgrade
                        if (Config.itemNerfsForceDowngrade)
                            newEffects = beaconEffects.fixOpEffects();
                        else
                            newEffects = new BeaconEffects(beaconEffects.effects);
                        iterator.set(ItemUtils.createStackCopyItemData(newEffects, is));
                        PortableBeacons.INSTANCE.logger.info("Updated outdated beacon item in " + p.getName() + "'s inventory.");
                    }
                }
            }
        });
    }

    public void doItemLocationCheck() {
        if (netherStarItems.size() == 0)
            return;
        Iterator<Map.Entry<Item, Player>> iterator = netherStarItems.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Item, Player> entry = iterator.next();
            Item item = entry.getKey();
            Player thrower = entry.getValue();
            if (item.isDead()) {
                iterator.remove();
                continue;
            }

            Block loc = item.getLocation().getBlock();
            Block blockBelow = loc.getRelative(BlockFace.DOWN);
            if (blockBelow.getType() == Material.BEACON) {
                // check if activated
                Beacon tileEntity = (Beacon) blockBelow.getState();
                if (tileEntity.getPrimaryEffect() == null || tileEntity.getTier() == 0) {
                    continue;
                }

                PotionEffectType primary = tileEntity.getPrimaryEffect().getType();
                PotionEffectType secondary = null;
                if (tileEntity.getSecondaryEffect() != null) {
                    secondary = PotionEffectType.REGENERATION;
                } else if (tileEntity.getPrimaryEffect().getAmplifier() == 1) {
                    secondary = primary;
                }
                if (!removeBeacon(thrower, blockBelow, tileEntity.getTier())) {
                    continue;
                }
                ItemStack stack = ItemUtils.createStack(new BeaconEffects(primary, secondary));

                // check split amount
                ItemStack currentStack = item.getItemStack();
                int newAmount = currentStack.getAmount() - Config.ritualItem.getAmount();
                if (newAmount < 0) {
                    continue;
                } else if (newAmount > 0) {
                    currentStack.setAmount(newAmount);
                    item.getWorld().dropItem(item.getLocation(), currentStack);
                }


                // replace itemstack
                item.setItemStack(stack);
                item.setGlowing(true);

                // play sound
                item.getWorld().playSound(item.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
            }
        }
    }

    private static boolean callBreakBlockEvent(Player player, Block block) {
        BlockBreakEvent e = new BlockBreakEvent(block, player);
        e.setDropItems(false);
        Bukkit.getPluginManager().callEvent(e);
        return !e.isCancelled();
    }

    public boolean removeBeacon(Player player, Block block, int tier) {
        ArrayList<Block> blocksToBreak = Lists.newArrayList(block);
        for (int i = 1; i <= tier; i++) {
            for (int x = -i; x <= i; x++) {
                for (int z = -i; z <= i; z++) {
                    Block offset = block.getRelative(x, -i, z);
                    blocksToBreak.add(offset);
                }
            }
        }
        for (Block b : blocksToBreak) {
            if (!callBreakBlockEvent(player, b)) {
                return false;
            }
        }
        World world = block.getWorld();
        for (Block b : blocksToBreak) {
            world.playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());
            world.spawnParticle(Particle.EXPLOSION_HUGE, b.getLocation(), 1);
            b.setType(Material.AIR);
        }
        return true;
    }

    private static Map<Player, HashMap<Vector, FallingBlock>> reminderOutline = new WeakHashMap<>();

    private Set<Block> findBeaconInRadius(Player player, double radius) {
        int r = (int) Math.ceil(radius);
        Block block = player.getLocation().getBlock();
        Set<Block> blocks = Sets.newHashSet();
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

    public void checkPlayerItem() {
        if (!Config.itemCreationReminderEnabled || Config.ritualItem.getType() == Material.AIR) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (Config.itemCreationReminderDisableIfAlreadyOwnBeaconItem) {
                // check if already own beacon item
                if (Arrays.stream(player.getInventory().getStorageContents()).parallel().anyMatch(ItemUtils::isPortableBeacon)) {
                    continue;
                }
            }

            if (player.getInventory().containsAtLeast(Config.ritualItem, Config.ritualItem.getAmount())) {
                Set<Block> nearbyBeacons = findBeaconInRadius(player, Config.itemCreationReminderRadius);
                HashMap<Vector, FallingBlock> entities = reminderOutline.computeIfAbsent(player, ignored->Maps.newHashMap());
                if (nearbyBeacons.size() > 0) {
                    nearbyBeacons.forEach(nearbyBeacon -> {
                        Vector vector = nearbyBeacon.getLocation().toVector();
                        Location location = nearbyBeacon.getLocation().add(0.5, -0.01, 0.5);
                        // spawn or ensure there is a falling block
                        FallingBlock oldEnt = entities.get(vector);
                        if (oldEnt == null) {
                            Location initialLoc = location.clone();
                            initialLoc.setY(255);
                            if (entities.size() == 0) // if first falling sand for player
                                player.sendMessage(Config.itemCreationReminderMessage);
                            FallingBlock ent = player.getWorld().spawnFallingBlock(initialLoc, nearbyBeacon.getBlockData());
                            ent.setInvulnerable(true);
                            ent.setGlowing(true);
                            ent.setDropItem(false);
                            ent.setGravity(false);
                            ent.setTicksLived(1);
                            ent.teleport(location);
                            entities.put(vector, ent);
                        } else {
                            oldEnt.teleport(location);
                        }
                        // display particles
                        nearbyBeacon.getWorld().spawnParticle(Particle.END_ROD, nearbyBeacon.getLocation().add(0.5, 1.5, 0.5), 20, 0, 0.5, 0, 0.4);
                    });
                    player.setCooldown(Config.ritualItem.getType(), 20);
                } else {
                    if (entities.size() > 0)
                        entities.values().forEach(Entity::remove);
                }
            } else {
                HashMap<Vector, FallingBlock> entities = reminderOutline.remove(player);
                if (entities != null)
                    entities.values().forEach(Entity::remove);
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
            }
        }
    }

    public static void onDisable() {
        reminderOutline.values().forEach(ents -> ents.values().forEach(Entity::remove));
        reminderOutline.clear();
    }

    private WeakHashMap<Item, Player> netherStarItems = new WeakHashMap<>();
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        ItemStack is = e.getItemDrop().getItemStack();
        if (is.isSimilar(Config.ritualItem) && is.getAmount() >= Config.ritualItem.getAmount()) {
            netherStarItems.put(e.getItemDrop(), e.getPlayer());
        }
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent e) {
        if (netherStarItems.containsKey(e.getEntity()) || netherStarItems.containsKey(e.getTarget())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        netherStarItems.values().removeIf(Predicate.isEqual(e.getPlayer()));
    }

    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent e) {
        if (ItemUtils.isPortableBeacon(e.getItemInHand())) {
            e.setCancelled(true); // don't place the beacon (yet)
            // TODO rebuild the beacon structure?
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        AnvilInventory inv = e.getInventory();
        ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
        ItemStack newIs = ItemUtils.combineStack(is1, is2);
        int cost = ItemUtils.calculateCombinationCost(is1, is2);
        if (newIs != null) {
            if (cost <= 39) {
                e.setResult(newIs);
                Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> e.getInventory().setRepairCost(cost));
            } else if (!Config.anvilCombinationEnforceVanillaExpLimit) {
                ItemMeta meta = newIs.getItemMeta();
                List<String> lore = meta.hasLore() ? Lists.newArrayList(meta.getLore()) : Lists.newArrayList();
                lore.add(""+ChatColor.GREEN + ChatColor.BOLD + "Enchantment cost: " + cost);
                meta.setLore(lore);
                newIs.setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        if (e.getWhoClicked() instanceof Player && e.getClickedInventory() != null &&
                e.getClickedInventory().getType() == InventoryType.ANVIL) {
            Player player = (Player) e.getWhoClicked();
            if (e.getRawSlot() != 2 || (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.SHIFT_LEFT))
                return;

            AnvilInventory inv = (AnvilInventory) e.getClickedInventory();
            ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
            int levelRequired = ItemUtils.calculateCombinationCost(is1, is2);
            if (player.getLevel() < levelRequired || (levelRequired >= inv.getMaximumRepairCost() && Config.anvilCombinationEnforceVanillaExpLimit)) {
                e.setResult(Event.Result.DENY);
                e.setCancelled(true);
                return;
            }
            ItemStack newIs = ItemUtils.combineStack(is1, is2);
            if (newIs != null) {
                inv.setItem(0, null);
                inv.setItem(1, null);
                if (e.getClick() == ClickType.SHIFT_LEFT) {
                    player.getInventory().addItem(newIs);
                } else {
                    player.setItemOnCursor(newIs);
                }
                player.updateInventory();
                player.setLevel(player.getLevel() - levelRequired);
                // play sound too
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1, 1);
            }
        }
    }

    @EventHandler
    public void onGrindstoneItem(InventoryClickEvent e) {
        if (e.getInventory() instanceof GrindstoneInventory) {
            if (ItemUtils.isPortableBeacon(e.getCurrentItem()) || ItemUtils.isPortableBeacon(e.getCursor())) {
                e.setResult(Event.Result.DENY);
            }
        }
    }
}
