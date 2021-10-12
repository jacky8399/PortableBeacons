package com.jacky8399.portablebeacons.inventory;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InventoryTogglePotion implements InventoryProvider {

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
        expMeta.setDisplayName(expUsageMessage.format(new Object[]{7.5 / beaconEffects.calcExpPerCycle()}));
        expStack.setItemMeta(expMeta);
    }

    private void updateItem() {
        BeaconEffects effects = ItemUtils.getEffects(stack);
        effects.setDisabledEffects(disabledEffects);
        // mildly inefficient
        ItemStack temp = ItemUtils.createStackCopyItemData(effects, stack);
        ItemMeta tempMeta = temp.getItemMeta();
        stack.setItemMeta(tempMeta);
        // all effects are disabled
        // why does this need to call setItemMeta twice
        displayStack.setItemMeta(tempMeta);
        displayStack.setType(effects.getEffects().size() == disabledEffects.size() ? Material.GLASS : Material.BEACON);
        displayStack.setItemMeta(tempMeta);

        ItemMeta expMeta = expStack.getItemMeta();
        expMeta.setDisplayName(expUsageMessage.format(new Object[]{7.5 / effects.calcExpPerCycle()}));
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

    @Override
    public void populate(Player player, InventoryAccessor inventory) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(ChatColor.BLACK.toString());
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inventory.set(4, displayStack, readOnly ? null : e -> {
                    // simple check to see if all toggleable effects have been disabled
                    if (!disabledEffects.containsAll(effects.keySet().stream()
                            .filter(type -> Config.effectsToggleEnabled &&
                                    (Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type)))
                            .collect(Collectors.toList()))) {
                        // disable all
                        effects.keySet().stream()
                                .filter(type -> checkToggleable(player, type))
                                .forEach(disabledEffects::add);
                    } else {
                        disabledEffects.removeIf(type -> checkToggleable(player, type));
                    }
                    updateItem();
                    inventory.requestRefresh(player);
                });
            } else if (i == 8 && Config.nerfExpPercentagePerCycle != 0) {
                inventory.set(8, expStack);
            } else {
                inventory.set(i, border);
            }
        }

        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = effects.entrySet().iterator();
        outer:
        for (int row = 1, rows = getRows(); row < rows; row++) {
            for (int column = 0; column < 9; column++) {
                if (!iterator.hasNext())
                    break outer;

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
                    display = ChatColor.stripColor(display);
                    display = ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + display;
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
                Consumer<InventoryClickEvent> handler = readOnly ? null : e -> {
                    // check permissions and config if they changed
                    if (!checkToggleable(player, type))
                        return;

                    if (!disabledEffects.add(type))
                        disabledEffects.remove(type);
                    updateItem();
                    inventory.requestRefresh(player);
                };
                inventory.set(row * 9 + column, stack, handler);
            }
        }
    }

    private static boolean checkToggleable(Player player, PotionEffectType type) {
        return Config.effectsToggleEnabled &&
                (Config.effectsToggleCanDisableNegativeEffects || !PotionEffectUtils.isNegative(type)) &&
                (!Config.effectsToggleFineTunePerms || player.hasPermission(
                        "portablebeacons.effect-toggle." + PotionEffectUtils.getName(type)
                ));
    }
}
