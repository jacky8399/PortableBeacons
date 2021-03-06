package com.jacky8399.main;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandPortableBeacons implements TabExecutor {
    /**
     * A bi-map of Bukkit effect names to vanilla names
     */
    private static final ImmutableBiMap<String, String> VANILLA_EFFECT_NAMES = ImmutableBiMap.<String, String>builder()
            .put("slow", "slowness")
            .put("fast_digging", "haste")
            .put("slow_digging", "mining_fatigue")
            .put("increase_damage", "strength")
            .put("heal", "instant_health")
            .put("harm", "instant_damage")
            .put("jump", "jump_boost")
            .put("confusion", "nausea")
            .put("damage_resistance", "resistance")
            .build();
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        if (command.getLabel().equalsIgnoreCase("portablebeacons")) {
            if (args.length <= 1) {
                return Stream.of("setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect").filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("item")) {
                switch (args.length) {
                    case 2: {
                        String[] operations = {"give", "add", "remove", "set", "setenchantment"};
                        List<String> completions = new ArrayList<>();
                        for (String operation : operations) {
                            // flags
                            if (operation.equalsIgnoreCase(args[1])) {
                                return Arrays.asList(operation, operation + "-silently"); // show original operation too
                            } else if (operation.startsWith(args[1])) {
                                completions.add(operation);
                            }
                        }
                        return completions;
                    }
                    case 3: {
                        return Stream.concat(
                                Stream.of("@a", "@s", "@p"),
                                Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        ).filter(name -> name.startsWith(args[2])).collect(Collectors.toList());
                    }
                    default: {
                        // Enchantment autocomplete
                        if (args[1].equalsIgnoreCase("setenchantment")) {
                            switch (args.length) {
                                case 4: {
                                    return Stream.of("exp-reduction", "soulbound")
                                            .filter(name -> name.startsWith(args[3]))
                                            .collect(Collectors.toList());
                                }
                                case 5: {
                                    // levels
                                    int maxLevel = args[3].equalsIgnoreCase("exp-reduction") ? Config.customEnchantExpReductionMaxLevel : Config.customEnchantSoulboundMaxLevel;
                                    return IntStream.rangeClosed(1, maxLevel)
                                            .mapToObj(Integer::toString)
                                            .filter(level -> level.startsWith(args[4]))
                                            .collect(Collectors.toList());
                                }
                                case 6: {
                                    if (args[3].equalsIgnoreCase("soulbound")) {
                                        // show player names for soulbound owner
                                        return Bukkit.getOnlinePlayers().stream()
                                                .map(Player::getName)
                                                .filter(name -> name.startsWith(args[5]))
                                                .collect(Collectors.toList());
                                    }
                                }
                                default: {
                                    return Collections.emptyList();
                                }
                            }
                        }

                        // Potion effect type autocomplete
                        String input = args[args.length - 1];
                        if (parseTypeLenient(input) != null) {
                            // valid input, show amplifiers
                            return IntStream.range("give".equalsIgnoreCase(args[1]) ? 1 : 0, 10)
                                    .mapToObj(i -> input + "*" + i)
                                    .filter(level -> level.startsWith(input))
                                    .collect(Collectors.toList());
                        }
                        // show potion effects
                        return Arrays.stream(PotionEffectType.values())
                                .map(PotionEffectType::getName).map(String::toLowerCase) // get potion name
                                .map(name -> VANILLA_EFFECT_NAMES.getOrDefault(name, name)) // try convert to vanilla names, fallback to Bukkit name
                                .filter(name -> name.startsWith(input))
                                .collect(Collectors.toList());
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    public static PotionEffectType parseTypeLenient(String input) {
        input = input.toLowerCase(Locale.US);
        String bukkitName = VANILLA_EFFECT_NAMES.inverse().get(input);
        return PotionEffectType.getByName(bukkitName != null ? bukkitName : input);
    }

    @NotNull
    public static PotionEffectType parseType(String input) {
        PotionEffectType type = parseTypeLenient(input);
        if (type == null)
            throw new IllegalArgumentException("Can't find potion effect " + input);
        return type;
    }

    @NotNull
    public static BeaconEffects parseEffects(CommandSender sender, String[] input, boolean allowZeroAmplifier) {
        HashMap<PotionEffectType, Short> effects = new HashMap<>();
        for (String s : input) {
            try {
                PotionEffectType type;
                short amplifier = 1;
                if (s.contains("*")) {
                    String[] split = s.split("\\*");
                    Preconditions.checkState(split.length == 2, "Invalid format, correct format is TYPE*AMPLIFIER");
                    type = parseType(split[0]);
                    amplifier = Short.parseShort(split[1]);
                } else {
                    type = parseType(s);
                }
                Preconditions.checkArgument(amplifier >= (allowZeroAmplifier ? 0 : 1), "Amplifier is negative");
                effects.put(type, amplifier);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + String.format("Skipped '%s' as it is not a valid potion effect (%s)", s, e.getMessage()));
            }
        }
        return new BeaconEffects(effects);
    }

    static void editPlayers(CommandSender sender, List<Player> players, Consumer<BeaconEffects> modifier, boolean silent, boolean modifyAll) {
        HashSet<Player> failedPlayers = new HashSet<>();

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

        sender.sendMessage(ChatColor.GREEN + "Modified " + (modifyAll ? "all instances of portable beacons on " : "held portable beacon of ") +
                players.stream()
                        .filter(player -> !failedPlayers.contains(player))
                        .map(Player::getName)
                        .collect(Collectors.joining(", ")));
        if (failedPlayers.size() != 0)
            sender.sendMessage(ChatColor.RED + "Failed to apply the operation on " +
                    failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                    " because they were not holding a portable beacon."
            );
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload": {
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                break;
            }
            case "saveconfig": {
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                break;
            }
            case "setitem":
            case "setritualitem": {
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
                        sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
                }
                break;
            }
            case "givebeacon": {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item give <player> <effects...>");
                    return true;
                }
                Player player = Bukkit.getPlayer(args[1]);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "No player called " + args[1]);
                    return true;
                }
                String[] effectsString = Arrays.copyOfRange(args, 2, args.length);
                BeaconEffects beaconEffects = parseEffects(sender, effectsString, false);
                player.getInventory().addItem(ItemUtils.createStack(beaconEffects));
                sender.sendMessage(ChatColor.GREEN + "Given " + args[1] + " a portable beacon with " +
                        String.join(ChatColor.WHITE + ", ", beaconEffects.toLore()));

                sender.sendMessage(ChatColor.RED + String.format("Please use /%s item give %s %s", label, args[1], String.join(" ", effectsString)));
                break;
            }
            case "updateitems": {
                Config.itemCustomVersion = UUID.randomUUID().toString().replace("-", "");
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "All portable beacon items will be forced to update soon.");
                sender.sendMessage(ChatColor.GREEN + "Configuration saved to file.");
                break;
            }
            case "inspect": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be run by a player!");
                    return true;
                }
                ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                if (!ItemUtils.isPortableBeacon(stack)) {
                    sender.sendMessage(ChatColor.RED + "You are not holding a portable beacon!");
                    return true;
                }
                BeaconEffects effects = ItemUtils.getEffects(stack);
                sender.sendMessage(ChatColor.GREEN + "Potion effects:");
                effects.getEffects().forEach((pot, amplifier) ->
                        sender.sendMessage("  " + ChatColor.YELLOW + VANILLA_EFFECT_NAMES.getOrDefault(pot.getName(), pot.getName()) + " " + amplifier));
                sender.sendMessage(ChatColor.GREEN + "Custom data ver: " + ChatColor.YELLOW + effects.customDataVersion);
                if (effects.soulboundLevel != 0 || effects.expReductionLevel != 0) {
                    sender.sendMessage(ChatColor.GREEN + "Enchantments:");
                    if (effects.expReductionLevel != 0)
                        sender.sendMessage("  " + ChatColor.YELLOW + "EXP_REDUCTION " + effects.expReductionLevel +
                                String.format(" (%.2f%% exp consumption)", Math.max(0, 1 - effects.expReductionLevel * Config.customEnchantExpReductionReductionPerLevel) * 100));
                    if (effects.soulboundLevel != 0)
                        sender.sendMessage("  " + ChatColor.YELLOW + "SOULBOUND " + effects.soulboundLevel + " (bound to " +
                                (effects.soulboundOwner != null ? Bukkit.getOfflinePlayer(effects.soulboundOwner).getName() : "no-one") + ")");
                }
                if (Config.itemNerfsExpPercentagePerCycle > 0) {
                    double xpPerCycle = effects.calcExpPerCycle();
                    sender.sendMessage(ChatColor.GREEN + "Exp %: " + ChatColor.YELLOW +
                            String.format("%.3f%%/7.5s, %.2f%%/min, %.2f%%/hour", xpPerCycle * 100, xpPerCycle * 8 * 100, xpPerCycle * 480 * 100));
                }
                break;
            }
            case "item": {
                String usage = ChatColor.RED + "Usage: /" + label + " item <give/add/set/remove/setenchantment>[-silently/-modify-all] <players> <effects...>";
                if (args.length < 4) {
                    sender.sendMessage(usage);
                    return true;
                }

                String[] operationWithFlags = args[1].split("-");
                String operation = operationWithFlags[0];
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

                Set<Player> failedPlayers = new HashSet<>();

                switch (operation) {
                    case "give": {
                        BeaconEffects beaconEffects = parseEffects(sender, effectsString, false);
                        ItemStack stack = ItemUtils.createStack(beaconEffects);
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
                                + " a portable beacon with " +
                                String.join(ChatColor.WHITE + ", ", beaconEffects.toLore()));
                        if (failedPlayers.size() != 0)
                            sender.sendMessage(ChatColor.RED + failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                                    " couldn't be given a portable beacon because their inventory is full."
                            );
                        break;
                    }
                    case "add":
                    case "set":
                    case "remove": {
                        BiFunction<Short, Short, Short> merger;
                        switch (operation) {
                            case "set": {
                                // remove if level is being set to 0
                                merger = (oldLevel, newLevel) -> newLevel == 0 ? null : newLevel;
                                break;
                            }
                            case "add": {
                                merger = (oldLevel, newLevel) -> (short) (oldLevel + newLevel);
                                break;
                            }
                            case "remove": {
                                // remove if resultant level <= 0
                                merger = (oldLevel, newLevel) -> {
                                    short result = (short) (oldLevel - newLevel);
                                    return result <= 0 ? null : result;
                                };
                                break;
                            }
                            default: // to make java shut up
                                throw new UnsupportedOperationException(operation);
                        }

                        BeaconEffects beaconEffects = parseEffects(sender, effectsString, true);
                        Map<PotionEffectType, Short> newEffects = beaconEffects.getEffects();
                        Consumer<BeaconEffects> modifier = effects -> {
                            HashMap<PotionEffectType, Short> map = new HashMap<>(effects.getEffects());
                            newEffects.forEach((pot, lvl) -> map.merge(pot, lvl, merger));
                            effects.setEffects(map);
                        };
                        editPlayers(sender, players, modifier, silent, modifyAll);
                        break;
                    }
                    case "setenchantment": {
                        if (args.length < 5) {
                            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item setenchantment <players> <enchantment> <level> [soulboundOwner]");
                            return true;
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
                                    return true;
                                }
                            } else {
                                modifier = effects -> effects.soulboundLevel = newLevel;
                            }
                        } else if (enchantment.equalsIgnoreCase("exp-reduction")) {
                            modifier = effects -> effects.expReductionLevel = newLevel;
                        } else {
                            sender.sendMessage(ChatColor.RED + "Invalid enchantment " + enchantment + "!");
                            return true;
                        }
                        editPlayers(sender, players, modifier, silent, modifyAll);
                        break;
                    }
                    default: {
                        sender.sendMessage(usage);
                        break;
                    }
                }
                break;
            }
            default: {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <reload/setritualitem/update/item> [...]");
                break;
            }
        }
        return true;
    }
}
