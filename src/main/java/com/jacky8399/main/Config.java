package com.jacky8399.main;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Config {

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        ritualItem = config.getItemStack("item-used",
                config.getItemStack("item_used",  new ItemStack(Material.NETHER_STAR, 32))); // migrate old data

        itemCustomVersion = config.getString("item-custom-version-do-not-edit");

        itemName = translateColor(config.getString("beacon-item.name", ""));
        itemLore = config.getStringList("beacon-item.lore").stream().map(Config::translateColor).collect(Collectors.toList());
        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        itemCreationReminderEnabled = config.getBoolean("beacon-item.creation-reminder.enabled");
        itemCreationReminderMessage = translateColor(config.getString("beacon-item.creation-reminder.message"));
        itemCreationReminderRadius = config.getDouble("beacon-item.creation-reminder.radius");
        itemCreationReminderDisableIfAlreadyOwnBeaconItem = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        customEnchantExpReductionEnchantment = Enchantment.getByKey(NamespacedKey.fromString(config.getString("beacon-item.custom-enchantments.exp-reduction.enchantment")));
        customEnchantExpReductionEnabled = config.getBoolean("beacon-item.custom-enchantments.exp-reduction.enabled");
        customEnchantExpReductionMaxLevel = config.getInt("beacon-item.custom-enchantments.exp-reduction.max-level");
        customEnchantExpReductionName = translateColor(config.getString("beacon-item.custom-enchantments.exp-reduction.name"));
        customEnchantExpReductionReductionPerLevel = config.getDouble("beacon-item.custom-enchantments.exp-reduction.reduction-per-level");

        customEnchantSoulboundEnchantment = Enchantment.getByKey(NamespacedKey.fromString(config.getString("beacon-item.custom-enchantments.soulbound.enchantment")));
        customEnchantSoulboundEnabled = config.getBoolean("beacon-item.custom-enchantments.soulbound.enabled");
        customEnchantSoulboundMaxLevel = config.getInt("beacon-item.custom-enchantments.soulbound.max-level");
        customEnchantSoulboundName = translateColor(config.getString("beacon-item.custom-enchantments.soulbound.name"));
        customEnchantSoulboundOwnerUsageOnly = config.getBoolean("beacon-item.custom-enchantments.soulbound.owner-usage-only");

        itemNerfsExpPercentagePerCycle = config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle");
        itemNerfsOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        itemNerfsDisabledWorlds = Sets.newHashSet(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        itemNerfsForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);

        anvilCombinationEnabled = config.getBoolean("anvil-combination.enabled");
        anvilCombinationMaxEffects = config.getInt("anvil-combination.max-effects");
        if (config.contains("anvil-combination.max-effect-amplifier", true)) {
            int maxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
            Config.effectsDefault = new PotionEffectInfo(null, Config.effectsDefault.durationInTicks, maxAmplifier);
            config.set("effects.default.max-amplifier", maxAmplifier);
            config.set("anvil-combination.max-effect-amplifier", null);
        }
        anvilCombinationCombineEffectsAdditively = config.getBoolean("anvil-combination.combine-effects-additively");
        anvilCombinationEnforceVanillaExpLimit = config.getBoolean("anvil-combination.enforce-vanilla-exp-limit");

        worldGuard = config.getBoolean("world-guard");
    }

    @SuppressWarnings("ConstantConditions")
    public static void readEffects(FileConfiguration config) {
        effects = new HashMap<>();
        // of course getValues doesn't work
        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        Set<String> keys = new LinkedHashSet<>(config.getDefaults().getConfigurationSection("effects").getKeys(false));
        // add keys from actual config later
        // so that user-defined values override defaults
        keys.addAll(effectsSection.getKeys(false));
        for (String key : keys) {
            ConfigurationSection yaml = config.getConfigurationSection("effects." + key);

            //PortableBeacons.INSTANCE.logger.info("Reading " + key);
            String displayName = translateColor(yaml.getString("name"));
            Integer maxAmplifier = (Integer) yaml.get("max-amplifier");
            Integer duration = (Integer) yaml.get("duration");
            if (key.equals("default")) {
                Preconditions.checkNotNull(maxAmplifier, "'max-amplifier' in default cannot be null");
                Preconditions.checkNotNull(duration, "'duration' in default cannot be null");

                effectsDefault = new PotionEffectInfo(null, duration, maxAmplifier);
            } else {
                PotionEffectType type = CommandPortableBeacons.getType(key);
                if (type == null)
                    throw new IllegalArgumentException(key + " is not a valid potion effect type");

                effects.put(type, new PotionEffectInfo(displayName, duration, maxAmplifier));
            }
        }

        Preconditions.checkNotNull(effectsDefault, "default must be provided");
    }

    private static final boolean canUseRGB;

    static {
        boolean canUseRGB1;
        try {
            ChatColor.class.getMethod("of", String.class);
            canUseRGB1 = true;
        } catch (NoSuchMethodException e) {
            canUseRGB1 = false;
        }
        canUseRGB = canUseRGB1;
    }

    @Nullable
    public static String translateColor(@Nullable String raw) {
        if (raw == null) return null;
        // replace RGB codes first
        // use EssentialsX &#RRGGBB format
        if (canUseRGB) {
            StringBuilder builder = new StringBuilder(raw);
            int idx;
            while ((idx = builder.indexOf("&#")) != -1) {
                try {
                    String colorStr = builder.substring(idx + 1, idx + 8);
                    ChatColor color = ChatColor.of(colorStr);
                    builder.replace(idx, idx + 8, color.toString());
                } catch (StringIndexOutOfBoundsException | IllegalArgumentException e) {
                    throw new IllegalArgumentException("Malformed RGB color around index=" + idx + ", string=" + raw, e);
                }
            }
            raw = builder.toString();
        }

        raw = ChatColor.translateAlternateColorCodes('&', raw);
        return raw;
    }

    public static ItemStack ritualItem;

    public static String itemName;
    public static List<String> itemLore;
    public static int itemCustomModelData;
    public static String itemCustomVersion = null;

    // Reminder
    public static boolean itemCreationReminderEnabled;
    public static String itemCreationReminderMessage;
    public static double itemCreationReminderRadius;
    public static boolean itemCreationReminderDisableIfAlreadyOwnBeaconItem;

    // Custom Enchantments
    public static boolean customEnchantExpReductionEnabled;
    public static double customEnchantExpReductionReductionPerLevel;
    public static int customEnchantExpReductionMaxLevel;
    public static String customEnchantExpReductionName;
    public static Enchantment customEnchantExpReductionEnchantment;

    public static boolean customEnchantSoulboundEnabled;
    public static boolean customEnchantSoulboundOwnerUsageOnly;
    public static int customEnchantSoulboundMaxLevel;
    public static String customEnchantSoulboundName;
    public static Enchantment customEnchantSoulboundEnchantment;

    // Nerfs
    public static double itemNerfsExpPercentagePerCycle;
    public static boolean itemNerfsOnlyApplyInHotbar;
    public static Set<String> itemNerfsDisabledWorlds;
    public static boolean itemNerfsForceDowngrade;

    // Anvil crafting
    public static boolean anvilCombinationEnabled;
    public static int anvilCombinationMaxEffects;
    public static boolean anvilCombinationCombineEffectsAdditively;
    public static boolean anvilCombinationEnforceVanillaExpLimit;

    public static boolean worldGuard;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @NotNull
    public static PotionEffectInfo effectsDefault;
    public static HashMap<PotionEffectType, PotionEffectInfo> effects;

    public static class PotionEffectInfo {
        @Nullable
        public final String displayName;
        @Nullable
        public final Integer durationInTicks;
        @Nullable
        public final Integer maxAmplifier;

        public PotionEffectInfo(@Nullable String displayName, @Nullable Integer duration, @Nullable Integer maxAmplifier) {
            this.displayName = displayName;
            this.durationInTicks = duration;
            this.maxAmplifier = maxAmplifier;
        }

        @SuppressWarnings("ConstantConditions")
        public int getDuration() {
            return durationInTicks != null ? durationInTicks : effectsDefault.durationInTicks;
        }

        @SuppressWarnings("ConstantConditions")
        public int getMaxAmplifier() {
            return maxAmplifier != null ? maxAmplifier : effectsDefault.maxAmplifier;
        }
    }
}
