package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public sealed interface ExpCostCalculator {
    /**
     * Calculates the cost for a given item.
     * Note: should NOT be used for calculating crafting cost.
     * @param player The player
     * @param effects The effects
     * @return The experience cost
     */
    int getCost(Player player, @Nullable BeaconEffects effects);

    /**
     * Calculates the cost for a given item.
     * Note: should NOT be used for calculating crafting cost.
     * @param player The player
     * @param stack The beacon
     * @return The experience cost
     */
    default int getCost(Player player, ItemStack stack) {
        return getCost(player, ItemUtils.getEffects(stack));
    }

    /**
     * Calculates the cost for a given item.
     * Note: should NOT be used for purposes other than calculating crafting cost.
     * @param player The player
     * @param left The beacon
     * @param right The sacrificial item
     * @return The experience cost
     */
    int getCost(Player player, ItemStack left, ItemStack right);

    static ExpCostCalculator deserialize(Object object) {
        if (object == null)
            return null;

        if (object instanceof Number number && number.intValue() >= 0) {
            return new Fixed(number.intValue());
        } else if ("dynamic-unrestricted".equals(object)) {
            return DynamicUnrestricted.INSTANCE;
        } else if (object instanceof String str && str.startsWith("dynamic")) {
            return Dynamic.deserialize(str);
        } else {
            String string = object.toString();
            int level;
            try {
                level = Integer.parseInt(string);
                return new Fixed(level);
            } catch (NumberFormatException ignored) {
                try {
                    return new Placeholder(string);
                } catch (Exception ex) {
                    throw new IllegalArgumentException(object + " is not a valid exp-cost", ex);
                }
            }
        }
    }

    Object serialize();

    record Fixed(int level) implements ExpCostCalculator {
        public Fixed {
            if (level < 0 || level > 1000000)
                throw new IllegalArgumentException("level must be between 0-1000000");
        }
        
        @Override
        public int getCost(Player player, BeaconEffects effects) {
            return level;
        }

        @Override
        public int getCost(Player player, ItemStack left, ItemStack right) {
            return level;
        }

        @Override
        public Object serialize() {
            return level;
        }
    }

    final class DynamicUnrestricted implements ExpCostCalculator {
        public static final DynamicUnrestricted INSTANCE = new DynamicUnrestricted();

        @Override
        public int getCost(Player player, @Nullable BeaconEffects effects) {
            if (effects == null) return 0;
            int cost = 0;
            var disabled = effects.getDisabledEffects();
            for (var entry : effects.getEffects().entrySet()) {
                if (disabled.contains(entry.getKey()))
                    continue;
                int level = entry.getValue();
                if (level > 20)
                    return -1;
                cost += 1 << level;
            }
            if (effects.expReductionLevel > 20 || effects.soulboundLevel > 20)
                return -1;
            cost += 1 << effects.expReductionLevel;
            cost += 1 << effects.soulboundLevel;
            cost += 1 << effects.beaconatorSelectedLevel;
            return cost;
        }

        private static int getRawCost(@Nullable BeaconEffects effects) {
            if (effects == null) return 0;
            int cost = 0;
            for (var entry : effects.getEffects().entrySet()) {
                int level = entry.getValue();
                if (level > 20)
                    return -1;
                cost += 1 << level;
            }
            if (effects.expReductionLevel > 20 || effects.soulboundLevel > 20)
                return -1;
            cost += 1 << effects.expReductionLevel;
            cost += 1 << effects.soulboundLevel;
            cost += 1 << effects.beaconatorLevel;
            return cost;
        }

        @Override
        public int getCost(Player player, ItemStack left, ItemStack right) {
            BeaconEffects leftEffects = ItemUtils.getEffects(left);
            BeaconEffects rightEffects = ItemUtils.getEffects(right);
            if (leftEffects == null)
                return 0;
            int cost = getRawCost(leftEffects);
            int otherCost = rightEffects != null ? getRawCost(rightEffects) : 0;
            if (cost == -1 || otherCost == -1)
                return -1;
            return cost + otherCost;
        }

        @Override
        public Object serialize() {
            return "dynamic-unrestricted";
        }
    }

    record Dynamic(int maxLevel) implements ExpCostCalculator {
        private static final DynamicUnrestricted unrestricted = DynamicUnrestricted.INSTANCE;
        public static final Dynamic VANILLA = new Dynamic(39);

        @Override
        public int getCost(Player player, @Nullable BeaconEffects effects) {
            int cost = unrestricted.getCost(player, effects);
            return cost > maxLevel ? -1 : cost;
        }

        @Override
        public int getCost(Player player, ItemStack left, ItemStack right) {
            int cost = unrestricted.getCost(player, left, right);
            return cost > maxLevel ? -1 : cost;
        }

        @Override
        public Object serialize() {
            return maxLevel == 39 ? "dynamic" : "dynamic-max" + maxLevel;
        }

        public static Dynamic deserialize(String input) {
            if ("dynamic".equals(input)) {
                return VANILLA;
            } else if (input.startsWith("dynamic-max")) {
                String substring = input.substring(11);
                return new Dynamic(Integer.parseInt(substring));
            }
            throw new IllegalArgumentException(input);
        }
    }

    record Placeholder(String placeholders) implements ExpCostCalculator {

        private static final DynamicUnrestricted unrestricted = DynamicUnrestricted.INSTANCE;

        public Placeholder {
            if (!Config.placeholderApi) throw new IllegalStateException("PlaceholderAPI not found");
        }

        @Override
        public int getCost(Player player, @Nullable BeaconEffects effects) {
            if (Config.placeholderApi) {
                String string = placeholders;
                if (string.contains("{_dynamic}"))
                    string = string.replace("{_dynamic}", Integer.toString(unrestricted.getCost(player, effects)));
                string = PlaceholderAPI.setPlaceholders(player, string);
                try {
                    return Integer.parseInt(string);
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
            return -1;
        }

        @Override
        public int getCost(Player player, ItemStack left, ItemStack right) {
            if (Config.placeholderApi) {
                String string = placeholders;
                if (string.contains("{_dynamic}"))
                    string = string.replace("{_dynamic}", Integer.toString(unrestricted.getCost(player, left, right)));
                string = PlaceholderAPI.setPlaceholders(player, string);
                try {
                    if (string.indexOf('.') > -1) {
                        double value = Double.parseDouble(string);
                        return Double.isFinite(value) ? (int) value : -1;
                    }
                    return Integer.parseInt(string);
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
            return -1;
        }

        @Override
        public Object serialize() {
            return placeholders;
        }
    }
}
