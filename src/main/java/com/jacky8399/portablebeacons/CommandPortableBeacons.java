package com.jacky8399.portablebeacons;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.utils.BeaconEffectsFilter;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import net.md_5.bungee.api.ChatColor;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandPortableBeacons implements TabExecutor {
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        Stream<String> tabCompletion = tabComplete(sender, args);
        if (tabCompletion == null)
            return Collections.emptyList();
        String input = args[args.length - 1];
        return tabCompletion.filter(completion -> completion.startsWith(input)).collect(Collectors.toList());
    }

    private Stream<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return Stream.of("setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect");
        } else if (args[0].equalsIgnoreCase("item")) {
            switch (args.length) {
                case 2: {
                    String[] operations = {"give", "add", "remove", "set", "filter", "setenchantment"};
                    String[] flags = {"", "-silently", "-modify-all", "-silently-modify-all", "-modify-all-silently"};
                    List<String> completions = new ArrayList<>();
                    for (String operation : operations) {
                        // flags
                        if (operation.split("-")[0].equalsIgnoreCase(args[1])) {
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
                }
                case 3: {
                    return Stream.concat(
                            Stream.of("@a", "@s", "@p"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    );
                }
                default: {
                    // Enchantment autocomplete
                    if (args[1].startsWith("setenchantment")) {
                        switch (args.length) {
                            case 4: {
                                return Stream.of("exp-reduction", "soulbound");
                            }
                            case 5: {
                                // levels
                                int maxLevel = args[3].equalsIgnoreCase("exp-reduction") ? Config.customEnchantExpReductionMaxLevel : Config.customEnchantSoulboundMaxLevel;
                                return IntStream.rangeClosed(1, maxLevel)
                                        .mapToObj(Integer::toString);
                            }
                            case 6: {
                                if (args[3].equalsIgnoreCase("soulbound")) {
                                    // show player names for soulbound owner
                                    return Bukkit.getOnlinePlayers().stream()
                                            .map(Player::getName);
                                }
                            }
                            default: {
                                return null;
                            }
                        }
                    } else if (args[1].startsWith("filter")) { // filter autocomplete
                        if (args.length == 4) {
                            return Stream.of("allow", "block");
                        } else {
                            String input = args[args.length - 1];
                            List<String> operators = new ArrayList<>();
                            for (BeaconEffectsFilter.Operator operator : BeaconEffectsFilter.Operator.values()) {
                                if (operator == BeaconEffectsFilter.Operator.ANY)
                                    continue;
                                int idx = input.indexOf(operator.operator);
                                if (idx == -1) {
                                    operators.add(operator.operator);
                                } else if (idx + operator.operator.length() == input.length()) {
                                    // inputted potion name and operator, show constraints
                                    return IntStream.range(0, 10).mapToObj(Integer::toString);
                                }
                                // already inputting constraint
                            }
                            Optional<PotionEffectType> type = PotionEffectUtils.parsePotion(input, false);
                            if (type.isPresent()) {
                                // already inputting operators
                                return Stream.concat(operators.stream(), Stream.of(input));
                            } else {
                                return PotionEffectUtils.getValidPotionNames().stream();
                            }
                        }
                    }

                    // Potion effect type autocomplete
                    String input = args[args.length - 1];
                    // try removing asterisk and everything after
                    int asteriskIdx = input.indexOf('*');
                    if (asteriskIdx != -1)
                        input = input.substring(0, asteriskIdx);
                    int maxAmplifier = -1;
                    Optional<PotionEffectType> optionalPotion = PotionEffectUtils.parsePotion(input, false);
                    if (optionalPotion.isPresent()) {
                        // valid input, show amplifiers
                        Config.PotionEffectInfo info = Config.getInfo(optionalPotion.get());
                        maxAmplifier = info.getMaxAmplifier();
                    } else if (input.equalsIgnoreCase("exp-reduction")) {
                        maxAmplifier = Config.customEnchantExpReductionMaxLevel;
                    } else if (input.equalsIgnoreCase("soulbound")) {
                        maxAmplifier = Config.customEnchantSoulboundMaxLevel;
                    }
                    if (maxAmplifier != -1) {
                        String finalInput = input;
                        return IntStream.rangeClosed("give".equalsIgnoreCase(args[1]) ? 1 : 0, maxAmplifier)
                                .mapToObj(i -> finalInput + "*" + i);
                    }
                    // show potion effects and enchantments
                    return Stream.concat(
                            Arrays.stream(PotionEffectType.values()).map(PotionEffectUtils::getName),
                            Stream.of("exp-reduction", "soulbound")
                    );
                }
            }
        }
        return null;
    }


    @NotNull
    public static BeaconEffects parseEffects(CommandSender sender, String[] input, boolean allowZeroAmplifier) {
        BeaconEffects beaconEffects = new BeaconEffects();
        beaconEffects.expReductionLevel = -1;
        beaconEffects.soulboundLevel = -1;
        HashMap<PotionEffectType, Short> effects = new HashMap<>();
        for (String s : input) {
            try {
                String potionName = s;
                PotionEffectType type;
                int amplifier = 1;
                if (s.contains("*")) {
                    String[] split = s.split("\\*");
                    Preconditions.checkState(split.length == 2, "Invalid format, correct format is TYPE*AMPLIFIER");
                    potionName = split[0];
                    amplifier = Integer.parseInt(split[1]);
                }

                if (potionName.equalsIgnoreCase("exp-reduction")) {
                    beaconEffects.expReductionLevel = amplifier;
                    continue;
                } else if (potionName.equalsIgnoreCase("soulbound")) {
                    beaconEffects.soulboundLevel = amplifier;
                    continue;
                }
                type = PotionEffectUtils.parsePotion(potionName, false)
                        .orElseThrow(()->new IllegalArgumentException(s + " is not a valid potion effect or enchantment"));
                Preconditions.checkArgument(amplifier >= (allowZeroAmplifier ? 0 : 1), "Amplifier is negative");
                effects.put(type, (short) amplifier);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + String.format("Skipped '%s' as it is not a valid potion effect (%s)", s, e.getMessage()));
            }
        }
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        boolean result = sender.hasPermission(permission);
        if (!result)
            sender.sendMessage(ChatColor.RED + "You don't have permission to do this!");
        return result;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (!checkPermission(sender, "portablebeacons.command.info"))
                return true;
            sender.sendMessage(ChatColor.GREEN + "You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "WorldGuard integration: " + Config.worldGuard + ", WorldGuard detected: " + PortableBeacons.INSTANCE.worldGuardInstalled);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                if (!checkPermission(sender, "portablebeacons.command.reload"))
                    return true;
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                break;
            }
            case "saveconfig": {
                if (!checkPermission(sender, "portablebeacons.command.saveconfig"))
                    return true;
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                break;
            }
            case "setritualitem": {
                if (!checkPermission(sender, "portablebeacons.command.setritualitem"))
                    return true;
                if (sender instanceof Player) {
                    ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                    if (stack.getType() == Material.AIR) {
                        Config.ritualItem = new ItemStack(Material.AIR);
                        PortableBeacons.INSTANCE.saveConfig();
                        sender.sendMessage(ChatColor.RED + "Disabled portable beacon creation");
                    } else {
                        Config.ritualItem = stack.clone();
                        PortableBeacons.INSTANCE.saveConfig();
                        sender.sendMessage(ChatColor.GREEN + "Successfully set ritual item to " +
                                stack.getAmount() + " of " + stack.getType().name() + "(+ additional item meta).");
                    }
                    sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                } else {
                    sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
                }
                break;
            }
            case "updateitems": {
                if (!checkPermission(sender, "portablebeacons.command.updateitems"))
                    return true;
                Config.itemCustomVersion = UUID.randomUUID().toString().replace("-", "");
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "All portable beacon items will be forced to update soon.");
                sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                break;
            }
            case "inspect": {
                if (!checkPermission(sender, "portablebeacons.command.inspect"))
                    return true;
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
                    return true;
                }
                doInspect((Player) sender);
                break;
            }
            case "item": {
                if (!checkPermission(sender, "portablebeacons.command.item"))
                    return true;
                doItem(sender, args, label);
                break;
            }
            default: {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <reload/setritualitem/update/item> [...]");
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

    public void doInspect(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!ItemUtils.isPortableBeacon(stack)) {
            player.sendMessage(ChatColor.RED + "You are not holding a portable beacon!");
            return;
        }
        BeaconEffects effects = ItemUtils.getEffects(stack);

        // simulate nerfs
        boolean isInDisabledWorld = Config.itemNerfsDisabledWorlds.contains(player.getWorld().getName());
        boolean canUseBeacons = true;
        BeaconEffects effectiveEffects = effects;
        if (PortableBeacons.INSTANCE.worldGuardInstalled && Config.worldGuard) {
            canUseBeacons = WorldGuardHelper.canUseBeacons(player);
            effectiveEffects = WorldGuardHelper.filterBeaconEffects(player, effects);
        }
        Map<PotionEffectType, Short> effectsMap = effects.getEffects();
        Map<PotionEffectType, Short> effectiveEffectsMap = effectiveEffects.getEffects();
        player.sendMessage(ChatColor.GREEN + "Potion effects:");
        for (Map.Entry<PotionEffectType, Short> entry : effectsMap.entrySet()) {
            // format used in commands
            String internalFormat = PotionEffectUtils.getName(entry.getKey()) + (entry.getValue() != 1 ? "*" + entry.getValue() : "");
            BaseComponent[] potionDisplay = new ComponentBuilder()
                    .append("  ") // indentation
                    .append(TextComponent.fromLegacyText(PotionEffectUtils.getDisplayName(entry.getKey(), entry.getValue())))
                    .append(" ", ComponentBuilder.FormatRetention.NONE)
                    .append("(" + internalFormat + ")").color(ChatColor.YELLOW)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder("Used in commands\nClick to copy!")
                            .color(ChatColor.YELLOW)
                            .create()
                    )))
                    .event(new ClickEvent(getCopyToClipboard(), internalFormat))
                    .create();

            BaseComponent[] disabledDisplay = null; // hover event, null if not disabled
            if (isInDisabledWorld) {
                disabledDisplay = new ComponentBuilder("You are in a world where Portable Beacons are disabled!\n" +
                        "Check config 'beacon-items.nerfs.disabled-worlds'.")
                        .color(ChatColor.RED)
                        .create();
            } else if (!canUseBeacons) {
                disabledDisplay = new ComponentBuilder("You are in a WorldGuard region where Portable Beacons are disabled!\n" +
                        "Check 'allow-portable-beacons' of the region.")
                        .color(ChatColor.RED)
                        .create();
            } else if (!effectiveEffectsMap.containsKey(entry.getKey())) {
                disabledDisplay = new ComponentBuilder("You are in a WorldGuard region where " + PotionEffectUtils.getName(entry.getKey()) + " is disabled!\n" +
                        "Check 'allowed-beacon-effects'/'blocked-beacon-effects' of the region.")
                        .color(ChatColor.RED)
                        .create();
            }

            if (disabledDisplay != null) {
                // an interesting way to apply strikethrough to the entire component
                TextComponent potionDisplayStrikethrough = new TextComponent();
                potionDisplayStrikethrough.setExtra(Arrays.asList(potionDisplay));

                BaseComponent[] actualDisplay = new ComponentBuilder()
                        .append(potionDisplayStrikethrough).strikethrough(true)
                        .append(" DISABLED ", ComponentBuilder.FormatRetention.NONE)
                        .color(ChatColor.DARK_GRAY).italic(true)
                        .append("[?]", ComponentBuilder.FormatRetention.NONE)
                        .color(ChatColor.DARK_GRAY).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(disabledDisplay)))
                        .create();
                player.spigot().sendMessage(actualDisplay);
            } else {
                player.spigot().sendMessage(potionDisplay);
            }
        }
        player.sendMessage(ChatColor.GREEN + "Custom data version: " + ChatColor.YELLOW + effects.customDataVersion);
        if (effects.soulboundLevel != 0 || effects.expReductionLevel != 0) {
            player.sendMessage(ChatColor.GREEN + "Enchantments:");
            if (effects.expReductionLevel != 0)
                player.sendMessage("  " + ChatColor.YELLOW + "EXP_REDUCTION " + effects.expReductionLevel +
                        String.format(" (%.2f%% exp consumption)", Math.max(0, 1 - effects.expReductionLevel * Config.customEnchantExpReductionReductionPerLevel) * 100));
            if (effects.soulboundLevel != 0)
                player.sendMessage("  " + ChatColor.YELLOW + "SOULBOUND " + effects.soulboundLevel + " (bound to " +
                        (effects.soulboundOwner != null ? Bukkit.getOfflinePlayer(effects.soulboundOwner).getName() : "no-one") + ")");
        }
        if (Config.itemNerfsExpPercentagePerCycle > 0) {
            double xpPerCycle = effects.calcExpPerCycle();
            player.sendMessage(ChatColor.GREEN + "Exp %: " + ChatColor.YELLOW +
                    String.format("%.2f%%/7.5s, %.1f%%/min, %.1f%%/hour", xpPerCycle * 100, xpPerCycle * 8 * 100, xpPerCycle * 480 * 100));
        }
    }

    static void editPlayers(CommandSender sender, List<Player> players, Consumer<BeaconEffects> modifier, boolean silent, boolean modifyAll) {
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

                if (success && !silent)
                    player.sendMessage(ChatColor.GREEN + "One or more of your portable beacon was modified!");
                else if (!success)
                    failedPlayers.add(player);
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
                if (!silent)
                    player.sendMessage(ChatColor.GREEN + "Your portable beacon was modified!");
            });

        ArrayList<Player> succeeded = new ArrayList<>(players);
        succeeded.removeAll(failedPlayers);

        if (succeeded.size() != 0)
            sender.sendMessage(ChatColor.GREEN + "Modified " + (modifyAll ? "all instances of portable beacons on " : "held portable beacon of ") +
                    succeeded.stream().map(Player::getName).collect(Collectors.joining(", ")));
        if (failedPlayers.size() != 0)
            sender.sendMessage(ChatColor.RED + "Failed to apply the operation on " +
                    failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                    " because they " + (modifyAll ? "did not own a portable beacon" : "were not holding a portable beacon.")
            );
    }

    public void doItem(CommandSender sender, String[] args, String label) {
        String usage = ChatColor.RED + "Usage: /" + label + " item <give/add/set/remove/setenchantment>[-silently/-modify-all] <players> <effects...>";
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
                sender.sendMessage(ChatColor.YELLOW + "Name of the flag '-modify-all' is subject to change.");
            } else if (!"modify".equalsIgnoreCase(flag)) {
                sender.sendMessage(ChatColor.RED + "Unknown flag -" + flag);
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
                        player.sendMessage(ChatColor.GREEN + "You were given a portable beacon!");
                });

                sender.sendMessage(ChatColor.GREEN + "Given " +
                        players.stream()
                                .filter(player -> !failedPlayers.contains(player))
                                .map(Player::getName)
                                .collect(Collectors.joining(", "))
                        + " a portable beacon with " + String.join(ChatColor.WHITE + ", ", beaconEffects.toLore()));
                if (failedPlayers.size() != 0)
                    sender.sendMessage(ChatColor.RED + failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
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
                BiFunction<Short, Short, Short> shortMerger = (s1, s2) -> {
                    Integer ret = merger.apply(s1.intValue(), s2.intValue());
                    return ret != null ? ret.shortValue() : null;
                };

                BeaconEffects virtualEffects = parseEffects(sender, effectsString, true);
                Map<PotionEffectType, Short> newEffects = virtualEffects.getEffects();
                Consumer<BeaconEffects> modifier = effects -> {
                    HashMap<PotionEffectType, Short> map = new HashMap<>(effects.getEffects());
                    newEffects.forEach((pot, lvl) -> map.merge(pot, lvl, shortMerger));
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
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item setenchantment <players> <enchantment> <level> [soulboundOwner]");
                    return;
                }

                String enchantment = args[3];
                int newLevel = Integer.parseInt(args[4]);
                Consumer<BeaconEffects> modifier;

                if (enchantment.equalsIgnoreCase("soulbound")) {
                    if (args.length >= 6) { // has soulboundOwner arg
                        UUID uuid = null;
                        try {
                            uuid = UUID.fromString(args[5]);
                        } catch (IllegalArgumentException ignored) {
                            Player newOwner = Bukkit.getPlayer(args[5]);
                            if (newOwner != null) {
                                uuid = newOwner.getUniqueId();
                            }
                        }
                        if (uuid != null) {
                            UUID finalUuid = uuid;
                            modifier = effects -> {
                                effects.soulboundLevel = newLevel;
                                effects.soulboundOwner = finalUuid;
                            };
                        } else {
                            sender.sendMessage(ChatColor.RED + "Couldn't find UUID or online player '" + args[5] + "'!");
                            return;
                        }
                    } else {
                        modifier = effects -> effects.soulboundLevel = newLevel;
                    }
                } else if (enchantment.equalsIgnoreCase("exp-reduction")) {
                    modifier = effects -> effects.expReductionLevel = newLevel;
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid enchantment " + enchantment + "!");
                    return;
                }
                editPlayers(sender, players, modifier, silent, modifyAll);
                break;
            }
            default: {
                sender.sendMessage(usage);
                break;
            }
        }
    }
}
