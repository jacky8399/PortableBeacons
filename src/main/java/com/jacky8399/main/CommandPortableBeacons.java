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
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandPortableBeacons implements TabExecutor {
    /**
     * A bi-map of Bukkit effect names to vanilla names
     */
    private static final ImmutableBiMap<String, String> VANILlA_EFFECT_NAMES = ImmutableBiMap.<String, String>builder()
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
                return Stream.of("setitem", "givebeacon", "reload", "updateitems", "inspect").filter(name -> name.startsWith(args[0])).collect(Collectors.toList());
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
                            .map(name -> VANILlA_EFFECT_NAMES.getOrDefault(name, name)) // try convert to vanilla names, fallback to Bukkit name
                            .filter(name -> name.startsWith(input)).collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }

    @Nullable
    public static PotionEffectType getType(String input) {
        input = input.toLowerCase(Locale.US);
        String bukkitName = VANILlA_EFFECT_NAMES.inverse().get(input);
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
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
                    if (args.length <= 2) {
                        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " givebeacon <player> <primaryEffect> [effects...]");
                        return true;
                    }
                    Player player = Bukkit.getPlayer(args[1]);
                    if (player == null) {
                        sender.sendMessage(ChatColor.RED + "No player called " + args[1]);
                        return true;
                    }
                    HashMap<PotionEffectType, Short> effects = Maps.newHashMap();
                    String[] effectsString = Arrays.copyOfRange(args, 2, args.length);
                    for (String s : effectsString) {
                        try {
                            Map.Entry<PotionEffectType, Short> effect = getEffect(s);
                            effects.put(effect.getKey(), effect.getValue());
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(ChatColor.RED + s + " is not a valid potion effect: " + e.getMessage());
                        }
                    }
                    BeaconEffects beaconEffects = new BeaconEffects(effects);
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
                    effects.getEffects().forEach((pot, amplifier)->
                            sender.sendMessage("" + ChatColor.YELLOW + VANILlA_EFFECT_NAMES.getOrDefault(pot.getName(), pot.getName()) + " " + amplifier));
                    sender.sendMessage(ChatColor.GREEN + "Custom data ver: " + ChatColor.YELLOW + effects.customDataVersion);
                    sender.sendMessage(ChatColor.GREEN + "Is obsolete: " + ChatColor.YELLOW + effects.shouldUpdate());
                    if (Config.itemNerfsExpPercentagePerCycle > 0) {
                        double xpPerCycle = effects.calcExpPerCycle();
                        sender.sendMessage(ChatColor.GREEN + "Exp %: " + ChatColor.YELLOW +
                                String.format("%.5f/7.5s, %.2f/min, %.2f/hour", xpPerCycle, xpPerCycle * 8, xpPerCycle * 480));
                    }
                }
                break;
            }
        } else {
            sender.sendMessage("You are running PortableBeacons " + PortableBeacons.INSTANCE.getDescription().getVersion());
        }
        return true;
    }
}
