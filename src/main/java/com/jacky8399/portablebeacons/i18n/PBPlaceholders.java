package com.jacky8399.portablebeacons.i18n;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public class PBPlaceholders {
    public record PlaceholderComponent(Component component) implements Placeholder {
        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            return component;
        }
    }


    // <...>
    // <...:0.#>
    public record PlaceholderFormat(double value, Function<Locale, NumberFormat> defaultFormat) implements Placeholder {
        public static PlaceholderFormat ofInt(double value) {
            return new PlaceholderFormat(value, NumberFormat::getIntegerInstance);
        }

        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            NumberFormat format = tag.args().length != 0 ? new DecimalFormat(tag.args()[0]) : defaultFormat.apply(context.locale());
            return Component.text(format.format(value));
        }
    }

    // <...>
    // <...:number>
    public record PlaceholderLevel(int level, boolean showLevelOne) implements Placeholder {
        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            String[] args = tag.args();
            if (args.length == 1 && "number".equals(args[0]))
                return Component.text(level);
            if (!showLevelOne && level == 1)
                return Component.empty();
            return level <= 10 ?
                    Component.translatable("enchantment.level." + level) :
                    Component.text(level);
        }
    }

    // <...>
    // <...|<reduction/multiplier>>
    // <...|<reduction/multiplier>|<number/percentage/format>>
    public record PlaceholderExpReduction(double expMultiplier) implements Placeholder {
        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            String[] args = tag.args();
            boolean isMultiplier = false;
            NumberFormat format;

            if (args.length >= 1) {
                isMultiplier = "multiplier".equals(args[0]);
            }
            if (args.length == 2) {
                format = switch (args[1]) {
                    case "number" -> NumberFormat.getNumberInstance(context.locale());
                    case "percentage" -> NumberFormat.getPercentInstance(context.locale());
                    default -> new DecimalFormat(args[1]);
                };
            } else {
                format = NumberFormat.getPercentInstance(context.locale());
            }
            double actualMultiplier = isMultiplier ? expMultiplier : 1 - expMultiplier;
            return Component.text(format.format(actualMultiplier));
        }
    }

    public record PlaceholderUUID(@Nullable UUID uuid, @Nullable String playerName, String fallback) implements Placeholder {
        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            String[] args = tag.args();
            String fallback = args.length >= 2 && "name".equals(args[0]) ? args[1] : this.fallback;
            if (uuid == null)
                return Component.text(fallback);
            if (args.length >= 1 && "uuid".equals(args[0])) {
                return Component.text(uuid.toString());
            }
            return Component.text(playerName != null ? playerName : fallback);
        }
    }

    public record PlaceholderEnchantment(int level) implements Placeholder {
        @Override
        public Component apply(PlaceholderTagInfo tag, Context context) {
            if (tag.args().length < 1)
                throw new IllegalArgumentException("Expected enchantment name");
            return I18n.formatEnchantment(tag.args()[1], level, context);
        }
    }

}
