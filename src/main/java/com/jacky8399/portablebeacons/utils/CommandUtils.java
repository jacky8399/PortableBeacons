package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Entity;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.md_5.bungee.api.ChatColor.RED;
import static net.md_5.bungee.api.ChatColor.YELLOW;

public class CommandUtils {
    // Potion effect type autocomplete
    private static final List<String> VALID_MODIFICATIONS = Stream.concat(
            PotionEffectUtils.getValidPotionNames().stream(),
            Stream.of("exp-reduction", "soulbound", "all")
    ).toList();
    public static Stream<String> listModifications(String input, boolean allowVirtual) {
        if (input.isEmpty())
            return PotionEffectUtils.getValidPotionNames().stream();
        // try removing equal sign and everything after
        int splitIdx = input.indexOf('=');
        if (splitIdx == -1)
            splitIdx = input.indexOf('*');
        if (splitIdx != -1)
            input = input.substring(0, splitIdx);
        int maxAmplifier = -1;
        PotionEffectType potion = PotionEffectUtils.parsePotion(input);
        if (potion != null) {
            // valid input, show amplifiers
            Config.PotionEffectInfo info = Config.getInfo(potion);
            maxAmplifier = info.getMaxAmplifier();
        } else if (input.equalsIgnoreCase("all")) {
            maxAmplifier = 9;
        } else if (input.equalsIgnoreCase("exp-reduction")) {
            maxAmplifier = Config.enchExpReductionMaxLevel;
        } else if (input.equalsIgnoreCase("soulbound")) {
            maxAmplifier = Config.enchSoulboundMaxLevel;
        }
        if (maxAmplifier != -1) {
            String finalInput = input;
            return IntStream.rangeClosed(allowVirtual ? 0 : 1, maxAmplifier)
                    .mapToObj(i -> finalInput + "=" + i);
        }
        // show potion effects and enchantments
        return VALID_MODIFICATIONS.stream();
    }


    private static final Pattern FILTER_FORMAT = Pattern.compile("^([a-z_:]+)(?:(=|<>|<|<=|>|>=)(\\d+)?)?$");
    public static Stream<String> listFilter(String input) {
        Matcher matcher = FILTER_FORMAT.matcher(input);
        if (!matcher.matches()) {
            return PotionEffectUtils.getValidPotionNames().stream();
        }
        String effectName = matcher.group(1);
        if (effectName.isBlank())
            return PotionEffectUtils.getValidPotionNames().stream();
        PotionEffectType type = PotionEffectUtils.parsePotion(matcher.group(1));
        if (type == null) {
            // <effect>
            return PotionEffectUtils.getValidPotionNames().stream();
        }
        if (matcher.groupCount() == 1) {
            // effect<op>
            return BeaconEffectsFilter.Operator.STRING_MAP.keySet().stream().map(op -> effectName + op);
        } else {
            // check if valid potion and operator
            String opString = matcher.group(2);
            BeaconEffectsFilter.Operator operator = BeaconEffectsFilter.Operator.getByString(opString);
            if (operator != null) {
                // effect<op> + effect op <level>
                var additionalOperators = Arrays.stream(BeaconEffectsFilter.Operator.values())
                        .filter(op -> op.operator.startsWith(opString))
                        .map(op -> effectName + op.operator)
                        .toList();
                return Stream.concat(
                        additionalOperators.stream(),
                        IntStream.rangeClosed(0, Config.getInfo(type).getMaxAmplifier())
                                .mapToObj(num -> effectName + matcher.group(2) + num)
                );
            }
            return Arrays.stream(BeaconEffectsFilter.Operator.values()).map(op -> effectName + op.operator);
        }
    }

