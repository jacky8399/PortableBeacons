package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.md_5.bungee.api.ChatColor.*;

public class CommandUtils {
    private static class Completions {
        // Potion effect type autocomplete
        private static final List<String> VALID_MODIFICATIONS = Stream.concat(
                PotionEffectUtils.getValidPotionNames().stream(),
                Stream.of("exp-reduction", "soulbound", "beaconator", "all", "all-positive", "all-negative")
        ).toList();
    }
    public static Stream<String> listModifications(String input, boolean allowVirtual) {
        // try removing equal sign and everything after
        int splitIdx = input.indexOf('=');
        if (splitIdx == -1)
            splitIdx = input.indexOf('*');
        if (splitIdx != -1)
            input = input.substring(0, splitIdx);
        if (input.isEmpty())
            return PotionEffectUtils.getValidPotionNames().stream();
        int maxAmplifier = -1;
        PotionEffectType potion = PotionEffectUtils.parsePotion(input);
        if (potion != null) {
            // valid input, show amplifiers
            Config.PotionEffectInfo info = Config.getInfo(potion);
            maxAmplifier = info.getMaxAmplifier();
        } else if (input.toLowerCase(Locale.ENGLISH).startsWith("all")) {
            maxAmplifier = 9;
        } else if (input.equalsIgnoreCase("exp-reduction")) {
            maxAmplifier = Config.enchExpReductionMaxLevel;
        } else if (input.equalsIgnoreCase("soulbound")) {
            maxAmplifier = Config.enchSoulboundMaxLevel;
        } else if (input.equalsIgnoreCase("beaconator")) {
            maxAmplifier = Config.enchBeaconatorLevels.size();
        }
        if (maxAmplifier != -1) {
            String finalInput = input;
            return IntStream.rangeClosed(allowVirtual ? 0 : 1, maxAmplifier)
                    .mapToObj(i -> finalInput + "=" + i);
        }
        // show potion effects and enchantments
        return Completions.VALID_MODIFICATIONS.stream();
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

    public static Stream<String> listPlayers(@Nullable CommandSender sender) {
        var players = Bukkit.getOnlinePlayers().stream();
        if (sender instanceof Player playerSender) {
            players = players.filter(playerSender::canSee);
        }
        return players.map(Player::getName);
    }

    public static String SELECTOR_PERMISSION = "minecraft.command.selector";
    public static Stream<String> listSelectors(@Nullable CommandSender sender) {
        var players = listPlayers(sender);
        if (sender != null && !sender.hasPermission(SELECTOR_PERMISSION))
            return players;

        var playersList = players.toList();
        var completions = new ArrayList<String>(playersList.size() + 3);
        completions.addAll(playersList);
        completions.add("@a");
        completions.add("@p");
        completions.add("@s");

        return completions.stream();
    }

    @NotNull
    public static BeaconEffects parseEffects(CommandSender sender, String[] input, boolean allowVirtual) {
        boolean seenEqualsPrompt = false;
        int minLevel = allowVirtual ? 0 : 1;

        Map<String, Integer> effects = new LinkedHashMap<>();
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
                    String[] split = s.split(splitChar, 3);
                    Preconditions.checkState(split.length == 2, "Invalid format, correct format is type" + splitChar + "level");
                    potionName = split[0];
                    level = Integer.parseInt(split[1]);
                }
                // Check level
                if (level == 0 && !allowVirtual) // more specific message for when virtual effects are not support
                    throw new IllegalArgumentException("Level 0 given, which is not allowed with this command.");
                if (level < minLevel)
                    throw new IllegalArgumentException("Level " + level + " is negative");
                effects.put(potionName, level);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return BeaconEffects.load(effects, allowVirtual);
    }

    private static final Pattern DESC_SPLIT_PATTERN = Pattern.compile("[{}]");
    public static TextComponent parseHelpDescription(String desc, String label) {
        if (desc.isBlank())
            return new TextComponent();
        // strip trailing newline
        if (desc.charAt(desc.length() - 1) == '\n')
            desc = desc.substring(0, desc.length() - 1);
        String[] components = DESC_SPLIT_PATTERN.split(desc);
        List<BaseComponent> children = new ArrayList<>();
        for (String component : components) {
            // check @tag
            String[] tagSplit = component.split(" ", 2);
            if (tagSplit.length == 2 && !tagSplit[0].isEmpty() && tagSplit[0].charAt(0) == '@') {
                String tag = tagSplit[0].substring(1);
                String input = tagSplit[1];
                switch (tag) {
                    case "color" -> {
                        String[] colorSplit = input.split(" ", 2);
                        ChatColor color = ChatColor.of(colorSplit[0]);
                        String str = colorSplit[1];
                        var textComponent = new TextComponent(str);
                        textComponent.setColor(color);
                        children.add(textComponent);
                    }
                    case "arg" -> {
                        var textComponent = new TextComponent(input);
                        textComponent.setColor(AQUA);
                        textComponent.setHoverEvent(TextUtils.showText(new TextComponent("This is an argument to this command")));
                        children.add(textComponent);
                    }
                    case "command" -> {
                        String command = "/" + label + " " + input;
                        children.add(displayCommandSuggestion(command));
                    }
                    default -> PortableBeacons.INSTANCE.logger.warning("Ignoring invalid @tag " + component);
                }
            } else {
                // simple text
                children.add(new TextComponent(component));
            }
        }
        var textComponent = new TextComponent();
        textComponent.setColor(YELLOW);
        textComponent.setExtra(children);
        return textComponent;
    }

    // Components

    public static BaseComponent displayCommandSuggestion(String command) {
        var textComponent = new TextComponent(command);
        textComponent.setColor(AQUA);
        textComponent.setBold(false);
        textComponent.setUnderlined(true);
        textComponent.setHoverEvent(TextUtils.showText(new TextComponent("Click to copy command to chat box")));
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        var copyComponent = new TextComponent(" [C]");
        copyComponent.setColor(DARK_AQUA);
        copyComponent.setBold(false);
        copyComponent.setUnderlined(false);
        copyComponent.setHoverEvent(TextUtils.showText(new TextComponent("Click to copy command to clipboard")));
        copyComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command));
        textComponent.setExtra(List.of(copyComponent));
        return textComponent;
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
                int pos = Math.min(index, arguments.length - 1);
                String expected = arguments[pos];
                throw new IllegalArgumentException(RED + "Expected " + expected + " at position " + pos + ", got nothing\n" +
                        RED + "Usage: /" + label + " " + usage);
            }
        }

        private IllegalArgumentException wrapException(Exception ex) {
            index--;
            int pos = Math.min(index, arguments.length - 1);
            String expected = arguments[pos];
            return new IllegalArgumentException(RED + "Expected " + expected + " at position " + pos +
                    ", got input " + input[index] + "\n" +
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

            if (!allowEmpty && players.isEmpty()) {
                throw wrapException(new IllegalArgumentException("No player selected by player selector" + selector));
            }

            return players;
        }
    }

}
