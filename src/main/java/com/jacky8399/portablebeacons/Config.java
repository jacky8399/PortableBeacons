package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.recipes.CombinationRecipe;
import com.jacky8399.portablebeacons.recipes.ExpCostCalculator;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.recipes.SimpleRecipe;
import com.jacky8399.portablebeacons.utils.BeaconModification;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public class Config {
    public static final Map<String, Field> TOGGLES;
    public static final Logger LOGGER = PortableBeacons.LOGGER;

    static {
        Class<Config> clazz = Config.class;
        try {
            TOGGLES = Map.of("ritual", clazz.getField("ritualEnabled"),
                    "toggle-gui", clazz.getField("effectsToggleEnabled"),
                    "creation-reminder", clazz.getField("creationReminder"),
                    "world-placement", clazz.getField("placementEnabled"),
                    "world-pickup", clazz.getField("pickupEnabled"));
        } catch (ReflectiveOperationException e) {
            throw new Error("Can't find toggleable field", e);
        }
    }

    public static final int CONFIG_VERSION = 2;

    public static void saveConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        // things that can be changed by commands
        config.set("ritual.item", Config.ritualItem);
        // toggles
        config.set("ritual.enabled", Config.ritualEnabled);
        config.set("beacon-item.effects-toggle.enabled", Config.effectsToggleEnabled);
        config.set("beacon-item.creation-reminder.enabled", Config.creationReminder);
        config.set("world-interactions.placement-enabled", Config.placementEnabled);
        config.set("world-interactions.pickup-enabled", Config.pickupEnabled);
        // warning message
        config.set("ritual.__", "ritual.item is saved by the plugin! If it is empty, the default (32x nether_star) is used.");
        config.setComments("ritual.item", List.of("Saved by the plugin", "To change this value in-game, use /portablebeacons setritualitem"));
        if (Config.itemCustomVersion != null) {
            config.set("item-custom-version-do-not-edit", Config.itemCustomVersion);
            config.setComments("item-custom-version-do-not-edit", List.of("Saved by the plugin", "To change this value in-game, use /portablebeacons updateitems"));
        }
        config.set("config-version", CONFIG_VERSION);
        config.setComments("config-version", List.of("The configuration version. Shouldn't be modified."));
        config.options().copyDefaults(true).setHeader(List.of("Documentation:",
                "https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml"));
    }

    public static void doMigrations(FileConfiguration config) {
        Logger logger = LOGGER;
        int configVersion = config.getInt("config-version", 0);

        List<String> migrated = new ArrayList<>();
        List<String> needsAttention = new ArrayList<>();

        ConfigMigrations.MigrationLogger migrationLogger = new ConfigMigrations.MigrationLogger(logger, migrated, needsAttention);

        if (configVersion == 0) {
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

            // anvil-combination.max-effect-amplifier
            if (config.contains("anvil-combination.max-effect-amplifier", true)) {
                int maxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
                config.set("effects.default.max-amplifier", maxAmplifier);
                config.set("anvil-combination.max-effect-amplifier", null);
                migrated.add("anvil-combination.max-effect-amplifier -> effects.default.max-amplifier");
            }

            // legacy effects
            if (migrateEffectsLegacy(migrationLogger, config)) {
                migrated.add("beacon-item.effects -> effects");
            }

            // anvil recipes
            doAnvilMigrations(config, migrated);
        } else if (configVersion <= 1) {
            ConfigMigrations.V1.migrateExpUsageMessage(migrationLogger, config);
            ConfigMigrations.V1.migrateEffectNames(migrationLogger, config);
        }

        if (!migrated.isEmpty()) {
            logger.warning("The following legacy config values have been migrated (in memory):");
            migrated.forEach(message -> logger.warning(" - " + message));
            logger.severe("Confirm that the features still work correctly, then run " +
                    "'/portablebeacons saveconfig' to save these changes to disk.");
            logger.severe("Consult documentation and migrate manually if necessary.");
        }
        if (!needsAttention.isEmpty()) {
            logger.severe("Additionally, the following config values need your attention:");
            needsAttention.forEach(message -> logger.severe(" - " + message));
        }
    }

    /**
     * Copies comments and inline comments from {@code source.path} to {@code destination.path}
     */
    static void copyComments(@Nullable YamlConfiguration source, YamlConfiguration destination, String path, boolean deep) {
        if (source == null)
            return;
        var srcSection = source.getConfigurationSection(path);
        var destSection = destination.getConfigurationSection(path);
        if (srcSection == null || destSection == null)
            return;
        for (String key : destSection.getKeys(deep)) {
            List<String> comments = srcSection.getComments(key);
            if (!comments.isEmpty()) {
                destSection.setComments(key, comments);
            }
            comments = srcSection.getInlineComments(key);
            if (!comments.isEmpty()) {
                destSection.setInlineComments(key, comments);
            }
        }
    }

    static void doAnvilMigrations(FileConfiguration config, List<String> migrated) {
        Logger logger = LOGGER;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(RecipeManager.RECIPES_FILE);
        YamlConfiguration defaultYaml;
        try (var reader = new InputStreamReader(Objects.requireNonNull(PortableBeacons.INSTANCE.getResource("recipes.yml")))) {
            defaultYaml = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ignored) {
            defaultYaml = null;
        }
        boolean changed = false;
        ConfigurationSection section;
        if ((section = config.getConfigurationSection("anvil-combination")) != null) {
            try {
                var values = CombinationRecipe.load("anvil-combination", section.getValues(false)).save();

                yaml.createSection("recipes.anvil-combination", values);
                yaml.setComments("recipes.anvil-combination", """
                        This recipe was created by automatic config migration.
                        To avoid having your changes be overwritten, you must either:
                         - Delete 'anvil-combination' in config.yml, OR
                         - Save the migrated config.yml using /pb saveconfig""".lines().toList());
                copyComments(defaultYaml, yaml, "recipes.anvil-combination", true);
                changed = true;
                config.set("anvil-combination", null);
                migrated.add("anvil-combination -> recipes.yml");
            } catch (Exception ex) {
                logger.severe("Failed to migrate anvil-combination");
                ex.printStackTrace();
            }
        }

        for (String customEnch : new String[]{"exp-reduction", "soulbound"}) {
            String oldPath = "beacon-item.custom-enchantments." + customEnch + ".enchantment";
            String enchStr;
            if ((enchStr = config.getString(oldPath, null)) == null || enchStr.isEmpty())
                continue;
            // enchantment-level was never released
            try {
                Enchantment ench = Objects.requireNonNull(Enchantment.getByKey(NamespacedKey.fromString(enchStr.toLowerCase(Locale.ENGLISH))),
                        "Invalid enchantment " + enchStr);
                var fakeStack = new ItemStack(Material.ENCHANTED_BOOK);
                var meta = (EnchantmentStorageMeta) fakeStack.getItemMeta();
                meta.addStoredEnchant(ench, 1, false);
                fakeStack.setItemMeta(meta);

                // add one level of enchantment
                var virtualEffects = BeaconEffects.load(Map.of(customEnch, 1), true);
                var recipe = new SimpleRecipe(
                        customEnch, InventoryType.ANVIL, fakeStack,
                        List.of(new BeaconModification(BeaconModification.Type.ADD, virtualEffects)),
                        ExpCostCalculator.Dynamic.VANILLA,
                        customEnch.equals("soulbound") ?
                                EnumSet.of(SimpleRecipe.SpecialOps.SET_SOULBOUND_OWNER) :
                                EnumSet.noneOf(SimpleRecipe.SpecialOps.class)
                );
                yaml.createSection("recipes." + customEnch, recipe.save());
                yaml.setComments("recipes." + customEnch, """
                        This recipe was created by automatic config migration.
                        To avoid having your changes be overwritten, you must either:
                         - Delete '%s' in config.yml, OR
                         - Save the migrated config.yml using /pb saveconfig""".formatted(oldPath).lines().toList());
                copyComments(defaultYaml, yaml, "recipes." + customEnch, true);
                config.set(oldPath, null);
                changed = true;
                migrated.add(oldPath + " -> recipes.yml");
            } catch (Exception ex) {
                logger.severe("Failed to migrate " + oldPath);
                ex.printStackTrace();
            }
        }

        if (changed) {
            try {
                yaml.save(RecipeManager.RECIPES_FILE);
            } catch (IOException ex) {
                logger.severe("An error occurred while migrating anvil-combination");
                ex.printStackTrace();
            }
        }
    }

    public static void loadConfig() {
        placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        // load messages.yml and set default
        FileConfiguration messages = YamlConfiguration.loadConfiguration(new File(PortableBeacons.INSTANCE.getDataFolder(), "messages.yml"));
        try (var is = Objects.requireNonNull(PortableBeacons.INSTANCE.getResource("messages.yml"));
             var reader = new InputStreamReader(is)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException ignored) {}

        doMigrations(config);

        try {
            loadConfigFrom(config, messages);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load config.yml, falling back to default configuration.", ex);
            loadConfigFrom(Objects.requireNonNull(config.getDefaults()), Objects.requireNonNull(messages.getDefaults()));
        }
        LOGGER.info("Config loaded");
    }

    private static void loadConfigFrom(Configuration config, Configuration messages) {
        Messages.loadMessages(messages);

        // debug
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

        itemName = translateColor(config.getString("beacon-item.name"));
        itemLore = config.getStringList("beacon-item.lore");

        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        // Creation reminder

        creationReminder = config.getBoolean("beacon-item.creation-reminder.enabled");
        creationReminderMessage = translateColor(config.getString("beacon-item.creation-reminder.message"));
        creationReminderRadius = getAndCheckDouble(0, config, "beacon-item.creation-reminder.radius");
        creationReminderDisableIfOwned = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        // Effects Toggle GUI
        var effectsToggle = config.getConfigurationSection("beacon-item.effects-toggle");
        effectsToggleEnabled = effectsToggle.getBoolean("enabled");
        effectsToggleTitle = getAndColorizeString(effectsToggle, "title");
        effectsToggleExpUsageMessage = getAndColorizeString(effectsToggle, "exp-usage-message");
        effectsToggleCanDisableNegativeEffects = effectsToggle.getBoolean("allow-disabling-negative-effects");
        effectsToggleFineTunePerms = effectsToggle.getBoolean("require-permission");
        Object canToggleBeaconator = effectsToggle.get("allow-toggling-beaconator");
        if (canToggleBeaconator instanceof Boolean bool) {
            effectsToggleCanToggleBeaconator = bool ? BeaconatorToggleMode.TRUE : BeaconatorToggleMode.FALSE;
        } else {
            try {
                effectsToggleCanToggleBeaconator = BeaconatorToggleMode.valueOf(canToggleBeaconator.toString().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException ignored) {
                effectsToggleCanToggleBeaconator = BeaconatorToggleMode.TRUE;
            }
        }

        // Effects Toggle Breakdown
        effectsToggleBreakdownEnabled = effectsToggle.getBoolean("breakdown-enabled");

        // Custom enchantments

        // Exp-reduction

        enchExpReductionEnabled = config.getBoolean("beacon-item.custom-enchantments.exp-reduction.enabled");
        enchExpReductionMaxLevel = getAndCheckInt(1, config, "beacon-item.custom-enchantments.exp-reduction.max-level");
        enchExpReductionName = translateColor(config.getString("beacon-item.custom-enchantments.exp-reduction.name"));
        enchExpReductionReductionPerLevel = config.getDouble("beacon-item.custom-enchantments.exp-reduction.reduction-per-level");

        // Soulbound

        enchSoulboundEnabled = config.getBoolean("beacon-item.custom-enchantments.soulbound.enabled");
        enchSoulboundMaxLevel = getAndCheckInt(1, config, "beacon-item.custom-enchantments.soulbound.max-level");
        enchSoulboundName = translateColor(config.getString("beacon-item.custom-enchantments.soulbound.name"));
        enchSoulboundOwnerUsageOnly = config.getBoolean("beacon-item.custom-enchantments.soulbound.owner-usage-only");
        enchSoulboundConsumeLevelOnDeath = config.getBoolean("beacon-item.custom-enchantments.soulbound.consume-level-on-death");
        enchSoulboundCurseOfBinding = config.getBoolean("beacon-item.custom-enchantments.soulbound.just-for-fun-curse-of-binding");

        // Beaconator

        var enchBeaconator = config.getConfigurationSection("beacon-item.custom-enchantments.beaconator");
        enchBeaconatorEnabled = enchBeaconator.getBoolean("enabled");
        enchBeaconatorName = getAndColorizeString(enchBeaconator, "name");
        try {
            enchBeaconatorInRangeCostMultiplier = ExpCostCalculator.deserialize(enchBeaconator.get("in-range-cost-multiplier"), false);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.WARNING, enchBeaconator.getCurrentPath() + ".in-range-cost-multiplier: " +
                    enchBeaconator.get("in-range-cost-multiplier") + " is not a valid exp-cost: " + ex.getMessage(),
                    debug ? ex : null);
            enchBeaconatorInRangeCostMultiplier = new ExpCostCalculator.Fixed(0);
        }
        enchBeaconatorLevels = enchBeaconator.getMapList("levels").stream()
                .map(map -> {
                    Object radiusObj = map.get("radius"), costObj = map.get("exp-cost");
                    try {
                        ExpCostCalculator calculator = ExpCostCalculator.deserialize(costObj);
                        return new BeaconatorLevel(((Number) radiusObj).intValue(), calculator);
                    } catch (ClassCastException ignored) {
                        LOGGER.log(Level.WARNING, "%s.levels: %s is not a valid radius".formatted(enchBeaconator.getCurrentPath(), radiusObj));
                    } catch (IllegalArgumentException ex) {
                        LOGGER.log(Level.WARNING, "%s.levels: %s is not a valid exp-cost: %s".formatted(enchBeaconator.getCurrentPath(), costObj, ex.getMessage()),
                                debug ? ex : null);
                    }
                    return new BeaconatorLevel(0, null);
                })
                .toList();
        if (enchBeaconatorLevels.isEmpty())
            throw new IllegalArgumentException("beacon-item.custom-enchantments.beaconator.levels cannot be empty");

        // Nerfs

        nerfExpLevelsPerMinute = getAndCheckDouble(0, config, "beacon-item.nerfs.exp-levels-per-minute");
        nerfOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");
        nerfDisabledWorlds = new HashSet<>(config.getStringList("beacon-item.nerfs.disabled-worlds"));
        nerfForceDowngrade = config.getBoolean("beacon-item.nerfs.force-downgrade");

        readEffects(config);

        worldGuard = config.getBoolean("world-guard");
    }

    private static void readEffects(Configuration config) {
        var effects = new HashMap<PotionEffectType, PotionEffectInfo>();
        // of course getValues doesn't work
        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        ConfigurationSection defaultSection = effectsSection.getDefaultSection();
        Set<String> keys = new LinkedHashSet<>();
        keys.add("default"); // load default first
        // add keys from actual config later
        // so that user-defined values override defaults
        keys.addAll(defaultSection.getKeys(false));
        keys.addAll(effectsSection.getKeys(false));

        // ensure that the defaults are set
        effectsDefaultMaxAmplifier = defaultSection.getInt("max-amplifier");
        effectsDefaultDuration = defaultSection.getInt("duration");
        effectsDefaultHideParticles = defaultSection.getBoolean("hide-particles");

        for (String key : keys) {
            ConfigurationSection yaml = config.getConfigurationSection("effects." + key);

            try {
                String nameOverride = getAndColorizeString(config, "name-override");
                String formatOverride = getAndColorizeString(config, "format-override");
                Integer maxAmplifier = getAndCheckInteger(0, 255, yaml, "max-amplifier");
                Integer duration = getAndCheckInteger(1, Integer.MAX_VALUE, yaml, "duration");
                Boolean hideParticles = (Boolean) yaml.get("hide-particles");
                Object expCost = yaml.get("exp-cost");

                if (key.equals("default")) {
                    effectsDefaultMaxAmplifier = Objects.requireNonNull(maxAmplifier, "'max-amplifier' in default cannot be null");
                    effectsDefaultDuration = Objects.requireNonNull(duration, "'duration' in default cannot be null");
                    effectsDefaultHideParticles = Objects.requireNonNull(hideParticles, "'hide-particles' in default cannot be null");

                    if (nameOverride != null || formatOverride != null)
                        LOGGER.severe("'effects.default' cannot have name or format overrides.");
                    if (expCost != null)
                        LOGGER.severe("'effects.default' cannot have experience cost overrides.");
                } else {
                    PotionEffectType type = Objects.requireNonNull(PotionEffectUtils.parsePotion(key, true),
                            key + " is not a valid potion effect");

                    List<? extends ExpCostCalculator> expCostList;
                    int maxAmplifierOrDefault = maxAmplifier == null ? effectsDefaultMaxAmplifier : maxAmplifier;
                    if (expCost instanceof List<?> list) {
                        if (list.size() != maxAmplifierOrDefault)
                            throw new IllegalArgumentException(("'effects.%s.exp-cost': Expected the same number of " +
                                    "entries as max-amplifier (%d), got %d.")
                                    .formatted(key, maxAmplifierOrDefault, list.size()));

                        expCostList = list.stream().map(o -> ExpCostCalculator.deserialize(o, false)).toList();
                    } else if (expCost != null) {
                        ExpCostCalculator calculator = ExpCostCalculator.deserialize(expCost, false);
                        expCostList = IntStream.rangeClosed(1, maxAmplifierOrDefault)
                                .mapToObj(i -> new ExpCostCalculator.Multiplier(calculator, i))
                                .toList();
                    } else {
                        expCostList = null;
                    }

                    effects.put(type, new PotionEffectInfo(nameOverride, EffectFormatter.parse(formatOverride), duration, maxAmplifier, hideParticles, expCostList));
                }
            } catch (Exception e) {
                LOGGER.severe("Skipping invalid config 'effects." + key + "': " + e.getMessage());
            }
        }
        Config.effects = Map.copyOf(effects);
    }

    public static boolean migrateEffectsLegacy(ConfigMigrations.MigrationLogger logger, FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("beacon-item.effects");
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                String effect = entry.getKey();
                Object name = entry.getValue();
                try {
                    PotionEffectType type = PotionEffectUtils.parsePotion(effect, true);
                    if (type == null)
                        logger.needsAttention().add("beacon-item.effects." + effect + ": not a valid potion effect");
                    config.set("effects." + effect + ".name", name);
                } catch (IllegalArgumentException ignored) {
                }
            }
            // delete section
            config.set("beacon-item.effects", null);
            return true;
        }
        return false;
    }

    public static String translateColor(String raw) {
        if (raw == null) return null;
        if (raw.indexOf('&') == -1) return raw;
        // replace RGB codes first
        // use EssentialsX &#RRGGBB format
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
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    static String getFullPath(ConfigurationSection config, String path) {
        String rootPath = config.getCurrentPath();
        if (rootPath == null || rootPath.isEmpty())
            return path;
        else
            return rootPath + "." + path;
    }

    static String getAndColorizeString(ConfigurationSection config, String path) {
        return translateColor(config.getString(path));
    }

    @Nullable
    static Integer getAndCheckInteger(int min, int max, ConfigurationSection config, String path) {
        Object obj = config.get(path);
        if (obj == null)
            return null;
        if (!(obj instanceof Integer value)) {
            LOGGER.severe("Config \"" + getFullPath(config, path) + "\" is not an integer.");
            return min;
        }
        if (value > max) {
            LOGGER.severe("Config \"%s\" cannot be more than %d, got %d.".formatted(getFullPath(config, path), max, value));
            return max;
        } else if (value < min) {
            LOGGER.severe("Config \"%s\" cannot be less than %d, got %d.".formatted(getFullPath(config, path), min, value));
            return min;
        } else {
            return value;
        }
    }


    static int getAndCheckInt(int min, int max, ConfigurationSection config, String path) {
        Integer integer = getAndCheckInteger(min, max, config, path);
        if (integer == null) {
            LOGGER.severe("Config \"" + getFullPath(config, path) + "\" cannot be null");
            return min;
        }
        return integer;
    }

    static int getAndCheckInt(int min, ConfigurationSection config, String path) {
        return getAndCheckInt(min, Integer.MAX_VALUE, config, path);
    }
    static double getAndCheckDouble(double min, double max, ConfigurationSection config, String path) {
        double value = config.getDouble(path);
        if (value > max) {
            LOGGER.severe("Config \"%s\" cannot be larger than %f, got %f."
                    .formatted(getFullPath(config, path), max, value));
            return max;
        } else if (value < min) {
            LOGGER.severe("Config \"%s\" cannot be smaller than %f, got %f."
                    .formatted(getFullPath(config, path), min, value));
            return min;
        } else {
            return value;
        }
    }

    static double getAndCheckDouble(double min, ConfigurationSection config, String path) {
        return getAndCheckDouble(min, Double.MAX_VALUE, config, path);
    }
    // Configuration values

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

    // Effects Toggle GUI
    public static boolean effectsToggleEnabled;
    public static String effectsToggleTitle;
    public static String effectsToggleExpUsageMessage;
    public static boolean effectsToggleCanDisableNegativeEffects;
    public static boolean effectsToggleFineTunePerms;
    public static BeaconatorToggleMode effectsToggleCanToggleBeaconator;

    // Effects Toggle Breakdown
    public static boolean effectsToggleBreakdownEnabled;

    // Custom Enchantments
    public static boolean enchExpReductionEnabled;
    public static double enchExpReductionReductionPerLevel;
    public static int enchExpReductionMaxLevel;
    public static String enchExpReductionName;

    public static boolean enchSoulboundEnabled;
    public static boolean enchSoulboundOwnerUsageOnly;
    public static boolean enchSoulboundConsumeLevelOnDeath;
    public static boolean enchSoulboundCurseOfBinding;
    public static int enchSoulboundMaxLevel;
    public static String enchSoulboundName;

    public static boolean enchBeaconatorEnabled;
    public static String enchBeaconatorName;
    public record BeaconatorLevel(double radius, @Nullable ExpCostCalculator expCost) {}
    public static List<BeaconatorLevel> enchBeaconatorLevels;
    public static ExpCostCalculator enchBeaconatorInRangeCostMultiplier;

    // Nerfs
    public static double nerfExpLevelsPerMinute;
    public static boolean nerfOnlyApplyInHotbar;
    public static Set<String> nerfDisabledWorlds;
    public static boolean nerfForceDowngrade;

    public static boolean worldGuard;

    public static boolean placeholderApi;


    public static int effectsDefaultDuration;

    public static int effectsDefaultMaxAmplifier;

    public static boolean effectsDefaultHideParticles;

    private static Map<PotionEffectType, PotionEffectInfo> effects = Map.of();
    private static final PotionEffectInfo EMPTY_INFO = new PotionEffectInfo(null, null, null, null, null, null);
    @NotNull
    public static PotionEffectInfo getInfo(PotionEffectType potion) {
        return effects.getOrDefault(potion, EMPTY_INFO);
    }

    public interface EffectFormatter {
        String format(int level);

        EffectFormatter DEFAULT = PotionEffectUtils::toRomanNumeral;

        EffectFormatter NUMBER = Integer::toString;

        @Nullable
        static EffectFormatter parse(@Nullable String input) throws IllegalArgumentException {
            if (input == null)
                return null;
            if (input.equals("number")) {
                return NUMBER;
            }
            throw new IllegalArgumentException("Unknown format " + input);
        }
    }

    public record PotionEffectInfo(@Nullable String nameOverride,
                                   @Nullable EffectFormatter formatOverride,
                                   @Nullable Integer durationInTicks,
                                   @Nullable Integer maxAmplifier,
                                   @Nullable Boolean hideParticles,
                                   @Nullable List<? extends ExpCostCalculator> expCostOverride) {

        public int getDuration() {
            return durationInTicks != null ? durationInTicks : effectsDefaultDuration;
        }

        public int getMaxAmplifier() {
            return maxAmplifier != null ? maxAmplifier : effectsDefaultMaxAmplifier;
        }

        public boolean isHideParticles() {
            return hideParticles != null ? hideParticles : effectsDefaultHideParticles;
        }

        @NotNull
        public ExpCostCalculator getExpCostCalculator(int level) {
            if (expCostOverride == null)
                return new ExpCostCalculator.Fixed(Config.nerfExpLevelsPerMinute * level);
            return expCostOverride.get(Math.min(level, expCostOverride.size() - 1));
        }
    }

    private static final BeaconatorLevel BEACONATOR_LEVEL_DISABLED = new BeaconatorLevel(0, null);
    public static BeaconatorLevel getBeaconatorLevel(int level, int selectedLevel) {
        if (selectedLevel == -1)
            return BEACONATOR_LEVEL_DISABLED;
        if (selectedLevel == 0) // unselected
            selectedLevel = level;
        else
            selectedLevel = Math.min(selectedLevel, level);
        if (selectedLevel - 1 >= enchBeaconatorLevels.size())
            return enchBeaconatorLevels.get(enchBeaconatorLevels.size() - 1);
        return enchBeaconatorLevels.get(selectedLevel - 1);
    }

    public static BeaconatorLevel getBeaconatorLevel(int level) {
        return enchBeaconatorLevels.get(level - 1);
    }

    public enum BeaconatorToggleMode {
        TRUE, FALSE, SELECT
    }
}
