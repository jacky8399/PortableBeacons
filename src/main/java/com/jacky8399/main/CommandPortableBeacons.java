package com.jacky8399.main;

import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandPortableBeacons implements TabExecutor {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getLabel().equals("portablebeacons")) {
            if (args.length <= 1) {
                return Stream.of("setitem", "givebeacon", "reload", "updateitems").filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
            } else if (args[0].equals("givebeacon")) {
                if (args.length <= 2) {
                    // show all players
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(name -> name.startsWith(args[1])).collect(Collectors.toList());
                } else {
                    // show potion effects
                    return Arrays.stream(PotionEffectType.values()).map(PotionEffectType::getName).map(String::toLowerCase)
                            .filter(name -> name.startsWith(args[args.length - 1])).collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "reload": {
                    PortableBeacons.INSTANCE.reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                }
                break;
                case "setitem": {
                    if (sender instanceof Player) {
                        ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                        if (stack.getType() == Material.AIR) {
                            sender.sendMessage(ChatColor.RED + "You must be holding an item!");
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
                    if (args.length <= 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " givebeacon <player> <primaryEffect> [effects...]");
                        return true;
                    }
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage(ChatColor.RED + "No player called " + args[1]);
                        return true;
                    }
                    List<PotionEffectType> effects = Lists.newArrayList();
                    for (int i = 2; i < args.length; i++) {
                        String typeName = args[i];
                        int amplifier = 1;
                        if (args[i].contains("*")) {
                            String[] split = args[i].split("\\*");
                            typeName = split[0];
                            try {
                                amplifier = Integer.parseInt(split[1]);
                            } catch (NumberFormatException ex) {
                                sender.sendMessage(ChatColor.RED + split[1] + " is not a valid number");
                            }
                        }
                        PotionEffectType type = PotionEffectType.getByName(typeName);
                        if (type == null) {
                            sender.sendMessage(ChatColor.RED + "No potion effect called " + typeName);
                            return true;
                        }
                        for (int z = 0; z < amplifier; z++) {
                            effects.add(type);
                        }
                    }
                    BeaconEffects beaconEffects = new BeaconEffects(effects.toArray(new PotionEffectType[0]));
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
                        break;
                    }
                    ItemStack stack = ((Player) sender).getInventory().getItemInMainHand();
                    if (!ItemUtils.isPortableBeacon(stack)) {
                        sender.sendMessage(ChatColor.RED + "You are not holding a portable beacon!");
                        break;
                    }
                    BeaconEffects effects = ItemUtils.getEffects(stack);
                    sender.sendMessage(ChatColor.GREEN + "Potion effects:");
                    effects.consolidateEffects().forEach((pot, amplifier)->sender.sendMessage(ChatColor.YELLOW.toString() + pot.toString() + " level " + amplifier));
                    sender.sendMessage(ChatColor.GREEN + "Custom data ver: " + ChatColor.YELLOW + effects.customDataVersion);
                    sender.sendMessage(ChatColor.GREEN + "Is obsolete: " + ChatColor.YELLOW + effects.shouldUpdate());
                }
                break;
            }
        } else {
            sender.sendMessage("You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
        }
        return true;
    }
}
