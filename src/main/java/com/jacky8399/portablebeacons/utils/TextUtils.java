package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.Config;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Entity;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class TextUtils {
    public static final DecimalFormat TWO_DP = new DecimalFormat("0.##");
    public static final DecimalFormat ONE_DP = new DecimalFormat("0.#");
    public static final NumberFormat INT = NumberFormat.getIntegerInstance();

    // reject quotation marks to avoid parsing JSON
    public static final Pattern PLACEHOLDER = Pattern.compile("(\\s)?\\{([^\"]+?)}");

    /**
     * Joins a stream of componenets with the given separator
     * @param separator The separator
     * @return A collector
     */
    public static Collector<BaseComponent, ?, BaseComponent[]> joiningComponents(BaseComponent separator) {
        return Collectors.collectingAndThen(Collectors.toList(), list -> {
            ComponentBuilder builder = new ComponentBuilder();
            var iter = list.iterator();
            while (iter.hasNext()) {
                builder.append(iter.next(), ComponentBuilder.FormatRetention.NONE);
                if (iter.hasNext())
                    builder.append(separator, ComponentBuilder.FormatRetention.NONE);
            }
            return builder.create();
        });
    }

    public static String replacePlaceholders(String input, ContextLevel level, Map<String, ? extends Context> contexts) {
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

    public static BaseComponent[] replaceComponentPlaceholders(String input, Map<String, ? extends ComponentContext> contexts) {
        if (input.indexOf('{') == -1)
            return TextComponent.fromLegacyText(input);

        ComponentBuilder builder = new ComponentBuilder();
        Matcher matcher = PLACEHOLDER.matcher(input);
        int last = 0;
        while (matcher.find()) {
            if (last != matcher.start()) {
                builder.append(TextComponent.fromLegacyText(input.substring(last, matcher.start())));
            }
            String[] args = matcher.group(2).split("\\|");
            String contextName = args[0];
            ComponentContext context = contexts.get(contextName);
            if (context == null) {
                // ignore
                builder.append(TextComponent.fromLegacyText(matcher.group()));
                last = matcher.end();
                continue;
            }
            args = Arrays.copyOfRange(args, 1, args.length);
            BaseComponent[] replacement = context.doComponentReplacement(args);
            builder.append(replacement);
            last = matcher.end();
        }
        // append tail
        builder.append(TextComponent.fromLegacyText(input.substring(last)));
        return builder.create();
    }

    public static String getEnchantmentName(String fullName) {
        return fullName.split("\\{", 2)[0];
    }

    // only for when placeholders don't matter
    public static String formatEnchantment(String fullName, int level) {
        return getEnchantmentName(fullName) + level;
    }

    public static List<BaseComponent> getLoreFromLegacyString(String[] lines) {
        List<BaseComponent> list = new ArrayList<>(lines.length);
        for (String line : lines) {
            TextComponent text = new TextComponent(TextComponent.fromLegacyText(line));
            text.setItalic(false);
            list.add(text);
        }
        return list;
    }

    public static List<BaseComponent> formatExpReduction(int level) {
        String enchName = TextUtils.replacePlaceholders(Config.enchExpReductionName,
                new TextUtils.ContextLevel(level),
                Map.of("exp-reduction", new TextUtils.ContextExpReduction(level))
        );
        return getLoreFromLegacyString(enchName.split("\n"));
    }

    public static List<BaseComponent> formatSoulbound(int level, UUID soulboundOwner) {
        String enchName = TextUtils.replacePlaceholders(Config.enchSoulboundName,
                new TextUtils.ContextLevel(level),
                Map.of("soulbound-owner", new TextUtils.ContextUUID(soulboundOwner, "???"))
        );
        return getLoreFromLegacyString(enchName.split("\n"));
    }

    public static List<BaseComponent> formatBeaconator(int level, int selectedLevel) {
        // fetch clamped radii
        Config.BeaconatorLevel realLevel = Config.getBeaconatorLevel(level, level);
        Config.BeaconatorLevel realSelectedLevel = Config.getBeaconatorLevel(level, selectedLevel);

        String enchName = TextUtils.replacePlaceholders(Config.enchBeaconatorName,
                new ContextLevel(selectedLevel <= 0 ? level : selectedLevel),
                Map.of("radius", new ContextFormat(realSelectedLevel.radius(), ONE_DP),
                        "max-radius", new ContextFormat(realLevel.radius(), ONE_DP))
        );
        if (selectedLevel == -1)
            enchName = "" + ChatColor.GRAY + ChatColor.STRIKETHROUGH + ChatColor.stripColor(enchName);
        return getLoreFromLegacyString(enchName.split("\n"));
    }

    public static TextComponent toComponent(String legacy) {
        return new TextComponent(TextComponent.fromLegacyText(legacy));
    }

    public static HoverEvent showText(BaseComponent... components) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(components));
    }

    public static HoverEvent showItem(Material material, int amount, @Nullable String itemTag) {
        var hoverContent = new Item(material.getKey().toString(), amount, itemTag != null ? ItemTag.ofNbt(itemTag) : null);
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverContent);
    }

    public static HoverEvent showItem(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return showItem(stack.getType(), stack.getAmount(), meta != null && stack.hasItemMeta() ? meta.getAsString() : null);
    }

    public static HoverEvent showEntity(EntityType entityType, UUID uuid, @Nullable BaseComponent displayName) {
        var hoverContent = new Entity(entityType.getKey().toString(), uuid.toString(), displayName);
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, hoverContent);
    }

    public static BaseComponent displayItem(ItemStack stack) {
        var material = stack.getType();
        var materialKey = material.getKey().toString();
        var meta = stack.getItemMeta();
        var hover = showItem(material, stack.getAmount(),
                meta != null && stack.hasItemMeta() ? meta.getAsString() : null);
        BaseComponent component;
        if (meta != null && meta.hasDisplayName()) {
            component = new TextComponent(meta.getDisplayName());
        } else {
            String translationKey;
            try {
                translationKey = material.getItemTranslationKey();
            } catch (NoSuchMethodError ignored) { // bruh
                translationKey = "item." + materialKey.replace(':', '.');
            }
            component = new TranslatableComponent(translationKey);
        }
        component.setHoverEvent(hover);
        component.setColor(ChatColor.YELLOW);
        return component;
    }

    public interface Context extends ComponentContext {
        default boolean shouldRemovePrecedingSpace(String... args) {
            return false;
        }

        String doReplacement(String... args);

        @Override
        default BaseComponent[] doComponentReplacement(String... args) {
            return new BaseComponent[] {new TextComponent(doReplacement(args))};
        }
    }

    public interface ComponentContext {
        BaseComponent[] doComponentReplacement(String... args);
    }

    public record ContextFormat(double value, NumberFormat defaultFormat) implements Context {
        public static ContextFormat ofInt(double value) {
            return new ContextFormat(value, INT);
        }

        @Override
        public String doReplacement(String... args) {
            NumberFormat format = args.length != 0 ? new DecimalFormat(args[0]) : defaultFormat;
            return format.format(value);
        }
    }

    public record ContextLevel(int level, boolean showLevelOne) implements Context {

        public ContextLevel(int level) {
            this(level, false);
        }

        @Override
        public String doReplacement(String... args) {
            return args.length == 1 && "number".equals(args[0]) ?
                    Integer.toString(level) :
                    !showLevelOne && level == 1 ? "" : PotionEffectUtils.toRomanNumeral(level);
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
