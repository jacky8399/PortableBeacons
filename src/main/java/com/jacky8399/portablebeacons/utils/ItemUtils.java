package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ItemUtils {
    public static boolean isPortableBeacon(ItemStack stack) {
        return stack != null && stack.getType() == Material.BEACON && stack.hasItemMeta() &&
                stack.getItemMeta().getPersistentDataContainer().has(BeaconEffects.BeaconEffectsDataType.STORAGE_KEY, BeaconEffects.BeaconEffectsDataType.STORAGE_TYPE);
    }

    public static BeaconEffects getEffects(ItemStack stack) {
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

    public static ItemMeta createMeta(BeaconEffects effects) {
        ItemMeta meta = Objects.requireNonNull(Bukkit.getItemFactory().getItemMeta(Material.BEACON));
        if (!Config.itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + Config.itemName);
        }
        if (Config.itemCustomModelData > -1) {
            meta.setCustomModelData(Config.itemCustomModelData);
        }
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<String> lore = new ArrayList<>(effects.toLore());
        if (Config.itemLore.size() != 0) {
            lore.add("");
            lore.addAll(Config.itemLore);
        }
        meta.setLore(lore);
        setEffects(meta, effects);
        return meta;
    }

    public static ItemStack createStack(BeaconEffects effects) {
        ItemStack stack = new ItemStack(Material.BEACON);
        ItemMeta meta = createMeta(effects);
        stack.setItemMeta(meta);
        return stack;
    }

    // preserves item name and things
    public static ItemStack createStackCopyItemData(BeaconEffects effects, ItemStack oldIs) {
        ItemMeta newMeta = createMeta(effects);

        ItemMeta oldMeta = oldIs.getItemMeta(), actualMeta = oldMeta.clone();

        // copy lore, enchants and set effects
        if (newMeta.hasLore())
            actualMeta.setLore(newMeta.getLore());
        for (Map.Entry<Enchantment, Integer> enchants : newMeta.getEnchants().entrySet()) {
            actualMeta.addEnchant(enchants.getKey(), enchants.getValue(), true);
        }
        actualMeta.addItemFlags(newMeta.getItemFlags().toArray(new ItemFlag[0]));
        if (newMeta.hasCustomModelData())
            actualMeta.setCustomModelData(newMeta.getCustomModelData());
        setEffects(actualMeta, effects);

        ItemStack newIs = new ItemStack(Material.BEACON);
        newIs.setItemMeta(actualMeta);
        newIs.setAmount(oldIs.getAmount());
        return newIs;
    }

    private static int getCost(BeaconEffects effects) {
        return effects.getEffects().values().stream().mapToInt(level -> 1 << level).sum();
    }

    public static int calculateCombinationCost(ItemStack is1, ItemStack is2) {
        if (!isPortableBeacon(is1))
            return 0;
        if (isPortableBeacon(is2)) {
            BeaconEffects e1 = getEffects(is1), e2 = getEffects(is2);
            return getCost(e1) + getCost(e2);
        } else if (is2 != null && is2.getType() == Material.ENCHANTED_BOOK && is2.hasItemMeta()) {
            BeaconEffects effects = getEffects(is1);
            Map<Enchantment, Integer> enchants = ((EnchantmentStorageMeta) is2.getItemMeta()).getStoredEnchants();
            int numOfEnchantsApplicable = 0;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null && enchants.containsKey(Config.enchSoulboundEnchantment))
                numOfEnchantsApplicable++;
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null && enchants.containsKey(Config.enchExpReductionEnchantment))
                numOfEnchantsApplicable++;

            return getCost(effects) * numOfEnchantsApplicable;
        }
        return 0;
    }

    public static ItemStack createMessageStack(String message) {
        ItemStack messageStack = new ItemStack(Material.BARRIER);
        ItemMeta meta = messageStack.getItemMeta();
        meta.setDisplayName(ChatColor.RED + message);
        messageStack.setItemMeta(meta);
        return messageStack;
    }

    public static ItemStack combineStack(Player player, ItemStack is1, ItemStack is2, boolean visual) {
        if (!isPortableBeacon(is1) || is2 == null)
            return null;
        BeaconEffects original = getEffects(is1);
        // soulbound owner check
        if (Config.enchSoulboundOwnerUsageOnly && !original.isOwner(player)) {
            return visual ? createMessageStack("You do not own the original item") : null;
        }

        if (isPortableBeacon(is2)) {
            BeaconEffects e2 = getEffects(is2);
            HashMap<PotionEffectType, Integer> potions = new HashMap<>(original.getEffects());
            BinaryOperator<Integer> algorithm = Config.anvilCombinationCombineEffectsAdditively ?
                    Integer::sum : ItemUtils::anvilAlgorithm;
            e2.getEffects().forEach((pot, count) -> potions.merge(pot, count, algorithm));
            // owner check for other beacon
            if (Config.enchSoulboundOwnerUsageOnly && !e2.isOwner(player)) {
                return visual ? createMessageStack("You do not own the portable beacons") : null;
            }

            // check max effects count / overpowered effects
            if (potions.size() > Config.anvilCombinationMaxEffects ||
                    potions.entrySet().stream().anyMatch(entry -> {
                        Config.PotionEffectInfo info = Config.getInfo(entry.getKey());
                        return entry.getValue() > info.getMaxAmplifier();
                    })) {
                return visual ? createMessageStack("Overpowered effects") : null;
            }

            BeaconEffects newEffects = new BeaconEffects(potions);
            // copy additional attributes
            newEffects.setDisabledEffects(original.getDisabledEffects());
            newEffects.expReductionLevel = Math.max(original.expReductionLevel, e2.expReductionLevel);
            newEffects.soulboundLevel = Math.max(original.soulboundLevel, e2.soulboundLevel);
            newEffects.soulboundOwner = original.soulboundOwner;

            return createStack(newEffects);
        } else if (is2.getType() == Material.ENCHANTED_BOOK && is2.hasItemMeta()) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) is2.getItemMeta();
            Map<Enchantment, Integer> enchants = meta.getStoredEnchants();
            boolean allowCombination = false;
            if (Config.enchSoulboundEnabled && Config.enchSoulboundEnchantment != null && enchants.containsKey(Config.enchSoulboundEnchantment)) {
                allowCombination = true;
                if (++original.soulboundLevel > Config.enchSoulboundMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Soulbound") : null;
                if (original.soulboundOwner == null || original.soulboundOwner.equals(player.getUniqueId()))
                    original.soulboundOwner = player.getUniqueId();
            }
            if (Config.enchExpReductionEnabled && Config.enchExpReductionEnchantment != null && enchants.containsKey(Config.enchExpReductionEnchantment)) {
                allowCombination = true;
                if (++original.expReductionLevel > Config.enchExpReductionMaxLevel) // level check
                    return visual ? createMessageStack("Overpowered Experience Efficiency") : null;
            }

            return allowCombination ?
                    createStackCopyItemData(original, is1) :
                    visual ? createMessageStack("Incompatible enchantments") : null;
        }
        return visual ? createMessageStack("Invalid combination") : null;
    }

    private static int anvilAlgorithm(int s1, int s2) {
        if (s1 == s2) {
            return s1 + 1;
        } else {
            return Math.max(s1, s2);
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("(\\s)?\\{(.+?)}");
    public static String replacePlaceholders(@Nullable Player player, String input, ContextLevel level, Map<String, Context> contexts) {
        if (input.indexOf('{') == -1) {
            return input + level.doReplacement();
        }
        Matcher matcher = PLACEHOLDER.matcher(input);
        // thanks, Java
        // ^ thanks, LanguageTools
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String[] args = matcher.group(2).split("\\|");
            String contextName = args[0];
            Context context = contextName.equals("level") ? level : contexts.get(contextName);
            if (context == null) {
                // ignore
                matcher.appendReplacement(buffer, "[UNKNOWN CONTEXT " + contextName + "]");
                continue;
            }
            args = Arrays.copyOfRange(args, 1, args.length);
            boolean copySpace = matcher.group(1) != null && !context.shouldRemovePrecedingSpace(args);
            String replacement = context.doReplacement(args);
            if (replacement == null) {
                // ignore
                matcher.appendReplacement(buffer, matcher.group());
                continue;
            }
            matcher.appendReplacement(buffer, (copySpace ? " " : "") + replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String replacePlaceholders(@Nullable Player player, String input, ContextLevel level) {
        return replacePlaceholders(player, input, level, Collections.emptyMap());
    }

    public static String replacePlaceholders(@Nullable Player player, String input, int level) {
        return replacePlaceholders(player, input, new ContextLevel(level), Collections.emptyMap());
    }

    public static abstract class Context {
        public boolean shouldRemovePrecedingSpace(String... args) {
            return false;
        }

        public abstract String doReplacement(String... args);
    }

    public static class ContextLevel extends Context {
        public final int level;
        public ContextLevel(int level) {
            this.level = level;
        }

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
    public static class ContextExpReduction extends Context {
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
    public static class ContextUUID extends Context {
        @Nullable
        UUID uuid;
        @Nullable
        String playerName;
        String fallback;
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
