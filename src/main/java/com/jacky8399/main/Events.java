package com.jacky8399.main;

import com.google.common.collect.Maps;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class Events implements Listener {
    public Events(PortableBeacons plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::doItemLocationCheck, 0, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkPlayerItem, 0, 40);
    }

    public void doItemLocationCheck() {
        if (ritualItems.size() == 0)
            return;
        Iterator<Map.Entry<Item, Player>> iterator = ritualItems.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Item, Player> entry = iterator.next();
            Item item = entry.getKey();
            Player thrower = entry.getValue();
            if (item.isDead()) {
                iterator.remove();
                continue;
            }

            Block blockBelow = item.getLocation().add(0, -1, 0).getBlock();
            if (blockBelow.getType() == Material.BEACON) {
                // check if activated
                Beacon tileEntity = (Beacon) blockBelow.getState();
                if (tileEntity.getPrimaryEffect() == null || tileEntity.getTier() == 0) {
                    continue;
                }

                Map<PotionEffectType, Short> effects = new HashMap<>();
                PotionEffect primary = tileEntity.getPrimaryEffect();
                effects.put(primary.getType(), (short) primary.getAmplifier());
                PotionEffect secondary = tileEntity.getSecondaryEffect();
                if (secondary != null)
                    effects.put(secondary.getType(), (short) secondary.getAmplifier());
                if (!removeBeacon(thrower, blockBelow, tileEntity.getTier())) {
                    continue;
                }
                // play sound to complement block break effects
                item.getWorld().playSound(item.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1, 1);

                ItemStack stack = ItemUtils.createStack(new BeaconEffects(effects));
                // check split amount
                ItemStack currentStack = item.getItemStack();
                int newAmount = currentStack.getAmount() - Config.ritualItem.getAmount();
                if (newAmount < 0) {
                    continue;
                } else if (newAmount > 0) {
                    currentStack.setAmount(newAmount);
                    Item splitItem = item.getWorld().dropItem(item.getLocation(), currentStack);
                    ritualItems.put(splitItem, thrower);
                }

                // replace item stack
                item.setItemStack(stack);
                item.setOwner(thrower.getUniqueId()); // pickup priority
                item.setGlowing(true);

                iterator.remove();
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
        ArrayList<Block> blocksToBreak = new ArrayList<>();
        blocksToBreak.add(block);
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
                    HashMap<Vector, FallingBlock> entities = reminderOutline.computeIfAbsent(player, ignored->Maps.newHashMap());
                    for (Block nearbyBeacon : nearbyBeacons) {
                        Vector vector = nearbyBeacon.getLocation().toVector();
                        Location location = nearbyBeacon.getLocation().add(0.5, -0.01, 0.5);
                        // spawn or ensure there is a falling block
                        FallingBlock oldEnt = entities.get(vector);
                        if (oldEnt == null) {
                            if (entities.size() == 0) // if first outline for player
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

    private WeakHashMap<Item, Player> ritualItems = new WeakHashMap<>();
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        ItemStack is = e.getItemDrop().getItemStack();
        if (is.isSimilar(Config.ritualItem) && is.getAmount() >= Config.ritualItem.getAmount()) {
            ritualItems.put(e.getItemDrop(), e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent e) {
        ritualItems.remove(e.getEntity());
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent e) {
        if (ritualItems.containsKey(e.getEntity()) || ritualItems.containsKey(e.getTarget())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        ritualItems.values().removeIf(Predicate.isEqual(e.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent e) {
        if (ItemUtils.isPortableBeacon(e.getItemInHand())) {
            e.setCancelled(true); // don't place the beacon
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        AnvilInventory inv = e.getInventory();
        ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
        ItemStack newIs = ItemUtils.combineStack((Player) e.getView().getPlayer(), is1, is2, false);
        int cost = ItemUtils.calculateCombinationCost(is1, is2);
        if (newIs != null) {
            if (!Config.anvilCombinationEnforceVanillaExpLimit && cost > inv.getMaximumRepairCost()) {
                ItemMeta meta = newIs.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(""+ChatColor.GREEN + ChatColor.BOLD + "Enchantment cost: " + cost);
                meta.setLore(lore);
                newIs.setItemMeta(meta);
                e.setResult(newIs);
            } else {
                e.setResult(newIs);
                Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> e.getInventory().setRepairCost(cost));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onAnvilClick(InventoryClickEvent e) {
        if (!Config.anvilCombinationEnabled)
            return;
        if (e.getClickedInventory() instanceof AnvilInventory) {
            Player player = (Player) e.getWhoClicked();
            Logger logger = PortableBeacons.INSTANCE.logger;
            if (e.getSlot() != 2) { // not target slot, ignoring
                return;
            } else if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.SHIFT_LEFT) { // not valid click
                // it is the correct slot still,
                // so prevent mayhem if it is a portable beacon
                if (ItemUtils.isPortableBeacon(e.getClickedInventory().getItem(0))) {
                    e.setResult(Event.Result.DENY);
                }
                return;
            }

            AnvilInventory inv = (AnvilInventory) e.getClickedInventory();
            ItemStack is1 = inv.getItem(0), is2 = inv.getItem(1);
            // item validation
            if (!ItemUtils.isPortableBeacon(is1) ||
                    (is2 == null || !(ItemUtils.isPortableBeacon(is2) || is2.getType() == Material.ENCHANTED_BOOK)) ||
                    inv.getRenameText() != null && !inv.getRenameText().isEmpty() ||
                    is1.getAmount() != 1 || is2.getAmount() != 1) {
                // slot 0 not portable beacon
                // or slot 1 not book and not beacon
                // or textbox not empty (vanilla behavior)
                // or invalid amount
                return;
            }

            int levelRequired = ItemUtils.calculateCombinationCost(is1, is2);
            if (Config.debug) {
                logger.info("Anvil combination for " + player);
                logger.info("Required levels: " + levelRequired + ", max repair cost: " + inv.getMaximumRepairCost() + ", enforce: " + Config.anvilCombinationEnforceVanillaExpLimit);
            }
            if (player.getLevel() < levelRequired ||
                    levelRequired >= inv.getMaximumRepairCost() && Config.anvilCombinationEnforceVanillaExpLimit) {
                e.setResult(Event.Result.DENY);
                return;
            }
            ItemStack newIs = ItemUtils.combineStack(player, is1, is2, false);
            if (Config.debug) logger.info("IS1: " + is1 + ", IS2: " + is2 + ", result: " + newIs);
            if (newIs != null) {
                if (e.getClick().isShiftClick()) {
                    inv.setItem(0, null);
                    inv.setItem(1, null);
                    player.getInventory().addItem(newIs);
                } else if (e.getCursor() != null) {
                    inv.setItem(0, null);
                    inv.setItem(1, null);
                    player.setItemOnCursor(newIs);
                } else {
                    e.setResult(Event.Result.DENY);
                    return;
                }
                player.updateInventory();
                if (player.getGameMode() != GameMode.CREATIVE)
                    player.setLevel(player.getLevel() - levelRequired);
                // play sound too
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 1, 1);
            } else {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onGrindstoneItem(InventoryClickEvent e) {
        if (e.getClickedInventory() instanceof GrindstoneInventory ||
                (e.getInventory() instanceof GrindstoneInventory && (e.getClick().isShiftClick() || e.getClick().isKeyboardClick()))) {
            if (ItemUtils.isPortableBeacon(e.getCurrentItem()) || ItemUtils.isPortableBeacon(e.getCursor())) {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    // for soulbound enchantment

    // add these items later
    Map<Player, List<ItemStack>> soulboundItems = new WeakHashMap<>();
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH) // you can cancel PlayerDeathEvent on Paper
    public void onDeath(PlayerDeathEvent e) {
        if (!e.getKeepInventory()) {
            for (Iterator<ItemStack> iterator = e.getDrops().iterator(); iterator.hasNext();) {
                ItemStack dropped = iterator.next();
                if (ItemUtils.isPortableBeacon(dropped)) {
                    Player player = e.getEntity();
                    BeaconEffects effects = ItemUtils.getEffects(dropped);
                    if (effects.soulboundLevel != 0 && effects.soulboundOwner.equals(player.getUniqueId())) {
                        iterator.remove();
                        List<ItemStack> items = soulboundItems.computeIfAbsent(player, key -> new ArrayList<>());
                        if (Config.customEnchantSoulboundConsumeLevelOnDeath) {
                            effects.soulboundLevel--;
                            items.add(ItemUtils.createStackCopyItemData(effects, dropped));
                        } else {
                            items.add(dropped);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        List<ItemStack> toGive = soulboundItems.remove(player);
        if (toGive != null) {
            player.getInventory().addItem(toGive.toArray(new ItemStack[0]));
        }
    }
}
