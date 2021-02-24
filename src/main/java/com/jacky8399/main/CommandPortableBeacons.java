package com.jacky8399.main;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
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
        if (command.getLabel().equals("portablebeacons")) {
            if (args.length <= 1) {
                return Stream.of("setritualitem", "item", "reload", "updateitems", "inspect").filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
            } else if (args[0].equals("givebeacon")) {
                if (args.length <= 2) {
                    // show all players
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(name -> name.startsWith(args[1])).collect(Collectors.toList());
                } else {
                    String input = args[args.length - 1];
                    if (getType(input) != null) {
                        // valid input, show amplifiers
                        return IntStream.range(1, 10).mapToObj(i -> input + "*" + i).collect(Collectors.toList());
                    }
                    // show potion effects
                    return Arrays.stream(PotionEffectType.values()).map(PotionEffectType::getName).map(String::toLowerCase)
                            .map(name -> VANILLA_EFFECT_NAMES.getOrDefault(name, name)) // try convert to vanilla names, fallback to Bukkit name
                            .filter(name -> name.startsWith(input)).collect(Collectors.toList());
                }
            } else if (args[0].equals("item")) {
                switch (args.length) {
                    case 2:
                        return Stream.of("give", "add", "remove", "setenchantment")
                                .filter(name -> name.startsWith(args[1]))
                                .collect(Collectors.toList());
                    case 3:
                        return Stream.concat(
                                Stream.of("@a", "@s", "@p"),
                                Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        ).filter(name -> name.startsWith(args[2])).collect(Collectors.toList());
                    default: {
                        // Enchantment autocomplete
                        if (args[1].equals("setenchantment")) {
                            if (args.length == 4)
                                return Stream.of("exp-reduction", "soulbound")
                                        .filter(name -> name.startsWith(args[3]))
                                        .collect(Collectors.toList());
                            else {
                                // levels
                                int maxLevel = args[3].equals("exp-reduction") ? Config.customEnchantExpReductionMaxLevel : Config.customEnchantSoulboundMaxLevel;
                                return IntStream.rangeClosed(1, maxLevel)
                                        .mapToObj(Integer::toString)
                                        .filter(level -> level.startsWith(args[4]))
                                        .collect(Collectors.toList());
                            }
                        }

                        // Potion effect type autocomplete
                        String input = args[args.length - 1];
                        if (getType(input) != null) {
                            // valid input, show amplifiers
                            return IntStream.range(1, 10).mapToObj(i -> input + "*" + i).collect(Collectors.toList());
                        }
                        // show potion effects
                        return Arrays.stream(PotionEffectType.values()).map(PotionEffectType::getName).map(String::toLowerCase)
                                .map(name -> VANILLA_EFFECT_NAMES.getOrDefault(name, name)) // try convert to vanilla names, fallback to Bukkit name
                                .filter(name -> name.startsWith(input)).collect(Collectors.toList());
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    public static PotionEffectType getType(String input) {
        input = input.toLowerCase(Locale.US);
        String bukkitName = VANILLA_EFFECT_NAMES.inverse().get(input);
        return PotionEffectType.getByName(bukkitName != null ? bukkitName : input);
    }

    @NotNull
    public static Map.Entry<PotionEffectType, Short> getEffect(String input) throws IllegalArgumentException {
        PotionEffectType type;
        short amplifier = 1;
        if (input.contains("*")) {
            String[] split = input.split("\\*");
            type = getType(split[0]);
            amplifier = Short.parseShort(split[1]);
        } else {
            type = getType(input);
        }
        Preconditions.checkArgument(type != null, "Invalid type: " + input);
        Preconditions.checkArgument(amplifier >= 1, "Invalid amplifier: " + amplifier);
        return Maps.immutableEntry(type, amplifier);
    }

    public static BeaconEffects getEffects(CommandSender sender, String[] input) {
        HashMap<PotionEffectType, Short> effects = new HashMap<>();
        for (String s : input) {
            try {
                Map.Entry<PotionEffectType, Short> effect = getEffect(s);
                effects.put(effect.getKey(), effect.getValue());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + s + " is not a valid potion effect: " + e.getMessage());
            }
        }
        return new BeaconEffects(effects);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
            return true;
        }

        switch (args[0]) {
            case "reload": {
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            }
            break;
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
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
                }
            }
            break;
            case "givebeacon": {
                sender.sendMessage(ChatColor.RED + "Please use /" + label + " item give <players> <effects...>");
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " givebeacon <player> <effects...>");
                    return true;
                }
                Player player = Bukkit.getPlayer(args[1]);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "No player called " + args[1]);
                    return true;
                }
                String[] effectsString = Arrays.copyOfRange(args, 2, args.length);
                BeaconEffects beaconEffects = getEffects(sender, effectsString);
                player.getInventory().addItem(ItemUtils.createStack(beaconEffects));
                sender.sendMessage(ChatColor.GREEN + "Given " + args[1] + " a portable beacon with " +
                        String.join(ChatColor.WHITE + ", ", beaconEffects.toLore()));
            }
            break;
            case "updateitems": {
                Config.itemCustomVersion = UUID.randomUUID().toString().replace("-", "");
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "All portable beacon items will be forced to update.");
            }
            break;
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
                effects.getEffects().forEach((pot, amplifier) -> sender.sendMessage("  " + ChatColor.YELLOW + VANILLA_EFFECT_NAMES.getOrDefault(pot.getName(), pot.getName()) + " " + amplifier));
                sender.sendMessage(ChatColor.GREEN + "Custom data ver: " + ChatColor.YELLOW + effects.customDataVersion);
                sender.sendMessage(ChatColor.GREEN + "Is obsolete: " + ChatColor.YELLOW + effects.shouldUpdate());
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
            }
            break;
            case "item": {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item <give/add/remove/setenchantment> <players> <effects...>");
                }
                String operation = args[1];
                List<Player> players = Bukkit.selectEntities(sender, args[2]).stream()
                        .filter(entity -> entity instanceof Player)
                        .map(player -> (Player) player).collect(Collectors.toList());
                String[] effectsString = Arrays.copyOfRange(args, 3, args.length);
                BeaconEffects beaconEffects = !operation.equals("setenchantment") ? getEffects(sender, effectsString) : null;

                Set<Player> failedPlayers = new HashSet<>();

                switch (operation) {
                    case "give": {
                        ItemStack stack = ItemUtils.createStack(beaconEffects);
                        players.forEach(player -> {
                            Map<Integer, ItemStack> unfit = player.getInventory().addItem(stack);
                            if (unfit.size() != 0 && unfit.get(0) != null && unfit.get(0).getAmount() != 0)
                                failedPlayers.add(player);
                            else
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
                    }
                    break;
                    case "add":
                    case "remove": {
                        players.forEach(player -> {
                            PlayerInventory inventory = player.getInventory();
                            ItemStack hand = inventory.getItemInMainHand();
                            if (!ItemUtils.isPortableBeacon(hand)) {
                                failedPlayers.add(player);
                                return;
                            }

                            BeaconEffects oldEffects = ItemUtils.getEffects(hand), newEffects = oldEffects.clone();
                            HashMap<PotionEffectType, Short> map = new HashMap<>(oldEffects.getEffects());
                            if ("add".equals(operation)) {
                                map.putAll(beaconEffects.getEffects());
                            } else {
                                // remove
                                map.keySet().removeAll(oldEffects.getEffects().keySet());
                            }
                            newEffects.setEffects(map);

                            inventory.setItemInMainHand(ItemUtils.createStackCopyItemData(newEffects, hand));
                            player.sendMessage(ChatColor.GREEN + "Your portable beacon was modified!");
                        });

                        sender.sendMessage(ChatColor.GREEN + "Modified the held portable beacon of " +
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
                    break;
                    case "setenchantment": {
                        if (args.length < 5) {
                            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item setenchantment <players> <enchantment> <level> [soulboundOwner]");
                            return true;
                        }

                        String enchantment = args[3];
                        int newLevel = Integer.parseInt(args[4]);
                        Consumer<BeaconEffects> modifier;

                        if (enchantment.equals("soulbound")) {
                            if (args.length >= 6) { // has soulboundOnwer arg
                                Player newOwner = Bukkit.getPlayer(args[5]);
                                if (newOwner != null) {
                                    modifier = effects -> {
                                        effects.soulboundLevel = newLevel;
                                        effects.soulboundOwner = newOwner.getUniqueId();
                                    };
                                } else {
                                    sender.sendMessage(ChatColor.RED + "Couldn't find player '" + args[5] + "'!");
                                    return true;
                                }
                            } else {
                                modifier = effects -> effects.soulboundLevel = newLevel;
                            }
                        } else if (enchantment.equals("exp-reduction")) {
                            modifier = effects -> effects.expReductionLevel = newLevel;
                        } else {
                            sender.sendMessage(ChatColor.RED + "Invalid enchantment " + enchantment + "!");
                            return true;
                        }

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
                            player.sendMessage(ChatColor.GREEN + "Your portable beacon was modified!");
                        });

                        sender.sendMessage(ChatColor.GREEN + "Modified the held portable beacon of " +
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
                    break;
                    default: {
                        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " item <give/add/remove/setenchantment> <players> <effects...>");
                    }
                    break;
                }
            }
            break;

            default: {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <reload/setritualitem/update/item> [...]");
            }
            break;
        }
        return true;
    }
}
