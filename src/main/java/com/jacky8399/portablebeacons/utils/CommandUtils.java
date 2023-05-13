package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
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

import static net.md_5.bungee.api.ChatColor.*;

public class CommandUtils {
    private static class Completions {
        // Potion effect type autocomplete
        private static final List<String> VALID_MODIFICATIONS = Stream.concat(
                PotionEffectUtils.getValidPotionNames().stream(),
                Stream.of("exp-reduction", "soulbound", "all", "all-positive", "all-negative")
        ).toList();
    }
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
        } else if (input.toLowerCase(Locale.ENGLISH).startsWith("all")) {
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
        BeaconEffects beaconEffects = new BeaconEffects();
        beaconEffects.expReductionLevel = minLevel - 1; // to ensure no level -1 with item give
        beaconEffects.soulboundLevel = minLevel - 1;
        HashMap<PotionEffectType, Integer> effects = new HashMap<>();

        List<String> skipped = new ArrayList<>();

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

                switch (potionName.toLowerCase(Locale.ENGLISH)) {
                    case "exp-reduction" -> beaconEffects.expReductionLevel = level;
                    case "soulbound" -> beaconEffects.soulboundLevel = level;
                    case "all" -> {
                        for (var effect : PotionEffectType.values()) {
                            effects.put(effect, level);
                        }
                    }
                    case "all-positive" -> {
                        for (var effect : PotionEffectType.values()) {
                            if (!PotionEffectUtils.isNegative(effect)) {
                                effects.put(effect, level);
                            }
                        }
                    }
                    case "all-negative" -> {
                        for (var effect : PotionEffectType.values()) {
                            if (PotionEffectUtils.isNegative(effect)) {
                                effects.put(effect, level);
                            }
                        }
                    }
                    default -> {
                        PotionEffectType type = PotionEffectUtils.parsePotion(potionName);
                        if (type == null)
                            throw new IllegalArgumentException(s + " is not a valid potion effect or enchantment");
                        effects.put(type, level);
                    }
                }
            } catch (IllegalArgumentException e) {
                skipped.add(s + ": " + e.getMessage());
            }
        }
        beaconEffects.setEffects(effects);
        return beaconEffects;
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
            if (tagSplit.length == 2 && tagSplit[0].length() != 0 && tagSplit[0].charAt(0) == '@') {
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
                        textComponent.setHoverEvent(showText(new TextComponent("This is an argument to this command")));
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

    public static BaseComponent displayCommandSuggestion(String command) {
        var textComponent = new TextComponent(command);
        textComponent.setColor(AQUA);
        textComponent.setBold(false);
        textComponent.setUnderlined(true);
        textComponent.setHoverEvent(showText(new TextComponent("Click to copy command to chat box")));
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        var copyComponent = new TextComponent(" [C]");
        copyComponent.setColor(DARK_AQUA);
        copyComponent.setBold(false);
        copyComponent.setUnderlined(false);
        copyComponent.setHoverEvent(showText(new TextComponent("Click to copy command to clipboard")));
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

            if (!allowEmpty && players.size() == 0) {
                throw wrapException(new IllegalArgumentException("No player selected by player selector" + selector));
            }

            return players;
        }
    }

}
