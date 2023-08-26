package com.jacky8399.portablebeacons.inventory;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;

public class InventoryTogglePotion implements InventoryProvider {
    public static final ItemStack BORDER = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    static {
        ItemMeta borderMeta = BORDER.getItemMeta();
        borderMeta.setDisplayName(ChatColor.BLACK.toString());
        BORDER.setItemMeta(borderMeta);
    }
    private final boolean readOnly;
    private ItemStack stack;
    private ItemStack displayStack;
    private MessageFormat expUsageMessage = new MessageFormat(Config.effectsToggleExpUsageMessage);
    private ItemStack expStack;
    private Map<PotionEffectType, Integer> effects;
    private HashSet<PotionEffectType> disabledEffects;

    public InventoryTogglePotion(ItemStack stack, boolean readOnly) {
        this.readOnly = readOnly;
        if (!ItemUtils.isPortableBeacon(stack)) {
            throw new IllegalArgumentException("stack is not beacon");
        }
        this.stack = stack;
        BeaconEffects beaconEffects = ItemUtils.getEffects(stack);
        effects = new TreeMap<>(PotionEffectUtils.POTION_COMPARATOR); // to maintain consistent order
        effects.putAll(beaconEffects.getEffects());
        disabledEffects = new HashSet<>(beaconEffects.getDisabledEffects());

        // display stack
        displayStack = stack.clone();
        displayStack.setType(beaconEffects.getEffects().size() == disabledEffects.size() ? Material.GLASS : Material.BEACON);

        expStack = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expStack.getItemMeta();
        expMeta.setDisplayName(expUsageMessage.format(new Object[]{60 / beaconEffects.calcExpPerMinute()}));
        expStack.setItemMeta(expMeta);
    }

    private void updateItem(Player player) {
        BeaconEffects effects = ItemUtils.getEffects(stack);
        effects.setEffects(this.effects);
        effects.setDisabledEffects(disabledEffects);
        // mildly inefficient
        ItemStack temp = ItemUtils.createStackCopyItemData(player, effects, stack);
        ItemMeta tempMeta = temp.getItemMeta();
        stack.setItemMeta(tempMeta);
        // all effects are disabled
        // why does this need to call setItemMeta twice
        displayStack.setType(effects.getEffects().size() == disabledEffects.size() ? Material.GLASS : Material.BEACON);
        displayStack.setItemMeta(Bukkit.getItemFactory().asMetaFor(tempMeta, displayStack));

        ItemMeta expMeta = expStack.getItemMeta();
        expMeta.setDisplayName(expUsageMessage.format(new Object[]{60 / effects.calcExpPerMinute()}));
        expStack.setItemMeta(expMeta);
    }

    @Override
    public String getTitle(Player player) {
        return Config.effectsToggleTitle;
    }

    @Override
    public int getRows() {
        return Math.min(6, effects.size() / 9 + 3);
    }

    private static final String ADD_POTION_PERM = "portablebeacons.command.item.add";
    private static final String REMOVE_POTION_PERM = "portablebeacons.command.item.remove";


