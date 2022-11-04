package com.jacky8399.portablebeacons;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.utils.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.md_5.bungee.api.ChatColor.*;

public class CommandPortableBeacons implements TabExecutor {
    private static final String COMMAND_PERM = "portablebeacons.command.";
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        Stream<String> tabCompletion = tabComplete(sender, args);
        if (tabCompletion == null)
            return Collections.emptyList();
        String input = args[args.length - 1];
        return tabCompletion.filter(completion -> completion.startsWith(input)).collect(Collectors.toList());
    }

    private static final Pattern FILTER_FORMAT = Pattern.compile("^([a-z_]+)(?:(=|<>|<|<=|>|>=)(\\d+)?)?$");
    private Stream<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return Stream.of("setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect", "toggle")
                    .filter(subcommand -> sender.hasPermission(COMMAND_PERM + subcommand));
        } else if (args[0].equalsIgnoreCase("item") && sender.hasPermission(COMMAND_PERM + "item")) {
            String operationArg = args[1].split("-")[0];
            if (args.length == 2) {
                String[] operations = {"give", "add", "remove", "set", "filter", "setowner"};
                String[] flags = {"", "-silently", "-modify-all", "-silently-modify-all", "-modify-all-silently"};
                List<String> completions = new ArrayList<>();
                for (String operation : operations) {
                    // check perm
                    if (!sender.hasPermission(COMMAND_PERM + "item." + operation))
                        continue;
                    // flags
                    if (operation.equalsIgnoreCase(operationArg)) {
                        for (String flag : flags) {
                            if ((operation + flag).startsWith(args[1])) {
                                completions.add(operation);
                            }
                        }
                    } else if (operation.startsWith(args[1])) {
                        completions.add(operation);
                    }
                }
                return completions.stream();
            } else if (args.length == 3) {
                return Stream.concat(Stream.of("@a", "@s", "@p"), Bukkit.getOnlinePlayers().stream().map(Player::getName));
            }
            // Enchantment autocomplete
            if (operationArg.startsWith("setowner") && args.length == 4) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName);
            } else if (operationArg.startsWith("filter")) { // filter autocomplete
                if (args.length == 4) {
                    return Stream.of("allow", "block");
                } else {
                    String input = args[args.length - 1];
                    Matcher matcher = FILTER_FORMAT.matcher(input);
                    if (!matcher.matches()) {
                        return PotionEffectUtils.getValidPotionNames().stream();
                    }
                    Optional<PotionEffectType> type = PotionEffectUtils.parsePotion(matcher.group(1));
                    if (!type.isPresent()) {
                        return PotionEffectUtils.getValidPotionNames().stream();
                    }
                    if (matcher.groupCount() == 1) {
                        return BeaconEffectsFilter.Operator.STRING_MAP.keySet().stream().map(op -> matcher.group(1) + op);
                    } else {
                        // check if valid potion and operator
                        BeaconEffectsFilter.Operator operator = BeaconEffectsFilter.Operator.getByString(matcher.group(2));
                        if (operator != null) {
                            return IntStream.rangeClosed(0, Config.getInfo(type.get()).getMaxAmplifier())
                                    .mapToObj(num -> matcher.group(1) + matcher.group(2) + num);
                        }
                        return Arrays.stream(BeaconEffectsFilter.Operator.values()).map(op -> matcher.group(1) + op.operator);
                    }
                }
            }

            // Potion effect type autocomplete
            String input = args[args.length - 1];
            // try removing equal sign and everything after
            int splitIdx = input.indexOf('=');
            if (splitIdx == -1)
                splitIdx = input.indexOf('*');
            if (splitIdx != -1)
                input = input.substring(0, splitIdx);
            int maxAmplifier = -1;
            Optional<PotionEffectType> optionalPotion = PotionEffectUtils.parsePotion(input);
            if (optionalPotion.isPresent()) {
                // valid input, show amplifiers
                Config.PotionEffectInfo info = Config.getInfo(optionalPotion.get());
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
                return IntStream.rangeClosed("give".equalsIgnoreCase(args[1]) ? 1 : 0, maxAmplifier)
                        .mapToObj(i -> finalInput + "=" + i);
            }
            // show potion effects and enchantments
            return Stream.concat(
                    PotionEffectUtils.getValidPotionNames().stream(),
                    Stream.of("exp-reduction", "soulbound", "all")
            );
        } else if (args[0].equalsIgnoreCase("inspect") && args.length == 2 &&
                sender.hasPermission(COMMAND_PERM + "inspect")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission(COMMAND_PERM + "toggle")) {
            if (args.length == 2) {
                return TOGGLES.keySet().stream()
                        .filter(toggle -> sender.hasPermission(COMMAND_PERM + "toggle." + toggle));
            } else if (args.length == 3) {
                return Stream.of("true", "false");
            }
        }
        return null;
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
                if (s.contains("=") || s.contains("*")) {
                    String splitChar = s.contains("=") ? "=" : "\\*";
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
                PotionEffectType type = PotionEffectUtils.parsePotion(potionName)
                        .orElseThrow(()->new IllegalArgumentException(s + " is not a valid potion effect or enchantment"));
                Preconditions.checkArgument(level >= minLevel, "Level is negative");
                effects.put(type, level);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(RED + String.format("Skipped '%s' as it is not a valid potion effect (%s)", s, e.getMessage()));
            }
        }
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        boolean result = sender.hasPermission(COMMAND_PERM + permission);
        if (!result)
            sender.sendMessage(RED + "You don't have permission to do this!");
        return result;
    }

    private boolean promptUsage(CommandSender sender, String label, String usage) {
        sender.sendMessage(RED + "Usage: /" + label + " " + usage);
        return true;
    }

    private static final ImmutableMap<String, Field> TOGGLES;
    static {
        ImmutableMap.Builder<String, Field> builder = ImmutableMap.builder();
        Class<Config> clazz = Config.class;
        try {
            builder.put("ritual", clazz.getField("ritualEnabled"));
            builder.put("anvil-combination", clazz.getField("anvilCombinationEnabled"));
            builder.put("toggle-gui", clazz.getField("effectsToggleEnabled"));
            builder.put("creation-reminder", clazz.getField("creationReminder"));
            builder.put("world-placement", clazz.getField("placementEnabled"));
            builder.put("world-pickup", clazz.getField("pickupEnabled"));
            TOGGLES = builder.build();
        } catch (Exception e) {
            throw new Error("Can't find toggleable field??", e);
        }
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (!checkPermission(sender, "info"))
                return true;
            sender.sendMessage(GREEN + "You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
            sender.sendMessage(GRAY + "WorldGuard integration: " + Config.worldGuard + ", WorldGuard detected: " + PortableBeacons.INSTANCE.worldGuardInstalled);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                if (!checkPermission(sender, "reload"))
                    return true;
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(GREEN + "Configuration reloaded.");
                break;
            }
            case "saveconfig": {
                if (!checkPermission(sender, "saveconfig"))
                    return true;
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "Configuration saved to file.");
                break;
            }
            case "toggle": {
                if (!checkPermission(sender, "toggle"))
                    return true;
                if (args.length < 2)
                    return promptUsage(sender, label,
                            "toggle <toggleName> [true/false]");
                Optional<Boolean> value = args.length == 3 ?
                        Optional.of(args[2].equalsIgnoreCase("true")) : Optional.empty();
                boolean newValue;
                String toToggle = args[1].toLowerCase(Locale.ROOT);
                Field field = TOGGLES.get(toToggle);
                if (field == null) {
                    sender.sendMessage(RED + "Invalid toggle option!");
                    return true;
                }
                if (!checkPermission(sender, "toggle." + toToggle))
                    return true;
                try {
                    newValue = value.orElseGet(()-> {
                        try {
                            return !field.getBoolean(null);
                        } catch (Exception e) {
                            throw new Error(e);
                        }
                    });
                    field.setBoolean(null, newValue);
                } catch (Exception e) {
                    sender.sendMessage(RED + "Couldn't toggle value");
                    return true;
                }
                sender.sendMessage(YELLOW + "Temporarily set " + GREEN + args[1] + YELLOW + " to " + (newValue ? GREEN : RED) + newValue);
                sender.sendMessage(YELLOW + "To make your change persist after a reload, do " + GREEN + "/" + label + " saveconfig");
                break;
            }
            case "setritualitem": {
                if (!checkPermission(sender, "setritualitem"))
                    return true;
                if (sender instanceof Player) {
                    ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                    if (stack.getType() == Material.AIR) {
                        Config.ritualItem = new ItemStack(Material.AIR);
                        sender.sendMessage(RED + "Set ritual item to air");
                        if (Config.pickupEnabled) {
                            sender.sendMessage(RED + "This allows players to \"pick up\" portable beacons by breaking " +
                                    "existing beacons" + (Config.pickupRequireSilkTouch ? " with silk touch tools" : ""));
                            sender.sendMessage(YELLOW + "To " + BOLD + "TURN OFF" + RESET + YELLOW + " the pickup feature, " +
                                    "do /" + label + " toggle world-pickup false");
                        } else {
                            sender.sendMessage(RED + "This would have allowed players to create portable beacons " +
                                    "by breaking existing beacons" + (Config.pickupRequireSilkTouch ? " with silk touch\n" : "\n")
                                    + DARK_RED + BOLD + "HOWEVER, " + RESET + RED + "the config option is " +
                                    "currently disabled. \n" + RED + "To turn on this feature, do /" + label + "toggle world-placement true");
                        }
                        sender.sendMessage(YELLOW + "To " + BOLD + "TURN OFF" + RESET + YELLOW + " the ritual, " +
                                "do /" + label + " toggle ritual false");
                    } else {
                        Config.ritualItem = stack.clone();
                        sender.sendMessage(GREEN + "Set ritual item to " +
                                stack.getAmount() + "x" + YELLOW + stack.getType().name() + GREEN + " (+ additional item meta).");
                    }
                    // clear old ritual items
                    Events.INSTANCE.ritualItems.clear();
                    PortableBeacons.INSTANCE.saveConfig();
                    sender.sendMessage(GREEN + "Configuration saved to file.");
                } else {
                    sender.sendMessage(RED + "You must be a player to use this command!");
                }
                break;
            }
            case "updateitems": {
                if (!checkPermission(sender, "updateitems"))
                    return true;
                // six characters
                Config.itemCustomVersion = Integer.toString(ThreadLocalRandom.current().nextInt(0xFFFFFF + 1), 16);
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "All portable beacon items will be forced to update soon.");
                sender.sendMessage(GREEN + "Configuration saved to file.");
                break;
            }
            case "inspect": {
                if (!checkPermission(sender, "inspect"))
                    return true;
                Player target = null;
                if (args.length > 1)
                    target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(RED + "You must specify a player!");
                        return true;
                    }
                    target = (Player) sender;
                }
                doInspect(sender, target);
                break;
            }
            case "item": {
                if (!checkPermission(sender, "item"))
                    return true;
                doItem(sender, args, label);
                break;
            }
            default: {
                promptUsage(sender, label, "<reload/setritualitem/updateitems/inspect/item/toggle> [...]");
                break;
            }
        }
        return true;
    }

    public void doInspect(CommandSender sender, Player target) {
        ItemStack stack = target.getInventory().getItemInMainHand();
        if (!ItemUtils.isPortableBeacon(stack)) {
            sender.sendMessage(RED + "Target is not holding a portable beacon!");
            return;
        }
        boolean showHoverMessages = sender instanceof Player;
        BeaconEffects effects = ItemUtils.getEffects(stack);

        // simulate nerfs
        boolean isInDisabledWorld = Config.nerfDisabledWorlds.contains(target.getWorld().getName());
        boolean canUseBeacons = true;
        BeaconEffects effectiveEffects = effects;
        if (PortableBeacons.INSTANCE.worldGuardInstalled && Config.worldGuard) {
            canUseBeacons = WorldGuardHelper.canUseBeacons(target);
            effectiveEffects = WorldGuardHelper.filterBeaconEffects(target, effects);
        }
        Map<PotionEffectType, Integer> effectsMap = effects.getEffects();
        Map<PotionEffectType, Integer> effectiveEffectsMap = effectiveEffects.getEffects();
        sender.sendMessage(GREEN + "Potion effects:");
        for (Map.Entry<PotionEffectType, Integer> entry : effectsMap.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue();
            // format used in commands
            String internalFormat = PotionEffectUtils.getName(type) + (level != 1 ? "=" + level : "");
            BaseComponent[] potionDisplay = new ComponentBuilder()
                    .append("  ") // indentation
                    .append(TextComponent.fromLegacyText(PotionEffectUtils.getDisplayName(type, level)))
                    .append(" ", ComponentBuilder.FormatRetention.NONE)
                    .append("(" + internalFormat + ")").color(YELLOW)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new Text(new ComponentBuilder("Used in commands\nClick to copy!").color(YELLOW).create())
                    ))
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, internalFormat))
                    .create();

            String disabledMsg = null; // hover event, null if not disabled
            if (isInDisabledWorld) {
                disabledMsg = "Target is in a world where Portable Beacons are disabled!\n" +
                        "Check config 'beacon-items.nerfs.disabled-worlds'.";
            } else if (!canUseBeacons) {
                disabledMsg = "Target is in a WorldGuard region where Portable Beacons are disabled!\n" +
                        "Check 'allow-portable-beacons' of the region.";
            } else if (!effectiveEffectsMap.containsKey(type)) {
                disabledMsg = "Target is in a WorldGuard region where " + PotionEffectUtils.getName(entry.getKey()) +
                        " is disabled!\nCheck 'allowed-beacon-effects'/'blocked-beacon-effects' of the region.";
            } else if (effects.getDisabledEffects().contains(type)) { // disabled
                disabledMsg = "Target has disabled the effect!";
            }

            if (disabledMsg != null) {
                // an interesting way to apply strikethrough to the entire component
                TextComponent potionDisplayStrikethrough = new TextComponent();
                potionDisplayStrikethrough.setExtra(Arrays.asList(potionDisplay));

                BaseComponent[] actualDisplay = new ComponentBuilder()
                        .append(potionDisplayStrikethrough).strikethrough(true)
                        .append(" DISABLED ", ComponentBuilder.FormatRetention.NONE)
                        .color(DARK_GRAY).italic(true)
                        .append("[?]", ComponentBuilder.FormatRetention.NONE)
                        .color(DARK_GRAY)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(new ComponentBuilder(disabledMsg).color(RED).create())
                        ))
                        .create();
                sender.spigot().sendMessage(actualDisplay);
                if (!showHoverMessages) { // show consoles too
                    TextComponent disabledDisplayIndent = new TextComponent("    " + disabledMsg);
                    disabledDisplayIndent.setColor(RED);
                    sender.spigot().sendMessage(disabledDisplayIndent);
                }
            } else {
                sender.spigot().sendMessage(potionDisplay);
            }
        }
        sender.sendMessage(GREEN + "Custom data version: " + YELLOW + effects.customDataVersion);
        if (effects.soulboundLevel != 0 || effects.expReductionLevel != 0) {
            sender.sendMessage(GREEN + "Enchantments:");
            if (effects.expReductionLevel != 0)
                sender.sendMessage(String.format("  %sEXP_REDUCTION %d (%.2f%% exp consumption)",
                        YELLOW, effects.expReductionLevel,
                        Math.max(0, 1 - effects.expReductionLevel * Config.enchExpReductionReductionPerLevel) * 100));
            if (effects.soulboundLevel != 0)
                sender.sendMessage(String.format("  %sSOULBOUND %d (bound to %s)",
                        YELLOW, effects.soulboundLevel,
                        (effects.soulboundOwner != null ?
                                Bukkit.getOfflinePlayer(effects.soulboundOwner).getName()+" - "+effects.soulboundOwner :
                                "no-one"))
                );
        }
        if (Config.nerfExpLevelsPerMinute > 0) {
            double perMinute = effects.calcExpPerMinute();
            String expUnit = " levels";
            if (sender instanceof Player) {
                TextComponent consumptionText = new TextComponent();
                consumptionText.setExtra(Arrays.asList(
                        new ComponentBuilder(String.format("%.2f%s/minute", perMinute, expUnit)).color(YELLOW)
                                .append(" [...]").color(GRAY)
                                .create()
                ));
                String consumptionHoverText = YELLOW + "Consumes " +
                        String.format(GREEN + "%.1f%3$s" + YELLOW + " / " + AQUA + "%.1f%3$s", perMinute / 16, perMinute * 60, expUnit) +
                        YELLOW + " per " + GREEN + "3.75s" + YELLOW + " / " + AQUA + "hour\n" +
                        YELLOW + "Consumes 1 exp level every " + GOLD + String.format("%.2fs", 60 / perMinute);
                sender.spigot().sendMessage(new ComponentBuilder("Exp upkeep: ").color(GREEN)
                        .append(consumptionText)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(consumptionHoverText)))
                        .create());
            } else {
                sender.sendMessage(GREEN + "Exp upkeep: " + YELLOW + String.format(
                        "%.4f%4$s/minute, %.2f%4$s/3.75s, %.1f%4$s/hour",
                                perMinute, perMinute / 16, perMinute * 60, expUnit));
            }
        }

        // Pyramid
        if (ItemUtils.isPyramid(stack)) {
            BeaconPyramid pyramid = ItemUtils.getPyramid(stack);
            sender.sendMessage(GREEN + "Pyramid:");
            sender.sendMessage("  " + GREEN + "Tier: " + YELLOW + pyramid.tier);
            Map<BlockData, Long> blockCount = pyramid.beaconBaseBlocks.stream()
                    .collect(Collectors.groupingBy(BeaconPyramid.BeaconBase::data, Collectors.counting()));
            sender.sendMessage("  " + GREEN + "Blocks:");
            blockCount.forEach((blockData, count) ->
                    sender.sendMessage("    " + YELLOW + blockData.getAsString() + ": " + count));
        }
    }

    static void editPlayers(CommandSender sender, List<Player> players, Consumer<BeaconEffects> modifier,
                            boolean silent, boolean modifyAll) {
        ArrayList<Player> succeeded = new ArrayList<>();
        ArrayList<Player> failedPlayers = new ArrayList<>();

        for (Player player : players) {
            if (modifyAll) { // modify inventory
                boolean success = false;
                for (ListIterator<ItemStack> iterator = player.getInventory().iterator(); iterator.hasNext(); ) {
                    ItemStack stack = iterator.next();
                    if (!ItemUtils.isPortableBeacon(stack))
                        continue;
                    success = true;
                    BeaconEffects effects = ItemUtils.getEffects(stack);
                    modifier.accept(effects);
                    iterator.set(ItemUtils.createStackCopyItemData(effects, stack));
                }
                if (success) {
                    succeeded.add(player);
                    if (!silent)
                        player.sendMessage(GREEN + "One or more of your portable beacon was modified!");
                } else {
                    failedPlayers.add(player);
                }
            } else {
                PlayerInventory inventory = player.getInventory();
                ItemStack hand = inventory.getItemInMainHand();
                if (!ItemUtils.isPortableBeacon(hand)) {
                    failedPlayers.add(player);
                    continue;
                }

                BeaconEffects effects = ItemUtils.getEffects(hand);
                modifier.accept(effects);
                inventory.setItemInMainHand(ItemUtils.createStackCopyItemData(effects, hand));
                succeeded.add(player);
                if (!silent)
                    player.sendMessage(GREEN + "Your portable beacon was modified!");
            }
        }

        succeeded.removeAll(failedPlayers);

        if (succeeded.size() != 0)
            sender.sendMessage(GREEN + "Modified " +
                    (modifyAll ? "all instances of portable beacons on " : "held portable beacon of ") +
                    succeeded.stream().map(Player::getName).collect(Collectors.joining(", ")));
        if (failedPlayers.size() != 0)
            sender.sendMessage(RED + "Failed to apply the operation on " +
                    failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                    " because they " +
                    (modifyAll ? "did not own a portable beacon" : "were not holding a portable beacon.")
            );
    }

    public void doItem(CommandSender sender, String[] args, String label) {
        String usage = "item <give/add/set/remove/setowner/filter>[-silently/-modify-all] <players> <...>";
        if (args.length < 3) {
            promptUsage(sender, label, usage);
            return;
        }

        String[] operationWithFlags = args[1].split("-");
        String operation = operationWithFlags[0];
        if (!checkPermission(sender, "item." + operation))
            return;
        boolean silent = false, modifyAll = false;
        for (int i = 1; i < operationWithFlags.length; i++) {
            String flag = operationWithFlags[i];
            if ("silently".equalsIgnoreCase(flag)) {
                silent = true;
            } else if ("all".equalsIgnoreCase(flag) && "modify".equalsIgnoreCase(operationWithFlags[i - 1])) {
                modifyAll = true;
                sender.sendMessage(YELLOW + "Name of the flag '-modify-all' is subject to change.");
            } else if (!"modify".equalsIgnoreCase(flag)) {
                sender.sendMessage(RED + "Unknown flag -" + flag);
            }
        }

        List<Player> players = Bukkit.selectEntities(sender, args[2]).stream()
                .filter(entity -> entity instanceof Player)
                .map(player -> (Player) player)
                .collect(Collectors.toList());

        if (players.size() == 0) {
            sender.sendMessage(RED + "No player selected");
            return;
        }

        switch (operation) {
            case "give": {
                if (args.length < 4) {
                    promptUsage(sender, label, "item "  + args[1] + " <players> <effects...>");
                }
                BeaconEffects beaconEffects = parseEffects(sender, Arrays.copyOfRange(args, 3, args.length), false);
                ItemStack stack = ItemUtils.createStack(beaconEffects);
                Set<Player> failedPlayers = new HashSet<>();

                for (Player p : players) {
                    Map<Integer, ItemStack> unfit = p.getInventory().addItem(stack);
                    if (unfit.size() != 0 && unfit.get(0) != null && unfit.get(0).getAmount() != 0)
                        failedPlayers.add(p);
                    else if (!silent)
                        p.sendMessage(GREEN + "You were given a portable beacon!");
                }

                sender.sendMessage(GREEN + "Given " +
                        players.stream()
                                .filter(player -> !failedPlayers.contains(player))
                                .map(Player::getName)
                                .collect(Collectors.joining(", "))
                        + " a portable beacon with " + String.join(WHITE + ", ", beaconEffects.toLore()));
                if (failedPlayers.size() != 0)
                    sender.sendMessage(RED +
                            failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                            " couldn't be given a portable beacon because their inventory is full.");
                break;
            }
            case "add":
            case "set":
            case "remove": {
                if (args.length < 4) {
                    promptUsage(sender, label, "item "  + args[1] + " <players> <effects...>");
                }
                BinaryOperator<Integer> merger;
                if ("set".equals(operation)) {
                    // remove if level is being set to 0
                    merger = (oldLevel, newLevel) -> newLevel == 0 ? null : newLevel;
                } else if ("add".equals(operation)) {
                    merger = (oldLevel, newLevel) -> {
                        int result = oldLevel + newLevel;
                        return result <= 0 ? null : result;
                    };
                } else {
                    // remove if resultant level <= 0
                    merger = (oldLevel, newLevel) -> {
                        int result = oldLevel - newLevel;
                        return result <= 0 ? null : result;
                    };
                }

                BeaconEffects virtual = parseEffects(sender, Arrays.copyOfRange(args, 3, args.length), true);
                Map<PotionEffectType, Integer> newEffects = virtual.getEffects();
                Consumer<BeaconEffects> modifier = effects -> {
                    HashMap<PotionEffectType, Integer> map = new HashMap<>(effects.getEffects());
                    newEffects.forEach((pot, lvl) -> map.merge(pot, lvl, merger));
                    effects.setEffects(map);

                    if (virtual.expReductionLevel != -1) {
                        Integer newLevel = merger.apply(effects.expReductionLevel, virtual.expReductionLevel);
                        effects.expReductionLevel = newLevel != null ? newLevel : 0;
                    }
                    if (virtual.soulboundLevel != -1) {
                        Integer newLevel = merger.apply(effects.soulboundLevel, virtual.soulboundLevel);
                        effects.soulboundLevel = newLevel != null ? newLevel : 0;
                    }
                };
                editPlayers(sender, players, modifier, silent, modifyAll);
                break;
            }
            case "update": {
                editPlayers(sender, players, effects -> {}, silent, modifyAll);
            }
            case "setowner": {
                if (args.length < 4) {
                    promptUsage(sender, label, "item "  + args[1] + " <players> <ownerUUID/ownerName>");
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(args[3]);
                } catch (IllegalArgumentException ignored) {
                    Player newOwner = Bukkit.getPlayer(args[3]);
                    if (newOwner != null) {
                        uuid = newOwner.getUniqueId();
                    } else {
                        sender.sendMessage(RED + "Couldn't find UUID or online player '" + args[3] + "'!");
                        promptUsage(sender, label, "item setowner <players> <ownerUUID/ownerName>");
                        return;
                    }
                }
                UUID finalUuid = uuid;
                Consumer<BeaconEffects> modifier = effects -> effects.soulboundOwner = finalUuid;
                editPlayers(sender, players, modifier, silent, modifyAll);
                break;
            }
            case "filter": {
                if (args.length < 5) {
                    promptUsage(sender, label, "item filter <players> <allow/block> <filters...>");
                }
                boolean whitelist = args[3].equalsIgnoreCase("allow");
                String[] filtersString = Arrays.copyOfRange(args, 4, args.length);
                Set<BeaconEffectsFilter> filters = new HashSet<>();
                for (String filterString : filtersString) {
                    try {
                        filters.add(BeaconEffectsFilter.fromString(filterString));
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(RED + "Invalid filter " + filterString + ": " + e.getMessage());
                        return;
                    }
                }
                editPlayers(sender, players, effects -> effects.filter(filters, whitelist), silent, modifyAll);
                break;
            }
            default: {
                promptUsage(sender, label, usage);
                break;
            }
        }
    }
}
