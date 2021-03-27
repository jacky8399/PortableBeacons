package com.jacky8399.portablebeacons.events;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.inventory.InventoryTogglePotion;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Predicate;

public class Events implements Listener {
    public static void registerEvents() {
        PortableBeacons plugin = PortableBeacons.INSTANCE;
        Bukkit.getPluginManager().registerEvents(new Events(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new ReminderOutline(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new AnvilRecipe(), plugin);
    }

    public static Events INSTANCE;
    public Events(PortableBeacons plugin) {
        INSTANCE = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::doItemLocationCheck, 0, 20);
    }

    public void doItemLocationCheck() {
        if (!Config.ritualEnabled || ritualItems.size() == 0)
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

                Map<PotionEffectType, Integer> effects = new HashMap<>();
                PotionEffect primary = tileEntity.getPrimaryEffect();
                effects.put(primary.getType(), primary.getAmplifier() + 1);
                PotionEffect secondary = tileEntity.getSecondaryEffect();
                int beaconTier = tileEntity.getTier();
                int requiredTier = Math.max(PotionEffectUtils.getRequiredTier(primary), PotionEffectUtils.getRequiredTier(secondary));
                if (beaconTier < requiredTier || requiredTier == -1)
                    continue;
                if (secondary != null)
                    effects.put(secondary.getType(), secondary.getAmplifier() + 1);
                if (!removeBeacon(thrower, blockBelow, requiredTier)) {
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
                    // ConcurrentModificationException
                    Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, ()->{
                        Item splitItem = item.getWorld().dropItem(item.getLocation(), currentStack);
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

    // the beacon will definitely be removed if this method returns true
    public boolean removeBeacon(Player player, Block block, int tier) {
//        HashMap<Integer, List<Block>> blocksToBreak = new HashMap<>();
        Block[][] blocksToBreak = new Block[tier + 1][];
//        ArrayList<Block> blocksToBreak = new ArrayList<>();
//        blocksToBreak.add(block);
        // beacon block

        blocksToBreak[0] = new Block[] {block};
        if (block.getType() != Material.BEACON)
            return false;
        else if (checkBlockEventFail(player, block))
            return false;

        for (int currentTier = 1; currentTier <= tier; currentTier++) {
            int i = 0;
            Block[] blockz = new Block[(int) Math.pow(currentTier * 2 + 1, 2)];
            for (int x = -currentTier; x <= currentTier; x++) {
                for (int z = -currentTier; z <= currentTier; z++) {
                    Block offset = block.getRelative(x, -currentTier, z);
                    // validate block
                    if (!Tag.BEACON_BASE_BLOCKS.isTagged(offset.getType()))
                        return false;
                    else if (checkBlockEventFail(player, offset))
                        return false;
                    blockz[i++] = offset;
                }
            }
            blocksToBreak[currentTier] = blockz;
        }
        World world = block.getWorld();
        for (int i = 0; i < blocksToBreak.length; i++) {
            Block[] blocks = blocksToBreak[i];
            for (Block b : blocks) {
                b.setType(Material.GLASS); // to prevent duping
            }
            // don't break that many blocks at once
            Bukkit.getScheduler().runTaskLater(PortableBeacons.INSTANCE, () -> {
                for (Block b : blocks) {
                    Location location = b.getLocation();
                    world.playEffect(location, Effect.STEP_SOUND, b.getType());
                    world.spawnParticle(Particle.EXPLOSION_LARGE, location.add(Math.random(), Math.random(), Math.random()), 1);
                    b.setType(Material.AIR);
                }
            }, (i + 1) * 10L);
        }
        return true;
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
    public void onPlaceBlock(BlockPlaceEvent e) {
        if (ItemUtils.isPortableBeacon(e.getItemInHand())) {
            e.setCancelled(true); // don't place the beacon
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconUse(PlayerInteractEvent e) {
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) &&
                e.getPlayer().isSneaking() && (e.useItemInHand() == Event.Result.ALLOW || e.useItemInHand() == Event.Result.DEFAULT) &&
                ItemUtils.isPortableBeacon(e.getItem())) {
            e.setUseItemInHand(Event.Result.DENY);
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
