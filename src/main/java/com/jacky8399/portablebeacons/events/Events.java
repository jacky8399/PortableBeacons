package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.inventory.InventoryTogglePotion;
import com.jacky8399.portablebeacons.utils.BeaconPyramid;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public final class Events implements Listener {
    public static void registerEvents() {
        PortableBeacons plugin = PortableBeacons.INSTANCE;
        Bukkit.getPluginManager().registerEvents(new Events(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new ReminderOutline(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new AnvilRecipe(), plugin);
        Bukkit.getPluginManager().registerEvents(new Inventories(), plugin);
    }

    public static Events INSTANCE;
    public Events(PortableBeacons plugin) {
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::doItemLocationCheck, 0, 20);
    }

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

    public void doItemLocationCheck() {
        if (!Config.ritualEnabled)
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
                int tier = checkBeaconTier(tileEntity);
                if (tier == -1) {
                    continue;
                }

                Map<PotionEffectType, Integer> effects = new HashMap<>();
                PotionEffect primary = tileEntity.getPrimaryEffect();
                effects.put(primary.getType(), primary.getAmplifier() + 1);
                PotionEffect secondary = tileEntity.getSecondaryEffect();
                if (secondary != null)
                    effects.put(secondary.getType(), secondary.getAmplifier() + 1);
                Map<Vector, BlockData> beaconBase = removeBeacon(thrower, blockBelow, tier, true);
                if (beaconBase == null) { // failed to remove beacon
                    continue;
                }
                // play sound to complement block break effects
                item.getWorld().playSound(item.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1);

                ItemStack stack = ItemUtils.createStack(new BeaconEffects(effects));
                ItemUtils.setPyramid(stack, new BeaconPyramid(tier, beaconBase));
                // check split amount
                ItemStack currentStack = item.getItemStack();
                int newAmount = currentStack.getAmount() - Config.ritualItem.getAmount();
                if (newAmount < 0) {
                    continue;
                } else if (newAmount > 0) {
                    currentStack.setAmount(newAmount);
                    // ConcurrentModificationException
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, ()->{
                        Item splitItem = item.getWorld().dropItem(item.getLocation().add(0, 1, 0), currentStack);
                        splitItem.setVelocity(new Vector(0, 0, 0));
                        ritualItems.put(splitItem, thrower);
                    });
                }

                // replace item stack
                item.setItemStack(stack);
                item.setOwner(thrower.getUniqueId()); // pickup priority
                item.setGlowing(true);

                iterator.remove();
            }
        }
    }

    private static boolean checkBlockEventFail(Player player, Block block) {
        BlockBreakEvent e = new BlockBreakEvent(block, player);
        e.setDropItems(false);
        Bukkit.getPluginManager().callEvent(e);
        return e.isCancelled();
    }

    // the beacon will definitely be removed if this method returns a non-null value
    public static Map<Vector, BlockData> removeBeacon(Player player, Block block, int tier, boolean modifyBeaconBlock) {
        Block[][] blocksToBreak = new Block[tier + 1][];
        // beacon block
        if (modifyBeaconBlock) {
            blocksToBreak[0] = new Block[] {block};
            if (block.getType() != Material.BEACON)
                return null;
            else if (checkBlockEventFail(player, block))
                return null;
        } else {
            blocksToBreak[0] = new Block[] {};
        }

        Map<Vector, BlockData> beaconBase = new HashMap<>();
        for (int currentTier = 1; currentTier <= tier; currentTier++) {
            int i = 0;
            int currentTierSize = (int) Math.pow(currentTier * 2 + 1, 2);
            Block[] blockz = new Block[currentTierSize];
            for (int x = -currentTier; x <= currentTier; x++) {
                for (int z = -currentTier; z <= currentTier; z++) {
                    Block offset = block.getRelative(x, -currentTier, z);
                    // validate block
                    if (!Tag.BEACON_BASE_BLOCKS.isTagged(offset.getType()))
                        return null;
                    else if (checkBlockEventFail(player, offset))
                        return null;
                    blockz[i++] = offset;

                    BlockVector vector = new BlockVector(x, -currentTier, z);
                    beaconBase.put(vector, offset.getBlockData());
                }
            }
            blocksToBreak[currentTier] = blockz;
        }
        World world = block.getWorld();
        for (int i = 0; i < blocksToBreak.length; i++) {
            Block[] blocks = blocksToBreak[i];
            // copy array
            for (Block b : blocks) {
                b.setType(Material.GLASS); // to prevent duping
            }
            // don't break that many blocks at once
            Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                for (Block b : blocks) {
                    Location location = b.getLocation();
                    world.playEffect(location, Effect.STEP_SOUND, b.getType());
                    //world.spawnParticle(Particle.EXPLOSION_LARGE, location.add(Math.random(), Math.random(), Math.random()), 1);
                    b.setType(Material.AIR);
                }
            }, (i + 1) * 4L);
        }
        return beaconBase;
    }

    public WeakHashMap<Item, Player> ritualItems = new WeakHashMap<>();
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (!Config.ritualEnabled)
            return;
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
    public void onBreakBlock(BlockDropItemEvent e) {
        if (!Config.ritualEnabled || Config.ritualItem.getType() != Material.AIR)
            return;
        BlockState state = e.getBlockState();
        if (state instanceof Beacon) {
            Beacon beacon = (Beacon) state;
            int tier = checkBeaconTier(beacon);
            if (tier == -1)
                return;

            Map<PotionEffectType, Integer> effects = new HashMap<>();
            PotionEffect primary = beacon.getPrimaryEffect();
            effects.put(primary.getType(), primary.getAmplifier() + 1);
            PotionEffect secondary = beacon.getSecondaryEffect();
            if (secondary != null)
                effects.put(secondary.getType(), secondary.getAmplifier() + 1);
            Map<Vector, BlockData> beaconBase = removeBeacon(e.getPlayer(), e.getBlock(), tier, false);
            if (beaconBase == null) {
                return;
            }

            BeaconEffects beaconEffects = new BeaconEffects(effects);
            BeaconPyramid pyramid = new BeaconPyramid(tier, beaconBase);
            e.getItems().forEach(item -> {
                if (item.getItemStack().getType() == Material.BEACON) {
                    ItemStack stack = ItemUtils.createStack(beaconEffects);
                    ItemUtils.setPyramid(stack, pyramid);
                    item.setItemStack(stack);
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent e) {
        ItemStack stack = e.getItemInHand();
        if (ItemUtils.isPortableBeacon(stack) && !ItemUtils.isPyramid(stack)) {
            e.setCancelled(true); // don't place the beacon if it is not a pyramid
        }
    }

    public boolean checkBlockPlaceEventFail(Player player, EquipmentSlot hand, Block placedAgainst, Block toPlace, BlockData state) {
        BlockCanBuildEvent canBuildEvent = new BlockCanBuildEvent(placedAgainst, player, state, true);
        Bukkit.getPluginManager().callEvent(canBuildEvent);
        if (!canBuildEvent.isBuildable())
            return true;
        ItemStack fakeStack = new ItemStack(state.getMaterial());
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(toPlace, toPlace.getState(), placedAgainst, fakeStack, player, true, hand);
        Bukkit.getPluginManager().callEvent(placeEvent);
        return !placeEvent.canBuild() || placeEvent.isCancelled();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlacePyramid(BlockPlaceEvent e) {
        ItemStack stack = e.getItemInHand();
        if (ItemUtils.isPortableBeacon(stack) && ItemUtils.isPyramid(stack)) {
            e.setCancelled(true);
            BeaconEffects effects = ItemUtils.getEffects(stack);
            BeaconPyramid pyramid = ItemUtils.getPyramid(stack);
            Block placeLocation = e.getBlockPlaced();
            Block beaconLocation = placeLocation.getRelative(0, pyramid.tier, 0);
            if (checkBlockPlaceEventFail(e.getPlayer(), e.getHand(), e.getBlockAgainst(), beaconLocation, Bukkit.createBlockData(Material.BEACON))) {
                return;
            }
            // check block placement first
            for (Map.Entry<Vector, BlockData> entry : pyramid.beaconBase.entrySet()) {
                Vector diff = entry.getKey();
                BlockData data = entry.getValue();
                // only place beacon base blocks!
                if (!Tag.BEACON_BASE_BLOCKS.isTagged(data.getMaterial())) {
                    continue; // don't check and filter later
                }
                Block relative = beaconLocation.getRelative(diff.getBlockX(), diff.getBlockY(), diff.getBlockZ());
                if (checkBlockPlaceEventFail(e.getPlayer(), e.getHand(), e.getBlockAgainst(), relative, data)) {
                    return;
                }
            }
            // then update blocks
            World world = beaconLocation.getWorld();
            // group by Y level to stagger creation
            Map<Integer, Map<Vector, BlockData>> pyramidByLayer = pyramid.beaconBase.entrySet().stream()
                    .filter(entry -> Tag.BEACON_BASE_BLOCKS.isTagged(entry.getValue().getMaterial())) // only place beacon base blocks
                    .collect(groupingBy(entry -> entry.getKey().getBlockY(), toMap(Map.Entry::getKey, Map.Entry::getValue)));
            for (Map.Entry<Integer, Map<Vector, BlockData>> entry : pyramidByLayer.entrySet()) {
                int delay = pyramid.tier + entry.getKey(); // key is negative
                Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                    for (Map.Entry<Vector, BlockData> entry1 : entry.getValue().entrySet()) {
                        Vector diff = entry1.getKey();
                        BlockData data = entry1.getValue();
                        Block relative = beaconLocation.getRelative(diff.getBlockX(), diff.getBlockY(), diff.getBlockZ());
                        Location relativeLocation = relative.getLocation();
                        // set block
                        relative.breakNaturally();
                        relative.setBlockData(data);
                        // try to move entities in the way
                        world.getNearbyEntities(relativeLocation, 0.5, 0.5, 0.5).forEach(entity -> entity.teleport(entity.getLocation().add(0, 1, 0)));
                        world.playEffect(relativeLocation, Effect.STEP_SOUND, data.getMaterial());
                    }
                }, delay * 4L);
            }
            Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                beaconLocation.setType(Material.BEACON);
                if (effects.getEffects().size() == 1 || effects.getEffects().size() == 2) {
                    Beacon beacon = (Beacon) beaconLocation.getState();
                    for (Map.Entry<PotionEffectType, Integer> entry : effects.getEffects().entrySet()) {
                        if (entry.getKey().equals(PotionEffectType.REGENERATION)) {
                            beacon.setSecondaryEffect(PotionEffectType.REGENERATION);
                            continue;
                        } else if (entry.getValue() == 2) {
                            beacon.setSecondaryEffect(entry.getKey());
                        }
                        beacon.setPrimaryEffect(entry.getKey());
                    }
                    beacon.update(true);
                }
                world.playEffect(beaconLocation.getLocation(), Effect.STEP_SOUND, Material.BEACON);
            }, (pyramid.tier - 1) * 4L);
            // needs to cancel event to prevent error in console
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    @EventHandler
    public void onBeaconUse(PlayerInteractEvent e) {
        if (!Config.effectsToggleEnabled)
            return;
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                e.getPlayer().isSneaking() && (e.useItemInHand() == Event.Result.ALLOW || e.useItemInHand() == Event.Result.DEFAULT) &&
                ItemUtils.isPortableBeacon(e.getItem())) {
            e.setUseInteractedBlock(Event.Result.DENY);
            Inventories.openInventory(e.getPlayer(), new InventoryTogglePotion(e.getItem()));
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
                    if (effects.soulboundLevel != 0 && player.getUniqueId().equals(effects.soulboundOwner)) {
                        iterator.remove();
                        List<ItemStack> items = soulboundItems.computeIfAbsent(player, key -> new ArrayList<>());
                        if (Config.enchSoulboundConsumeLevelOnDeath) {
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

    // curse of binding
    static boolean shouldApplyFunnyCurse(ItemStack stack) {
        if (!Config.enchSoulboundCurseOfBinding || !ItemUtils.isPortableBeacon(stack))
            return false;
        BeaconEffects effects = ItemUtils.getEffects(stack);
        if (effects.soulboundLevel == 0)
            return false;
        for (PotionEffectType type : effects.getEffects().keySet()) {
            if (PotionEffectUtils.isNegative(type)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropCurseItem(PlayerDropItemEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE)
            return;
        if (shouldApplyFunnyCurse(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerClickCurseItem(InventoryClickEvent e) {
        if (e.getWhoClicked().getGameMode() == GameMode.CREATIVE)
            return;
        if (e.getClickedInventory() instanceof PlayerInventory && e.getInventory().getType() != InventoryType.CRAFTING && e.getClick().isShiftClick()) {
            if (shouldApplyFunnyCurse(e.getCurrentItem())) {
                e.setCancelled(true);
            }
        } else if (!(e.getClickedInventory() instanceof PlayerInventory)) {
            if (shouldApplyFunnyCurse(e.getCursor())) {
                e.setCancelled(true);
            }
        }
    }

}
