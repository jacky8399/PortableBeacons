package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.utils.TextUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigMigrations {

    public record MigrationLogger(Logger logger, List<String> migrated, List<String> needsAttention) {}

    @FunctionalInterface
    public interface Migrator {
        void migrate(@NotNull MigrationLogger logger, @NotNull FileConfiguration config);
    }

    public static final class V1 {

        public static final Pattern EXP_USAGE_PATTERN = Pattern.compile("\\{0(,number(,.+?)?)?}", Pattern.CASE_INSENSITIVE);

        public static void migrateExpUsageMessage(MigrationLogger logger, FileConfiguration config) {
            String expUsageMsg = config.getString("beacon-item.effects-toggle.exp-usage-message");
            if (expUsageMsg != null && expUsageMsg.contains("{0")) {
                expUsageMsg = EXP_USAGE_PATTERN.matcher(expUsageMsg).replaceAll((match) -> {
                    if (match.group(1) == null || match.group(2) == null) { // {0} or {0,number}
                        return "{usage}";
                    } else { // {0,number,format}
                        return "{usage|" + match.group(2).substring(1) + "}";
                    }
                });

                config.set("beacon-item.effects-toggle.exp-usage-message", expUsageMsg);
                logger.migrated.add("beacon-item.effects-toggle.exp-usage-message format change");
            }
        }

        public static void migrateEffectNames(MigrationLogger logger, FileConfiguration config) {
            ConfigurationSection section = config.getConfigurationSection("effects");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (key.equals("default")) continue;

                    ConfigurationSection effect = Objects.requireNonNull(section.getConfigurationSection(key));
                    String name = effect.getString("name");
                    if (name == null) continue;
                    Matcher matcher = TextUtils.PLACEHOLDER.matcher(name);
                    String nameOverride;
                    String formatOverride; // Roman numerals by default
                    if (!matcher.find()) { // doesn't have placeholders
                        nameOverride = name;
                        formatOverride = null;
                    } else {
                        // {level} vs {level|number}
                        formatOverride = matcher.group().split("\\|", 2).length != 1 ? "number" : null;
                        matcher.reset(); // replaceAll calls find()
                        nameOverride = matcher.replaceAll("");
                    }
                    // detect the case where the old name is some substring of the key
                    // e.g. minecraft:speed.name: Speed
                    if (key.toLowerCase(Locale.ENGLISH).replace('_', ' ').contains(
                            ChatColor.stripColor(Config.translateColor(nameOverride)).toLowerCase(Locale.ENGLISH))) {
                        nameOverride = null;
                    }

                    effect.set("name", null);

                    String oldPath = "effects." + key + ".name -> ";
                    if (formatOverride != null) {
                        effect.set("format-override", formatOverride);
                        effect.set("name-override", nameOverride);
                        logger.migrated.add(oldPath + (nameOverride != null ? "name-override, format-override" : "format-override"));
                    } else if (nameOverride != null) {
                        effect.set("name-override", nameOverride);
                        logger.migrated.add(oldPath + "name-override");
                    } else {
                        if (effect.getKeys(false).isEmpty())
                            section.set(key, null);
                        logger.migrated.add(oldPath + "REMOVED");
                    }
                }
            }
        }
    }
}
