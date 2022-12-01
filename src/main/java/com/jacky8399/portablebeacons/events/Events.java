package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.inventory.InventoryTogglePotion;
import com.jacky8399.portablebeacons.utils.BeaconPyramid;
import com.jacky8399.portablebeacons.utils.BeaconUtils;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

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

    public void doItemLocationCheck() {
        if (!Config.ritualEnabled)
            return;
        for (var iterator = ritualItems.iterator(); iterator.hasNext();) {
            Item item = iterator.next();
            UUID throwerUUID = item.getThrower();
            if (throwerUUID == null) {
                iterator.remove();
                continue;
            }
            Player thrower = Bukkit.getPlayer(throwerUUID);
            if (thrower == null || !thrower.isOnline() || item.isDead()) {
                iterator.remove();
                continue;
            }

            Block blockBelow = item.getLocation().add(0, -1, 0).getBlock();
            if (blockBelow.getType() == Material.BEACON) {
                // check if activated
                Beacon tileEntity = (Beacon) blockBelow.getState();
                int tier = BeaconUtils.checkBeaconTier(tileEntity);
                if (tier == -1) {
                    continue;
                }

                Map<PotionEffectType, Integer> effects = new HashMap<>();
                PotionEffect primary = tileEntity.getPrimaryEffect();
                effects.put(primary.getType(), primary.getAmplifier() + 1);
                PotionEffect secondary = tileEntity.getSecondaryEffect();
                if (secondary != null)
                    effects.put(secondary.getType(), secondary.getAmplifier() + 1);
                BeaconPyramid pyramid = BeaconUtils.removeBeacon(thrower, blockBelow, tier, true);
                if (pyramid == null) { // failed to remove beacon
                    continue;
                }
                // play sound to complement block break effects
                item.getWorld().playSound(item.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1);

                ItemStack stack = ItemUtils.createStack(new BeaconEffects(effects));
                ItemUtils.setPyramid(stack, pyramid);
                // check split amount
                ItemStack currentStack = item.getItemStack();
                int newAmount = currentStack.getAmount() - Config.ritualItem.getAmount();
                if (newAmount < 0) {
                    continue;
                } else if (newAmount > 0) {
                    currentStack.setAmount(newAmount);
                    // ConcurrentModificationException
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, () -> {
                        Item splitItem = item.getWorld().dropItem(item.getLocation().add(0, 1, 0), currentStack);
                        splitItem.setThrower(throwerUUID);
                        splitItem.setVelocity(new Vector(0, 0, 0));
                        ritualItems.add(splitItem);
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

    public static HashSet<Location> ritualTempBlocks = new HashSet<>();

    public Set<Item> ritualItems = new LinkedHashSet<>();
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDropItem(PlayerDropItemEvent e) {
        if (!Config.ritualEnabled)
            return;
        ItemStack is = e.getItemDrop().getItemStack();
        if (is.isSimilar(Config.ritualItem) && is.getAmount() >= Config.ritualItem.getAmount()) {
            ritualItems.add(e.getItemDrop());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent e) {
        ritualItems.remove(e.getEntity());
    }

    @EventHandler
    public void onItemMerge(ItemMergeEvent e) {
        if (ritualItems.contains(e.getEntity()) || ritualItems.contains(e.getTarget())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        List<Block> blocks = e.getBlocks();
        for (Block block : blocks) {
            if (ritualTempBlocks.contains(block.getLocation())) {
                e.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        List<Block> blocks = e.getBlocks();
        for (Block block : blocks) {
            if (ritualTempBlocks.contains(block.getLocation())) {
                e.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onTempBlockBroken(BlockBreakEvent e) {
        if (ritualTempBlocks.contains(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconBroken(BlockDropItemEvent e) {
        if (!Config.ritualEnabled || Config.ritualItem.getType() != Material.AIR)
            return;
        if (!Config.pickupEnabled)
            return;
        BlockState state = e.getBlockState();
        if (state instanceof Beacon beacon) {
            int tier = BeaconUtils.checkBeaconTier(beacon);
            if (tier == -1)
                return;

            // check silk touch
            if (Config.pickupRequireSilkTouch) {
                ItemStack stack = e.getPlayer().getInventory().getItemInMainHand();
                if (!stack.containsEnchantment(Enchantment.SILK_TOUCH))
                    return;
            }

            Map<PotionEffectType, Integer> effects = new HashMap<>();
            PotionEffect primary = beacon.getPrimaryEffect();
            effects.put(primary.getType(), primary.getAmplifier() + 1);
            PotionEffect secondary = beacon.getSecondaryEffect();
            if (secondary != null)
                effects.put(secondary.getType(), secondary.getAmplifier() + 1);
            BeaconPyramid pyramid = BeaconUtils.removeBeacon(e.getPlayer(), e.getBlock(), tier, false);
            if (pyramid == null) {
                return;
            }

            BeaconEffects beaconEffects = new BeaconEffects(effects);
            for (Item item : e.getItems()) {
                if (item.getItemStack().getType() == Material.BEACON) {
                    ItemStack stack = ItemUtils.createStack(beaconEffects);
                    ItemUtils.setPyramid(stack, pyramid);
                    item.setItemStack(stack);
                    break;
                }
            }
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

    private static final float HARDNESS_THRESHOLD = Material.OBSIDIAN.getHardness();
    @EventHandler(ignoreCancelled = true)
    public void onPlacePyramid(BlockPlaceEvent e) {
        ItemStack stack = e.getItemInHand();
        if (!ItemUtils.isPortableBeacon(stack)) {
            return; // ignore non-beacon items
        }
        e.setCancelled(true);
        if (!ItemUtils.isPyramid(stack)) {
            return; // can't be placed
        }
        Player player = e.getPlayer();
        EquipmentSlot hand = e.getHand();
        Block placeLocation = e.getBlockPlaced();
        Block placedAgainst = e.getBlockAgainst();
        BeaconEffects effects = ItemUtils.getEffects(stack);
        BeaconPyramid pyramid = ItemUtils.getPyramid(stack);
        Block beaconLocation = placeLocation.getRelative(0, pyramid.tier, 0);
        // check block placement first
        if (checkBlockPlaceEventFail(player, hand, placedAgainst, beaconLocation, Material.BEACON.createBlockData())) {
            return;
        }
        for (var beaconBase : pyramid.beaconBaseBlocks) {
            var blockData = beaconBase.data();
            // only place beacon base blocks!
            if (!Tag.BEACON_BASE_BLOCKS.isTagged(blockData.getMaterial())) {
                continue; // don't check and filter later
            }
            Block relative = beaconBase.getLocation(beaconLocation);
            float hardness = relative.getType().getHardness();
            if (hardness < 0 || hardness >= HARDNESS_THRESHOLD) {
                return; // don't break unbreakable or blocks like obsidian
            }
            if (checkBlockPlaceEventFail(player, hand, placedAgainst, relative, blockData)) {
                return;
            }
        }
        // then update blocks
        World world = beaconLocation.getWorld();
        // group by Y level to stagger creation
        Map<Integer, List<BeaconPyramid.BeaconBase>> pyramidByLayer = pyramid.beaconBaseBlocks.stream()
                .filter(base -> Tag.BEACON_BASE_BLOCKS.isTagged(base.data().getMaterial())) // only place beacon base blocks
                .collect(groupingBy(BeaconPyramid.BeaconBase::relativeY, toList()));
        for (var layerEntry : pyramidByLayer.entrySet()) {
            int delay = pyramid.tier + layerEntry.getKey(); // key is negative
            Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                for (var beaconBlock : layerEntry.getValue()) {
                    BlockData data = beaconBlock.data();
                    Block relative = beaconBlock.getLocation(beaconLocation);
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

    @EventHandler
    public void onBeaconUse(PlayerInteractEvent e) {
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                ItemUtils.isPortableBeacon(e.getItem())) {
            Player player = e.getPlayer();
            if (player.isSneaking() && e.useItemInHand() != Event.Result.DENY) {
                e.setUseInteractedBlock(Event.Result.DENY);
                e.setUseItemInHand(Event.Result.ALLOW);
                ItemStack stack = e.getItem();
                BeaconEffects effects = ItemUtils.getEffects(stack);
                boolean isReadOnly = !Config.effectsToggleEnabled ||
                        (Config.enchSoulboundOwnerUsageOnly && !effects.isOwner(player));
                Inventories.openInventory(player, new InventoryTogglePotion(stack, isReadOnly));
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