    @Override
    public void populate(Player player, InventoryAccessor inventory) {
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inventory.set(4, displayStack, readOnly ? InventoryTogglePotion::playCantEdit : e -> {
                    if (!Config.effectsToggleEnabled)
                        return;
                    // simple check to see if all toggleable effects have been disabled
                    if (effects.keySet().stream()
                            .filter(type -> Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type))
                            .anyMatch(effect -> !disabledEffects.contains(effect))) {
                        // disable all
                        effects.keySet().stream()
                                .filter(type -> checkToggleable(player, type))
                                .forEach(disabledEffects::add);
                    } else {
                        disabledEffects.removeIf(type -> checkToggleable(player, type));
                    }
                    playEdit(e);
                    updateItem(player);
                    inventory.requestRefresh(player);
                });
            } else if (i == 0 && (player.hasPermission(ADD_POTION_PERM) || player.hasPermission(REMOVE_POTION_PERM))) {
                // edit potion effects
                var stack = new ItemStack(Material.WRITABLE_BOOK);
                var meta = stack.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + "Edit potion effects (Admin)");
                var lore = new ArrayList<String>();
                if (player.hasPermission(ADD_POTION_PERM))
                    lore.add(ChatColor.GREEN + "Add potion effects by putting potions here");
                if (player.hasPermission(REMOVE_POTION_PERM))
                    lore.add(ChatColor.RED + "Remove potion effects by pressing the drop key in the UI");
                meta.setLore(lore);
                stack.setItemMeta(meta);
                inventory.set(0, stack, addPotionEffects(inventory));
            } else if (i == 8 && Config.nerfExpLevelsPerMinute != 0) {
                inventory.set(8, expStack);
            } else {
                inventory.set(i, BORDER);
            }
        }

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = effects.entrySet().iterator();
        for (int row = 1, rows = getRows(); row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = row * 9 + column;
                if (!iterator.hasNext()) {
                    inventory.set(slot, null);
                    continue;
                }

                Map.Entry<PotionEffectType, Integer> entry = iterator.next();
                PotionEffectType type = entry.getKey();
                int level = entry.getValue();
                String display = PotionEffectUtils.getDisplayName(type, level);
                ItemStack stack;
                ItemMeta meta;
                if (disabledEffects.contains(type)) {
                    stack = new ItemStack(Material.GLASS_BOTTLE);
                    meta = stack.getItemMeta();
                    // strip colour and add strikethrough
                    display = ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + ChatColor.stripColor(display);
                } else {
                    stack = new ItemStack(Material.POTION);
                    meta = stack.getItemMeta();
                    PotionMeta potionMeta = (PotionMeta) meta;
                    potionMeta.setColor(type.getColor());
                    potionMeta.setBasePotionData(new PotionData(PotionType.LUCK)); // to make the potion glow
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                meta.setDisplayName(display);
                stack.setItemMeta(meta);
                inventory.set(slot, stack, togglePotionEffect(inventory, type));
            }
        }
    }

    private Consumer<InventoryClickEvent> togglePotionEffect(InventoryAccessor inventory, PotionEffectType type) {
        return e -> {
            if (readOnly) {
                playCantEdit(e);
                return;
            }

            Player clicked = (Player) e.getWhoClicked();
            if (e.getClick() != ClickType.DROP && e.getClick() != ClickType.CONTROL_DROP) {
                // check permissions and config if they changed
                if (!checkToggleable(clicked, type)) {
                    playCantEdit(e);
                    return;
                }
                if (!disabledEffects.add(type))
                    disabledEffects.remove(type);
            } else {
                if (!clicked.hasPermission(REMOVE_POTION_PERM)) {
                    playCantEdit(e);
                    return;
                }
                effects.remove(type);
                disabledEffects.remove(type);
            }
            playEdit(e);
            updateItem(clicked);
            inventory.requestRefresh(clicked);
        };
    }

    private Consumer<InventoryClickEvent> addPotionEffects(InventoryAccessor inventory) {
        return e -> {
            Player clicked = (Player) e.getWhoClicked();
            if (!clicked.hasPermission(ADD_POTION_PERM))
                return;
            var cursor = e.getCursor();
            if (cursor == null || !(cursor.getItemMeta() instanceof PotionMeta potionMeta))
                return;
            var basePotion = potionMeta.getBasePotionData();
            if (basePotion.getType().getEffectType() != null) {
                PotionEffectType potion = basePotion.getType() == PotionType.TURTLE_MASTER ?
                        PotionEffectType.DAMAGE_RESISTANCE :
                        basePotion.getType().getEffectType();
                int amplifier = basePotion.isUpgraded() ? 2 : 1;
                effects.merge(potion, amplifier, Integer::sum);
            }
            if (potionMeta.hasCustomEffects()) {
                for (var potionEffect : potionMeta.getCustomEffects()) {
                    effects.merge(potionEffect.getType(), potionEffect.getAmplifier(), Integer::sum);
                }
            }
            playEdit(e);
            updateItem(clicked);
            inventory.requestRefresh(clicked);
        };
    }

    private static final Sound EDIT_SOUND = Sound.BLOCK_NOTE_BLOCK_BASS;
    private static final Sound CANT_EDIT_SOUND = Sound.ENTITY_VILLAGER_NO;
    private static void playCantEdit(InventoryClickEvent e) {
        ((Player) e.getWhoClicked()).playSound(e.getWhoClicked(), CANT_EDIT_SOUND, SoundCategory.PLAYERS, 0.5f, 1);
    }

    private static void playEdit(InventoryClickEvent e) {
        ((Player) e.getWhoClicked()).playSound(e.getWhoClicked(), EDIT_SOUND, SoundCategory.PLAYERS, 0.5f, 1);
    }

    private static boolean checkToggleable(Player player, PotionEffectType type) {
        return Config.effectsToggleEnabled &&
                (Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type)) &&
                (!Config.effectsToggleFineTunePerms ||
                        player.hasPermission("portablebeacons.effect-toggle." + PotionEffectUtils.getName(type)));
    }
}