    public static Stream<String> listExpCosts(String input) {
        if (input.startsWith("dynamic-max")) {
            return IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> "dynamic-max" + i);
        } else {
            return Stream.of("0", "1", "dynamic", "dynamic-unrestricted", "dynamic-max");
        }
    }

    @NotNull
    public static BeaconEffects parseEffects(CommandSender sender, String[] input, boolean allowVirtual) {
        boolean seenEqualsPrompt = false;
        int minLevel = allowVirtual ? 0 : 1;
        BeaconEffects beaconEffects = new BeaconEffects();
        beaconEffects.expReductionLevel = minLevel - 1; // to ensure no level -1 with item give
        beaconEffects.soulboundLevel = minLevel - 1;
        HashMap<PotionEffectType, Integer> effects = new HashMap<>();
        for (String s : input) {
            try {
                String potionName = s;
                int level = 1;
                if (s.indexOf('=') > -1 || s.indexOf('*') > -1) {
                    String splitChar = s.indexOf('=') > -1 ? "=" : "\\*";
                    if (!"=".equals(splitChar) && !seenEqualsPrompt) {
                        seenEqualsPrompt = true;
                        sender.sendMessage(YELLOW + "Consider using type=level for consistency with other formats.");
                    }
                    String[] split = s.split(splitChar);
                    Preconditions.checkState(split.length == 2, "Invalid format, correct format is type" + splitChar + "level");
                    potionName = split[0];
                    level = Integer.parseInt(split[1]);
                }

                if (potionName.equalsIgnoreCase("exp-reduction")) {
                    beaconEffects.expReductionLevel = level;
                    continue;
                } else if (potionName.equalsIgnoreCase("soulbound")) {
                    beaconEffects.soulboundLevel = level;
                    continue;
                } else if (potionName.equalsIgnoreCase("all")) {
                    for (PotionEffectType potionEffectType : PotionEffectType.values()) {
                        effects.put(potionEffectType, level);
                    }
                    continue;
                }
                PotionEffectType type = PotionEffectUtils.parsePotion(potionName);
                if (type == null)
                    throw new IllegalArgumentException(s + " is not a valid potion effect or enchantment");
                Preconditions.checkArgument(level >= minLevel, "Level is negative");
                effects.put(type, level);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(RED + String.format("Skipped '%s' as it is not a valid potion effect (%s)", s, e.getMessage()));
            }
        }
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }

    // Components

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
            var langKey = (material.isBlock() ? "block." : "item.") + materialKey.replace(':', '.');
            component = new TranslatableComponent(langKey);
        }
        component.setHoverEvent(hover);
        component.setColor(ChatColor.YELLOW);
        return component;
    }

    public static ArgumentParser parse(CommandSender sender, String label, String usage, String[] args, int fromIndex) {
        return new ArgumentParser(sender, label, usage, args, fromIndex);
    }

    public static class ArgumentParser {
        private final CommandSender sender;
        private final String label;
        private String usage;
        private String[] arguments;
        private final String[] input;
        private int index;

        private ArgumentParser(CommandSender sender, String label, String usage, String[] input, int fromIndex) {
            this.sender = sender;
            this.label = label;
            updateUsage(usage);
            this.input = input;
            this.index = fromIndex;
        }

        private void checkSize() {
            if (index == input.length) {
                String expected = arguments[index];
                throw new IllegalArgumentException(RED + "Expected " + expected + " at position " + index + "\n" +
                                                   RED + "Usage: /" + label + " " + usage);
            }
        }

        private IllegalArgumentException wrapException(Exception ex) {
            index--;
            String expected = arguments.length > index ? arguments[index] : arguments[arguments.length - 1];
            return new IllegalArgumentException(RED + "Expected " + expected + " at position " + index + ", " +
                                               "but got input " + input[index] + "\n" +
                                               RED + "Usage: /" + label + " " + usage, ex);
        }

        public IllegalArgumentException throwUsage() {
            return wrapException(null);
        }

        public IllegalArgumentException throwUsage(String cause) {
            return wrapException(new IllegalArgumentException(cause));
        }

        public void updateUsage(String usage) {
            this.usage = usage;
            this.arguments = usage.split(" ");
        }

        public boolean hasNext() {
            return index < input.length;
        }

        public String popWord() {
            checkSize();
            return input[index++];
        }

        public String[] getRemainingInput() {
            if (index == input.length)
                return new String[0];
            return Arrays.copyOfRange(input, index, input.length);
        }

        // ensures that there is at least one unparsed argument
        public String[] popRemainingInput() {
            checkSize();
            return Arrays.copyOfRange(input, index, input.length);
        }


        public String popRest() {
            String result = String.join(" ", popRemainingInput());
            index = input.length;
            return result;
        }

        public int popInt() {
            checkSize();
            try {
                return Integer.parseInt(input[index++]);
            } catch (NumberFormatException ex) {
                throw wrapException(ex);
            }
        }

        public boolean popBoolean() {
            checkSize();
            return Boolean.parseBoolean(input[index++]);
        }

        public ItemStack popItemStack() {
            checkSize();
            ItemStack stack;
            try {
                stack = Bukkit.getItemFactory().createItemStack(input[index++]);
                if (hasNext()) {
                    int amount = Integer.parseInt(input[index++]);
                    if (amount <= 0)
                        throw new IllegalArgumentException("Item count must be > 0");
                    stack.setAmount(amount);
                }
            } catch (IllegalArgumentException ex) {
                throw wrapException(ex);
            }
            return stack;
        }

        public ItemStack tryPopItemStack() {
            int curIndex = index;
            ItemStack stack;
            try {
                stack = popItemStack();
            } catch (Exception ex) {
                if (!(sender instanceof Player player)) {
                    if (input.length >= curIndex + 1) // provided invalid item
                        throw ex;
                    else
                        throw wrapException(new IllegalArgumentException("Console must provide item"));
                }
                index = curIndex + 2;
                stack = player.getInventory().getItemInMainHand().clone();
            }
            return stack;
        }

        public List<Player> popPlayers(boolean allowEmpty) {
            String selector = popWord();
            List<Player> players;
            if (sender.hasPermission("minecraft.command.selector")) {
                players = Bukkit.selectEntities(sender, selector).stream()
                        .map(entity -> entity instanceof Player player ? player : null)
                        .filter(Objects::nonNull)
                        .toList();
            } else {
                Player player = Bukkit.getPlayer(selector);
                players = player != null ? List.of(player) : List.of();
            }

            if (!allowEmpty && players.size() == 0) {
                throw wrapException(new IllegalArgumentException("No player selected by player selector" + selector));
            }

            return players;
        }
    }

}
