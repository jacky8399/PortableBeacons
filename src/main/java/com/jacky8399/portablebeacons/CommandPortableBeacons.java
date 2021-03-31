package com.jacky8399.portablebeacons;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.utils.BeaconEffectsFilter;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.md_5.bungee.api.ChatColor.*;

public class CommandPortableBeacons implements TabExecutor {
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
            return Stream.of("setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect", "toggle");
        } else if (args[0].equalsIgnoreCase("item")) {
            String operationArg = args[1].split("-")[0];
            if (args.length == 2) {
                String[] operations = {"give", "add", "remove", "set", "filter", "setenchantment"};
                String[] flags = {"", "-silently", "-modify-all", "-silently-modify-all", "-modify-all-silently"};
                List<String> completions = new ArrayList<>();
                for (String operation : operations) {
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
            if (operationArg.startsWith("setenchantment")) {
                switch (args.length) {
                    case 4: {
                        return Stream.of("soulbound");
                    }
                    case 5: { // levels
                        int maxLevel = Config.enchSoulboundMaxLevel;
                        return IntStream.rangeClosed(1, maxLevel)
                                .mapToObj(Integer::toString);
                    }
                    case 6: { // show player names for soulbound owner
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName);
                    }
                }
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
        } else if (args[0].equalsIgnoreCase("inspect") && args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args[0].equalsIgnoreCase("toggle")) {
            if (args.length == 2) {
                return Stream.of("ritual", "anvil-combination");
            } else if (args.length == 3) {
                return Stream.of("true", "false");
            }
        }
        return null;
    }

    @NotNull
    public static BeaconEffects parseEffects(CommandSender sender, String[] input, boolean allowVirtual) {
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
                    if (!"=".equals(splitChar)) {
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
        boolean result = sender.hasPermission(permission);
        if (!result)
            sender.sendMessage(RED + "You don't have permission to do this!");
        return result;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (!checkPermission(sender, "portablebeacons.command.info"))
                return true;
            sender.sendMessage(GREEN + "You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
            sender.sendMessage(GRAY + "WorldGuard integration: " + Config.worldGuard + ", WorldGuard detected: " + PortableBeacons.INSTANCE.worldGuardInstalled);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                if (!checkPermission(sender, "portablebeacons.command.reload"))
                    return true;
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(GREEN + "Configuration reloaded.");
                break;
            }
            case "saveconfig": {
                if (!checkPermission(sender, "portablebeacons.command.saveconfig"))
                    return true;
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "Configuration saved to file.");
                break;
            }
            case "toggle": {
                if (!checkPermission(sender, "portablebeacons.command.toggle"))
                    return true;
                if (args.length < 2) {
                    sender.sendMessage(RED + "Usage: /" + label + " toggle <ritual/anvil-combination> [true/false]");
                    return false;
                }
                Boolean value = args.length == 3 ? args[2].equalsIgnoreCase("true") : null;
                boolean newValue;
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "ritual": {
                        newValue = Config.ritualEnabled = value != null ? value : !Config.ritualEnabled;
                        if (!Config.ritualEnabled) {
                            Events.INSTANCE.ritualItems.clear();
                        }
                        break;
                    }
                    case "anvil-combination": {
                        newValue = Config.anvilCombinationEnabled = value != null ? value : !Config.anvilCombinationEnabled;
                        break;
                    }
                    default: {
                        sender.sendMessage(RED + "Invalid toggle option!");
                        return true;
                    }
                }
                sender.sendMessage(GREEN + "Temporarily set " + args[1] + " to " + newValue);
                sender.sendMessage(GREEN + "To make your change persist after a reload, do /" + label + " saveconfig");
                break;
            }
            case "setritualitem": {
                if (!checkPermission(sender, "portablebeacons.command.setritualitem"))
                    return true;
                if (sender instanceof Player) {
                    ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                    if (stack.getType() == Material.AIR) {
                        Config.ritualItem = new ItemStack(Material.AIR);
                        sender.sendMessage(RED + "Set ritual item to air");
                        sender.sendMessage(RED + "This allows players to create portable beacons by breaking existing beacons");
                        sender.sendMessage(YELLOW + "To " + BOLD + "TURN OFF" + RESET + YELLOW + " the ritual, do /" + label + " toggle ritual false");
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
                if (!checkPermission(sender, "portablebeacons.command.updateitems"))
                    return true;
                Config.itemCustomVersion = UUID.randomUUID().toString().replace("-", "");
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "All portable beacon items will be forced to update soon.");
                sender.sendMessage(GREEN + "Configuration saved to file.");
                break;
            }
            case "inspect": {
                if (!checkPermission(sender, "portablebeacons.command.inspect"))
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
                if (!checkPermission(sender, "portablebeacons.command.item"))
                    return true;
                doItem(sender, args, label);
                break;
            }
            default: {
                sender.sendMessage(RED + "Usage: /" + label + " <reload/setritualitem/updateitems/inspect/item/toggle> [...]");
                break;
            }
        }
        return true;
    }

    // find COPY_TO_CLIPBOARD, or use SUGGEST_COMMAND on older versions
    private static ClickEvent.Action getCopyToClipboard() {
        try {
            return ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD");
        } catch (IllegalArgumentException e) {
            return ClickEvent.Action.SUGGEST_COMMAND;
        }
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
            String internalFormat = PotionEffectUtils.getName(type) + (level != 1 ? "*" + level : "");
            BaseComponent[] potionDisplay = new ComponentBuilder()
                    .append("  ") // indentation
                    .append(TextComponent.fromLegacyText(PotionEffectUtils.getDisplayName(type, level)))
                    .append(" ", ComponentBuilder.FormatRetention.NONE)
                    .append("(" + internalFormat + ")").color(YELLOW)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder("Used in commands\nClick to copy!")
                            .color(YELLOW)
                            .create()
                    )))
                    .event(new ClickEvent(getCopyToClipboard(), internalFormat))
                    .create();

            BaseComponent[] disabledDisplay = null; // hover event, null if not disabled
            if (isInDisabledWorld) {
                disabledDisplay = new ComponentBuilder("Target is in a world where Portable Beacons are disabled!\n" +
                        "Check config 'beacon-items.nerfs.disabled-worlds'.")
                        .color(RED)
                        .create();
            } else if (!canUseBeacons) {
                disabledDisplay = new ComponentBuilder("Target is in a WorldGuard region where Portable Beacons are disabled!\n" +
                        "Check 'allow-portable-beacons' of the region.")
                        .color(RED)
                        .create();
            } else if (!effectiveEffectsMap.containsKey(type)) {
                disabledDisplay = new ComponentBuilder("Target is in a WorldGuard region where " + PotionEffectUtils.getName(entry.getKey()) + " is disabled!\n" +
                        "Check 'allowed-beacon-effects'/'blocked-beacon-effects' of the region.")
                        .color(RED)
                        .create();
            } else if (effects.getDisabledEffects().contains(type)) { // disabled
                disabledDisplay = new ComponentBuilder("Target has disabled the effect!")
                        .color(RED)
                        .create();
            }

            if (disabledDisplay != null) {
                // an interesting way to apply strikethrough to the entire component
                TextComponent potionDisplayStrikethrough = new TextComponent();
                potionDisplayStrikethrough.setExtra(Arrays.asList(potionDisplay));

                BaseComponent[] actualDisplay = new ComponentBuilder()
                        .append(potionDisplayStrikethrough).strikethrough(true)
                        .append(" DISABLED ", ComponentBuilder.FormatRetention.NONE)
                        .color(DARK_GRAY).italic(true)
                        .append("[?]", ComponentBuilder.FormatRetention.NONE)
                        .color(DARK_GRAY).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(disabledDisplay)))
                        .create();
                sender.spigot().sendMessage(actualDisplay);
                if (!showHoverMessages) { // show consoles too
                    TextComponent disabledDisplayIndent = new TextComponent("    ");
                    disabledDisplayIndent.setExtra(Arrays.asList(disabledDisplay));
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
                sender.sendMessage("  " + YELLOW + "EXP_REDUCTION " + effects.expReductionLevel +
                        String.format(" (%.2f%% exp consumption)", Math.max(0, 1 - effects.expReductionLevel * Config.enchExpReductionReductionPerLevel) * 100));
            if (effects.soulboundLevel != 0)
                sender.sendMessage("  " + YELLOW + "SOULBOUND " + effects.soulboundLevel + " (bound to " +
                        (effects.soulboundOwner != null ? Bukkit.getOfflinePlayer(effects.soulboundOwner).getName() : "no-one") + ")");
        }
        if (Config.nerfExpPercentagePerCycle > 0) {
            double xpPerCycle = effects.calcExpPerCycle();
            sender.sendMessage(GREEN + "Exp %: " + YELLOW +
                    String.format("%.2f%%/7.5s, %.1f%%/min, %.1f%%/hour", xpPerCycle * 100, xpPerCycle * 8 * 100, xpPerCycle * 480 * 100));
        }
    }

    static void editPlayers(CommandSender sender, List<Player> players, Consumer<BeaconEffects> modifier, boolean silent, boolean modifyAll) {
        ArrayList<Player> succeeded = new ArrayList<>(players);
        ArrayList<Player> failedPlayers = new ArrayList<>();

        if (modifyAll) // modify inventory or hand
            players.forEach(player -> {
                boolean success = false;
                for (ListIterator<ItemStack> iterator = player.getInventory().iterator(); iterator.hasNext();) {
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
            });
        else
            players.forEach(player -> {
                PlayerInventory inventory = player.getInventory();
                ItemStack hand = inventory.getItemInMainHand();
                if (!ItemUtils.isPortableBeacon(hand)) {
                    failedPlayers.add(player);
                    return;
                }

                BeaconEffects effects = ItemUtils.getEffects(hand);
                modifier.accept(effects);
                inventory.setItemInMainHand(ItemUtils.createStackCopyItemData(effects, hand));
                succeeded.add(player);
                if (!silent)
                    player.sendMessage(GREEN + "Your portable beacon was modified!");
            });

        succeeded.removeAll(failedPlayers);

        if (succeeded.size() != 0)
            sender.sendMessage(GREEN + "Modified " + (modifyAll ? "all instances of portable beacons on " : "held portable beacon of ") +
                    succeeded.stream().map(Player::getName).collect(Collectors.joining(", ")));
        if (failedPlayers.size() != 0)
            sender.sendMessage(RED + "Failed to apply the operation on " +
                    failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                    " because they " + (modifyAll ? "did not own a portable beacon" : "were not holding a portable beacon.")
            );
    }

    public void doItem(CommandSender sender, String[] args, String label) {
        String usage = RED + "Usage: /" + label + " item <give/add/set/remove/setenchantment/filter>[-silently/-modify-all] <players> <...>";
        if (args.length < 4) {
            sender.sendMessage(usage);
            return;
        }

        String[] operationWithFlags = args[1].split("-");
        String operation = operationWithFlags[0];
        if (!checkPermission(sender, "portablebeacons.command.item." + operation))
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
        String[] effectsString = Arrays.copyOfRange(args, 3, args.length);

        switch (operation) {
            case "give": {
                BeaconEffects beaconEffects = parseEffects(sender, effectsString, false);
                ItemStack stack = ItemUtils.createStack(beaconEffects);
                Set<Player> failedPlayers = new HashSet<>();

                final boolean finalSilent = silent;
                players.forEach(player -> {
                    Map<Integer, ItemStack> unfit = player.getInventory().addItem(stack);
                    if (unfit.size() != 0 && unfit.get(0) != null && unfit.get(0).getAmount() != 0)
                        failedPlayers.add(player);
                    else if (finalSilent)
                        player.sendMessage(GREEN + "You were given a portable beacon!");
                });

                sender.sendMessage(GREEN + "Given " +
                        players.stream()
                                .filter(player -> !failedPlayers.contains(player))
                                .map(Player::getName)
                                .collect(Collectors.joining(", "))
                        + " a portable beacon with " + String.join(WHITE + ", ", beaconEffects.toLore()));
                if (failedPlayers.size() != 0)
                    sender.sendMessage(RED + failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                            " couldn't be given a portable beacon because their inventory is full.");
                break;
            }
            case "add":
            case "set":
            case "remove": {
                BiFunction<Integer, Integer, Integer> merger;
                switch (operation) {
                    case "set": {
                        // remove if level is being set to 0
                        merger = (oldLevel, newLevel) -> newLevel == 0 ? null : newLevel;
                        break;
                    }
                    case "add": {
                        merger = Integer::sum;
                        break;
                    }
                    case "remove": {
                        // remove if resultant level <= 0
                        merger = (oldLevel, newLevel) -> {
                            int result = oldLevel - newLevel;
                            return result <= 0 ? null : result;
                        };
                        break;
                    }
                    default: throw new UnsupportedOperationException(operation); // to make java shut up
                }

                BeaconEffects virtualEffects = parseEffects(sender, effectsString, true);
                Map<PotionEffectType, Integer> newEffects = virtualEffects.getEffects();
                Consumer<BeaconEffects> modifier = effects -> {
                    HashMap<PotionEffectType, Integer> map = new HashMap<>(effects.getEffects());
                    newEffects.forEach((pot, lvl) -> map.merge(pot, lvl, merger));
                    effects.setEffects(map);

                    if (virtualEffects.expReductionLevel != -1) {
                        Integer newExpReductionLevel = merger.apply(effects.expReductionLevel, virtualEffects.expReductionLevel);
                        effects.expReductionLevel = newExpReductionLevel != null ? newExpReductionLevel : 0;
                    }
                    if (virtualEffects.soulboundLevel != -1) {
                        Integer newSoulboundLevel = merger.apply(effects.soulboundLevel, virtualEffects.soulboundLevel);
                        effects.soulboundLevel = newSoulboundLevel != null ? newSoulboundLevel : 0;
                    }
                };
                editPlayers(sender, players, modifier, silent, modifyAll);
                break;
            }
            case "setenchantment": {
                if (args.length < 5) {
                    sender.sendMessage(RED + "Usage: /" + label + " item setenchantment <players> soulbound <level> <soulboundOwner>");
                    return;
                }
                int newLevel = Integer.parseInt(args[4]);
                Consumer<BeaconEffects> modifier;
                if (args[3].equalsIgnoreCase("soulbound") && args.length >= 6) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(args[5]);
                    } catch (IllegalArgumentException ignored) {
                        Player newOwner = Bukkit.getPlayer(args[5]);
                        if (newOwner != null) {
                            uuid = newOwner.getUniqueId();
                        } else {
                            sender.sendMessage(RED + "Couldn't find UUID or online player '" + args[5] + "'!");
                            return;
                        }
                    }
                    UUID finalUuid = uuid;
                    modifier = effects -> {
                        effects.soulboundLevel = newLevel;
                        effects.soulboundOwner = finalUuid;
                    };
                } else {
                    sender.sendMessage(RED + "Please use /" + label + " item set " + args[2] + " set " + args[3] + "*" + newLevel);
                    sender.sendMessage(RED + "setenchantment is only retained for changing soulbound owner");
                    return;
                }
                editPlayers(sender, players, modifier, silent, modifyAll);
                break;
            }
            case "filter": {
                if (args.length < 5) {
                    sender.sendMessage(RED + "Usage: /" + label + " item filter <players> <allow/block> <filters...>");
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
                sender.sendMessage(usage);
                break;
            }
        }
    }
}
