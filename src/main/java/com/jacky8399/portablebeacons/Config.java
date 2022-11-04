package com.jacky8399.portablebeacons;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
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

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Config {
    public static void saveConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        // things that can be changed by commands
        config.set("ritual.item", Config.ritualItem);
        // toggles
        config.set("ritual.enabled", Config.ritualEnabled);
        config.set("anvil-combination.enabled", Config.anvilCombinationEnabled);
        config.set("beacon-item.effects-toggle.enabled", Config.effectsToggleEnabled);
        config.set("beacon-item.creation-reminder.enabled", Config.creationReminder);
        // prompt
        config.set("ritual.__", "ritual.item is saved by the plugin! If it is empty, the default (32x nether_star) is used.");
        if (Config.itemCustomVersion != null)
            config.set("item-custom-version-do-not-edit", Config.itemCustomVersion);
        config.options().copyDefaults(true).header("Documentation: \n" +
                "https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml");
    }

    public static void doMigrations(FileConfiguration config) {
        List<String> migrated = new ArrayList<>();
        // ritual.item
        ItemStack legacy = config.getItemStack("item-used", config.getItemStack("item_used"));
        if (legacy != null && !config.contains("ritual.item", true)) {
            config.set("ritual.item", legacy);
            config.set("item-used", null);
            config.set("item_used", null);
            migrated.add("item-used/item_used -> ritual.item");
        }

        // beacon-item.nerfs.exp-percentage-per-cycle
        if (config.contains("beacon-item.nerfs.exp-percentage-per-cycle") &&
                !config.contains("beacon-item.nerfs.exp-levels-per-minute", true)) {
            double expPerCycle = config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle");
            config.set("beacon-item.nerfs.exp-levels-per-minute", expPerCycle * 8);
            config.set("beacon-item.nerfs.exp-percentage-per-cycle", null);
            migrated.add("beacon-item.nerfs: exp-percentage-per-cycle -> exp-levels-per-minute");
        }

        // legacy effects
        if (loadConfigLegacy(config)) {
            migrated.add("beacon-item.effects -> effects");
        }

        if (migrated.size() != 0) {
            Logger logger = PortableBeacons.INSTANCE.logger;
            logger.warning("The following legacy config values have been migrated (in memory):");
            migrated.forEach(message -> logger.warning(" - " + message));
            logger.warning("Confirm that the features still work correctly, then run " +
                    "'/portablebeacons saveconfig' to save these changes to disk.");
            logger.warning("Consult documentation and migrate manually by deleting old values if necessary.");
        }
    }

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();

        doMigrations(config);

        // debug
        Logger logger = PortableBeacons.INSTANCE.logger;
        debug = config.getBoolean("debug");

        // Ritual item
        ritualEnabled = config.getBoolean("ritual.enabled");
        ritualItem = config.getItemStack("ritual.item");
        if (ritualItem == null) {
            ritualItem = new ItemStack(Material.NETHER_STAR, 32);
        }

        // World interactions
        placementEnabled = config.getBoolean("world-interactions.placement-enabled");
        pickupEnabled = config.getBoolean("world-interactions.pickup-enabled");
        pickupRequireSilkTouch = config.getBoolean("world-interactions.pickup-requires-silk-touch");


        // Beacon item properties

        itemCustomVersion = config.getString("item-custom-version-do-not-edit");

        itemName = translateColor(config.getString("beacon-item.name", ""));
        itemLore = config.getStringList("beacon-item.lore").stream().map(Config::translateColor).collect(Collectors.toList());

        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        // Creation reminder

        creationReminder = config.getBoolean("beacon-item.creation-reminder.enabled");
        creationReminderMessage = translateColor(config.getString("beacon-item.creation-reminder.message"));
        creationReminderRadius = config.getDouble("beacon-item.creation-reminder.radius");
        creationReminderDisableIfOwned = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        // Effects Toggle GUI
        effectsToggleEnabled = config.getBoolean("beacon-item.effects-toggle.enabled");
        effectsToggleTitle = translateColor(config.getString("beacon-item.effects-toggle.title"));
        effectsToggleExpUsageMessage = translateColor(config.getString("beacon-item.effects-toggle.exp-usage-message"));
        effectsToggleCanDisableNegativeEffects = config.getBoolean("beacon-item.effects-toggle.allow-disabling-negative-effects");
        effectsToggleFineTunePerms = config.getBoolean("beacon-item.effects-toggle.require-permission");

        // Custom enchantments

        // Exp-reduction

        enchExpReductionEnabled = config.getBoolean("beacon-item.custom-enchantments.exp-reduction.enabled");
        enchExpReductionEnchantment = Enchantment.getByKey(NamespacedKey.minecraft(config.getString("beacon-item.custom-enchantments.exp-reduction.enchantment").toLowerCase(Locale.ROOT)));
        enchExpReductionMaxLevel = config.getInt("beacon-item.custom-enchantments.exp-reduction.max-level");
        enchExpReductionName = translateColor(config.getString("beacon-item.custom-enchantments.exp-reduction.name"));
        enchExpReductionReductionPerLevel = config.getDouble("beacon-item.custom-enchantments.exp-reduction.reduction-per-level");

        // Soulbound

        enchSoulboundEnabled = config.getBoolean("beacon-item.custom-enchantments.soulbound.enabled");
        enchSoulboundEnchantment = Enchantment.getByKey(NamespacedKey.minecraft(config.getString("beacon-item.custom-enchantments.soulbound.enchantment").toLowerCase(Locale.ROOT)));
        enchSoulboundMaxLevel = config.getInt("beacon-item.custom-enchantments.soulbound.max-level");
        enchSoulboundName = translateColor(config.getString("beacon-item.custom-enchantments.soulbound.name"));
        enchSoulboundOwnerUsageOnly = config.getBoolean("beacon-item.custom-enchantments.soulbound.owner-usage-only");
        enchSoulboundConsumeLevelOnDeath = config.getBoolean("beacon-item.custom-enchantments.soulbound.consume-level-on-death");
        enchSoulboundCurseOfBinding = config.getBoolean("beacon-item.custom-enchantments.soulbound.just-for-fun-curse-of-binding");

        // Nerfs

        nerfExpLevelsPerMinute = Math.max(0, config.getDouble("beacon-item.nerfs.exp-levels-per-minute"));
        nerfOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        nerfDisabledWorlds = Sets.newHashSet(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        nerfForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);

        // Anvil combination

        anvilCombinationEnabled = config.getBoolean("anvil-combination.enabled");
        anvilCombinationMaxEffects = config.getInt("anvil-combination.max-effects");
        if (config.contains("anvil-combination.max-effect-amplifier", true)) {
            int maxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
            Config.effectsDefault = new PotionEffectInfo(null, Config.effectsDefault.durationInTicks, maxAmplifier, Config.effectsDefault.hideParticles);
            config.set("effects.default.max-amplifier", maxAmplifier);
            config.set("anvil-combination.max-effect-amplifier", null);
            PortableBeacons.INSTANCE.logger.info("Old config (anvil-combination.max-effect-amplifier) has been migrated automatically. Use '/pb saveconfig' to save to file.");
        }
        anvilCombinationCombineEffectsAdditively = config.getBoolean("anvil-combination.combine-effects-additively");
        anvilCombinationEnforceVanillaExpLimit = config.getBoolean("anvil-combination.enforce-vanilla-exp-limit");
        anvilDisplayFailurePrompt = config.getBoolean("anvil-combination.display-failure-prompt");

        worldGuard = config.getBoolean("world-guard");

        if (debug) {
            logger.info("[Debug] Configuration:");
            Map<String, Object> values = config.getValues(true);
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    logger.info(key + ": " + value);
                } else if (value instanceof List<?>) {
                    logger.info(key + ": " + ((List<?>) value).stream()
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ", "[", "]")));
                }
            }
        }

        logger.info("Config loaded");
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

            try {
                String displayName = translateColor(yaml.getString("name"));
                Integer maxAmplifier = (Integer) yaml.get("max-amplifier");
                Integer duration = (Integer) yaml.get("duration");
                Boolean hideParticles = (Boolean) yaml.get("hide-particles");
                if (key.equals("default")) {
                    Preconditions.checkNotNull(maxAmplifier, "'max-amplifier' in default cannot be null");
                    Preconditions.checkNotNull(duration, "'duration' in default cannot be null");
                    Preconditions.checkNotNull(hideParticles, "'hide-particles' in default cannot be null");

                    effectsDefault = new PotionEffectInfo(null, duration, maxAmplifier, hideParticles);
                } else {
                    PotionEffectType type = PotionEffectUtils.parsePotion(key, true)
                            .orElseThrow(()->new IllegalArgumentException(key + " is not a valid potion effect"));

                    effects.put(type, new PotionEffectInfo(displayName, duration, maxAmplifier, hideParticles));
                }
            } catch (Exception e) {
                PortableBeacons.INSTANCE.logger.severe(String.format("Error while reading config 'effects.%s' (%s), skipping!", key, e.getMessage()));
            }
        }

        if (effectsDefault == null) {
            PortableBeacons.INSTANCE.logger.severe("'effects.default' must be provided");
            effectsDefault = new PotionEffectInfo(null, 140, 3, false);
        }
    }

    public static boolean loadConfigLegacy(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("beacon-item.effects");
        if (section != null) {
            section.getValues(false).forEach((effect, name) -> {
                try {
                    PotionEffectType type = PotionEffectUtils.parsePotion(effect, true)
                            .orElseThrow(IllegalArgumentException::new); // for vanilla names
                    String newName = Config.translateColor((String) name);
                    // override PotionEffectInfo
                    Config.PotionEffectInfo info = Config.effects.get(type);
                    Config.PotionEffectInfo newInfo = new Config.PotionEffectInfo(newName, info != null ? info.durationInTicks : null, info != null ? info.maxAmplifier : null, info != null ? info.hideParticles : null);
                    Config.effects.put(type, newInfo);
                    config.set("effects." + effect + ".name", name);
                } catch (IllegalArgumentException ignored) {}
            });
            // delete section
            config.set("beacon-item.effects", null);
            return true;
        }
        return false;
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

    public static boolean debug;

    public static boolean ritualEnabled;
    public static ItemStack ritualItem;

    public static String itemName;
    public static List<String> itemLore;
    public static int itemCustomModelData;
    public static String itemCustomVersion = null;

    // World interactions
    public static boolean placementEnabled;
    public static boolean pickupEnabled;
    public static boolean pickupRequireSilkTouch;

    // Reminder
    public static boolean creationReminder;
    public static String creationReminderMessage;
    public static double creationReminderRadius;
    public static boolean creationReminderDisableIfOwned;

    // Effects toggle GUI
    public static boolean effectsToggleEnabled;
    public static String effectsToggleTitle;
    public static String effectsToggleExpUsageMessage;
    public static boolean effectsToggleCanDisableNegativeEffects;
    public static boolean effectsToggleFineTunePerms;

    // Custom Enchantments
    public static boolean enchExpReductionEnabled;
    public static double enchExpReductionReductionPerLevel;
    public static int enchExpReductionMaxLevel;
    public static String enchExpReductionName;
    public static Enchantment enchExpReductionEnchantment;

    public static boolean enchSoulboundEnabled;
    public static boolean enchSoulboundOwnerUsageOnly;
    public static boolean enchSoulboundConsumeLevelOnDeath;
    public static boolean enchSoulboundCurseOfBinding;
    public static int enchSoulboundMaxLevel;
    public static String enchSoulboundName;
    public static Enchantment enchSoulboundEnchantment;

    // Nerfs
    public static double nerfExpLevelsPerMinute;
    public static boolean nerfOnlyApplyInHotbar;
    public static Set<String> nerfDisabledWorlds;
    public static boolean nerfForceDowngrade;

    // Anvil crafting
    public static boolean anvilCombinationEnabled;
    public static int anvilCombinationMaxEffects;
    public static boolean anvilCombinationCombineEffectsAdditively;
    public static boolean anvilCombinationEnforceVanillaExpLimit;
    public static boolean anvilDisplayFailurePrompt;

    public static boolean worldGuard;

    @SuppressWarnings("NotNullFieldNotInitialized")
    @NotNull
    public static PotionEffectInfo effectsDefault;
    public static HashMap<PotionEffectType, PotionEffectInfo> effects;
    private static final PotionEffectInfo EMPTY_INFO = new PotionEffectInfo(null, null, null, null);
    @NotNull
    public static PotionEffectInfo getInfo(PotionEffectType potion) {
        return effects.getOrDefault(potion, EMPTY_INFO);
    }

    public static class PotionEffectInfo {
        @Nullable
        public final String displayName;
        @Nullable
        public final Integer durationInTicks;
        @Nullable
        public final Integer maxAmplifier;
        @Nullable
        public final Boolean hideParticles;

        public PotionEffectInfo(@Nullable String displayName, @Nullable Integer duration, @Nullable Integer maxAmplifier, @Nullable Boolean hideParticles) {
            this.displayName = displayName;
            this.durationInTicks = duration;
            this.maxAmplifier = maxAmplifier;
            this.hideParticles = hideParticles;
        }

        @SuppressWarnings("ConstantConditions")
        public int getDuration() {
            return durationInTicks != null ? durationInTicks : effectsDefault.durationInTicks;
        }

        @SuppressWarnings("ConstantConditions")
        public int getMaxAmplifier() {
            return maxAmplifier != null ? maxAmplifier : effectsDefault.maxAmplifier;
        }

        @SuppressWarnings("ConstantConditions")
        public boolean isHideParticles() {
            return hideParticles != null ? hideParticles : effectsDefault.hideParticles;
        }
    }
}
