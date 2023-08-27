package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BEACON)
            return null;
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static void setEffects(ItemStack stack, BeaconEffects effects) {
        ItemMeta meta = stack.getItemMeta();
        setEffects(meta, effects);
        stack.setItemMeta(meta);
    }

    public static void setEffects(ItemMeta meta, BeaconEffects effects) {
        meta.getPersistentDataContainer().set(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE, effects);
    }

    public static boolean isPyramid(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static BeaconPyramid getPyramid(ItemStack stack) {
        return stack.getItemMeta().getPersistentDataContainer().get(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE);
    }

    public static void setPyramid(ItemStack stack, BeaconPyramid pyramid) {
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(BeaconPyramid.BeaconPyramidDataType.STORAGE_KEY, BeaconPyramid.BeaconPyramidDataType.STORAGE_TYPE, pyramid);
        stack.setItemMeta(meta);
    }

    @NotNull
    public static ItemStack createStack(@Nullable Player player, @NotNull BeaconEffects effects) {
        return createMetaCopyItemData(player, effects, new ItemStack(Material.BEACON));
    }

    @NotNull
    public static ItemStack createMetaCopyItemData(@Nullable Player player, @NotNull BeaconEffects effects, @NotNull ItemStack stack) {
        ItemMeta meta = createMetaCopyItemData(player, effects, Objects.requireNonNull(stack.getItemMeta()));
        ItemStack newStack = new ItemStack(Material.BEACON);
        newStack.setItemMeta(meta);
        newStack.setAmount(stack.getAmount());
        return newStack;
    }

    // preserves item name and things
    @NotNull
    public static ItemMeta createMetaCopyItemData(@Nullable Player player, @NotNull BeaconEffects effects, @NotNull ItemMeta meta) {
        boolean hideEffects = !meta.getItemFlags().contains(ItemFlag.HIDE_POTION_EFFECTS); // can't use HIDE_ENCHANTS

        // copy lore, enchants and set effects
        if (!meta.hasDisplayName() && !Config.itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + Config.itemName);
        }
        if (Config.itemCustomModelData > -1) {
            meta.setCustomModelData(Config.itemCustomModelData);
        }

        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> effectsLore = effects.toLore(hideEffects, hideEffects);

        if (!Config.itemLore.isEmpty()) {
            List<String> lore = new ArrayList<>(effectsLore.size() + Config.itemLore.size() + 1);
            lore.addAll(effectsLore);
            lore.add("");
            if (Config.placeholderApi) {
                try {
                    lore.addAll(PlaceholderAPI.setPlaceholders(player, Config.itemLore));
                } catch (Exception ex) {
                    PortableBeacons.LOGGER.log(Level.SEVERE, "Failed to parse PlaceholderAPI placeholders for player " + player, ex);
                }
            } else {
                lore.addAll(Config.itemLore);
            }
            meta.setLore(lore);
        } else {
            meta.setLore(effectsLore);
        }
        setEffects(meta, effects);
        return meta;
    }

    /*
        Spigot stores the display name and lore as JSON strings internally, but there is no API to set them to a JSON text.
        https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaItem.java?at=64c15270e76475e68b2167d4bfba162a4a827fe0#267
     */
    private static final VarHandle ITEM_META_DISPLAY_NAME;
    private static final VarHandle ITEM_META_LORE;
    static {
        Class<? extends ItemMeta> clazz = Objects.requireNonNull(Bukkit.getItemFactory().getItemMeta(Material.STONE)).getClass();
        VarHandle itemMetaDisplayName = null;
        VarHandle itemMetaLore = null;
        try {
            var privateLookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
            itemMetaDisplayName = privateLookup.findVarHandle(clazz, "displayName", String.class);
            itemMetaLore = privateLookup.findVarHandle(clazz, "lore", List.class);
        } catch (ReflectiveOperationException ex) {
            PortableBeacons.INSTANCE.logger.log(Level.WARNING, "Failed to find displayName/lore field in " + clazz.getName(), ex);
        }
        ITEM_META_DISPLAY_NAME = itemMetaDisplayName;
        ITEM_META_LORE = itemMetaLore;
    }
    public static void setDisplayName(ItemMeta meta, BaseComponent @Nullable... components) {
        if (components == null) {
            meta.setDisplayName(null);
            return;
        }

        if (ITEM_META_DISPLAY_NAME == null) {
            // failed to find displayName field
            meta.setDisplayName(BaseComponent.toLegacyText(components));
        } else {
            String json = ComponentSerializer.toString(components);
            ITEM_META_DISPLAY_NAME.set(meta, json);
        }
    }

    public static void setLore(ItemMeta meta, @Nullable List<BaseComponent> lore) {
        if (lore == null) {
            meta.setLore(null);
            return;
        }

        if (ITEM_META_LORE == null) {
            meta.setLore(lore.stream().map(component -> component.toLegacyText()).toList());
        } else {
            List<String> newLore = lore.stream().map(ComponentSerializer::toString).toList();
            ITEM_META_LORE.set(meta, newLore);
        }
    }

    public static void setRawLore(ItemMeta meta, @Nullable List<String> lore) {
        if (ITEM_META_LORE == null) {
            meta.setLore(lore);
        } else {
            ITEM_META_LORE.set(meta, lore);
        }
    }

    @Nullable
    public static List<String> getRawLore(ItemMeta meta) {
        return ITEM_META_LORE != null ? (List<String>) ITEM_META_LORE.get(meta) : meta.getLore();
    }

    // reject quotation marks to avoid parsing JSON
    private static final Pattern PLACEHOLDER = Pattern.compile("(\\s)?\\{([^\"]+?)}");
    public static String replacePlaceholders(String input, ContextLevel level, Map<String, Context> contexts) {
        if (input.indexOf('{') == -1) {
            return input + level.doReplacement();
        }
        return PLACEHOLDER.matcher(input).replaceAll(match -> {
            String[] args = match.group(2).split("\\|");
            String contextName = args[0];
            Context context = contextName.equals("level") ? level : contexts.get(contextName);
            if (context == null) {
                // ignore
                return match.group();
            }
            args = Arrays.copyOfRange(args, 1, args.length);
            boolean prependSpace = match.group(1) != null && !context.shouldRemovePrecedingSpace(args);
            String replacement = context.doReplacement(args);
            if (replacement == null) {
                // ignore
                return match.group();
            }
            return (prependSpace ? " " : "") + replacement;
        });
    }

    public static String replacePlaceholders(String input, ContextLevel level) {
        return replacePlaceholders(input, level, Map.of());
    }

    public static String replacePlaceholders(String input, int level) {
        return replacePlaceholders(input, new ContextLevel(level), Map.of());
    }

    public interface Context {
        default boolean shouldRemovePrecedingSpace(String... args) {
            return false;
        }

        String doReplacement(String... args);
    }

    public record ContextLevel(int level) implements Context {

        @Override
        public boolean shouldRemovePrecedingSpace(String... args) {
            return args.length == 0; // {level}
        }

        @Override
        public String doReplacement(String... args) {
            return args.length == 1 && "number".equals(args[0]) ?
                    Integer.toString(level) :
                    PotionEffectUtils.toRomanNumeral(level);
        }
    }

    /*
        {...}
        {...|<reduction/multiplier>}
        {...|<reduction/multiplier>|<number/percentage>}
     */
    public static class ContextExpReduction implements Context {
        public final double expMultiplier;
        public ContextExpReduction(double expMultiplier) {
            this.expMultiplier = expMultiplier;
        }

        public ContextExpReduction(int level) {
            this(Math.max(0, 1 - level * Config.enchExpReductionReductionPerLevel));
        }

        @Override
        public String doReplacement(String... args) {
            boolean isMultiplier = false;
            boolean isPercentage = true;

            if (args.length >= 1) {
                isMultiplier = "multiplier".equals(args[0]);
            }
            if (args.length == 2) {
                isPercentage = "percentage".equals(args[1]);
            }

            double actualMultiplier = isMultiplier ? expMultiplier : 1 - expMultiplier;
            return isPercentage ? String.format("%.2f%%", actualMultiplier * 100) : String.format("%.2f", actualMultiplier);
        }
    }

    /*
        {...}
        {...|<name/uuid>}
        {...|name|<fallback>}
     */
    public static class ContextUUID implements Context {
        @Nullable
        public final UUID uuid;
        @Nullable
        public final String playerName;
        public final String fallback;
        public ContextUUID(@Nullable UUID uuid, @Nullable String playerName, String fallback) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.fallback = fallback;
        }

        public ContextUUID(UUID uuid, String fallback) {
            this(uuid, uuid != null ? Bukkit.getOfflinePlayer(uuid).getName() : null, fallback);
        }

        @Override
        public String doReplacement(String... args) {
            String fallback = args.length == 2 && "name".equals(args[0]) ? args[1] : this.fallback;
            if (uuid == null)
                return fallback;
            if (args.length == 0 || "name".equals(args[0])) {
                return playerName != null ? playerName : fallback;
            } else if (args.length == 1 && "uuid".equals(args[0])) {
                return uuid.toString();
            }
            return playerName;
        }
    }
}
