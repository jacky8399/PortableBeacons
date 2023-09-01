package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.Config;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class TextUtils {
    // reject quotation marks to avoid parsing JSON
    public static final Pattern PLACEHOLDER = Pattern.compile("(\\s)?\\{([^\"]+?)}");

    public static TextComponent toComponent(ItemStack stack) {
        // I miss Adventure :(
        Material material = stack.getType();
        String key = material.getKey().toString();
        ItemMeta meta = stack.getItemMeta();
        BaseComponent[] components;
        if (meta.hasDisplayName()) {
            components = TextComponent.fromLegacyText(meta.getDisplayName(), ChatColor.AQUA);
        } else {
            String translationKey;
            try {
                translationKey = material.getItemTranslationKey();
            } catch (NoSuchMethodError ignored) { // bruh
                translationKey = "item." + key.replace(':', '.');
            }
            components = new BaseComponent[] {new TranslatableComponent(translationKey)};
        }
        var realComponent = new TextComponent();
        realComponent.setColor(ChatColor.AQUA);
        var realExtra = new ArrayList<BaseComponent>(components.length + 2);
        realExtra.add(new TextComponent("["));
        realExtra.addAll(Arrays.asList(components));
        realExtra.add(new TextComponent("]"));
        realComponent.setExtra(realExtra);
        realComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item(key, stack.getAmount(), ItemTag.ofNbt(meta.getAsString()))));
        return realComponent;
    }

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
