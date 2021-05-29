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
        config.options().copyDefaults(true).header("To see descriptions of different options: \n" +
                "https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml");
    }

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();

        // debug
        Logger logger = PortableBeacons.INSTANCE.logger;
        if ((debug = config.getBoolean("debug"))) {
            logger.info("Debug enabled");
        }

        // Ritual item
        ritualEnabled = config.getBoolean("ritual.enabled");
        ritualItem = config.getItemStack("ritual.item");
        if (ritualItem == null) {
            ItemStack legacy = config.getItemStack("item-used", config.getItemStack("item_used"));
            if (legacy != null) {
                config.set("item-used", null);
                config.set("item_used", null);
                ritualItem = legacy;
                logger.info("Old config (item-used) has been migrated successfully. Use '/pb saveconfig' to save to file.");
            } else {
                ritualItem = new ItemStack(Material.NETHER_STAR, 32);
            }
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

        nerfExpPercentagePerCycle = Math.max(0, config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle"));
        nerfOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        nerfDisabledWorlds = Sets.newHashSet(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        nerfForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);
        loadConfigLegacy(config);

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
            logger.info("Ritual/enabled: " + ritualEnabled);
            logger.info("Ritual/item: " + ritualItem);
            logger.info("Item custom version: " + itemCustomVersion);
            logger.info("Beacon item/name: " + itemName);
            logger.info("Beacon item/lore: " + String.join("\\n", itemLore));
            logger.info("Beacon item/custom model data: " + itemCustomModelData);
            logger.info("Placement/enabled: " + placementEnabled);
            logger.info("Pickup/enabled: " + pickupEnabled);
            logger.info("Pickup/require silk touch: " + pickupRequireSilkTouch);
            logger.info("Creation reminder/enabled: " + creationReminder);
            logger.info("Creation reminder/message: " + creationReminderMessage);
            logger.info("Creation reminder/radius: " + creationReminderRadius);
            logger.info("Creation reminder/disable if owned: " + creationReminderDisableIfOwned);
            logger.info("Effects toggle/enabled: " + effectsToggleEnabled);
            logger.info("Effects toggle/title: " + effectsToggleTitle);
            logger.info("Effects toggle/can disable negative effects: " + effectsToggleCanDisableNegativeEffects);
            logger.info("Effects toggle/fine tune perms: " + effectsToggleFineTunePerms);
            logger.info("Enchant/Exp-reduction/enabled: " + enchExpReductionEnabled);
            logger.info("Enchant/Exp-reduction/enchantment: " + enchExpReductionEnchantment);
            logger.info("Enchant/Exp-reduction/max level: " + enchExpReductionMaxLevel);
            logger.info("Enchant/Exp-reduction/name: " + enchExpReductionName);
            logger.info("Enchant/Exp-reduction/reduction per level: " + enchExpReductionReductionPerLevel);
            logger.info("Enchant/Soulbound/enabled: " + enchSoulboundEnabled);
            logger.info("Enchant/Soulbound/enchantment: " + enchSoulboundEnchantment);
            logger.info("Enchant/Soulbound/max level: " + enchSoulboundMaxLevel);
            logger.info("Enchant/Soulbound/name: " + enchSoulboundName);
            logger.info("Enchant/Soulbound/owner usage only: " + enchSoulboundOwnerUsageOnly);
            logger.info("Enchant/Soulbound/consume level on death: " + enchSoulboundConsumeLevelOnDeath);
            logger.info("Enchant/Soulbound/curse of binding: " + enchSoulboundCurseOfBinding);
            logger.info("Nerfs/exp % per cycle: " + nerfExpPercentagePerCycle);
            logger.info("Nerfs/only apply when in hotbar: " + nerfOnlyApplyInHotbar);
            logger.info("Nerfs/disabled worlds: " + String.join(", ", nerfDisabledWorlds));
            logger.info("Nerfs/force downgrade: " + nerfForceDowngrade);
            logger.info("Anvil combination/enabled: " + anvilCombinationEnabled);
            logger.info("Anvil combination/max effects: " + anvilCombinationMaxEffects);
            logger.info("Anvil combination/combine effects additively: " + anvilCombinationCombineEffectsAdditively);
            logger.info("Anvil combination/enforce vanilla exp limit: " + anvilCombinationEnforceVanillaExpLimit);
            logger.info("Anvil combination/display failure prompt: " + anvilDisplayFailurePrompt);
            logger.info("World guard: " + worldGuard);
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

    public static void loadConfigLegacy(FileConfiguration config) {
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
            PortableBeacons.INSTANCE.logger.info("Old config (beacon-item.effects) has been migrated automatically. Use '/pb saveconfig' to save to file.");
        }
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
    public static double nerfExpPercentagePerCycle;
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
