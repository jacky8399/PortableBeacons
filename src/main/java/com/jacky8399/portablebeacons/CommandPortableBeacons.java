package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.recipes.*;
import com.jacky8399.portablebeacons.utils.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jacky8399.portablebeacons.utils.CommandUtils.displayItem;
import static net.md_5.bungee.api.ChatColor.*;
import static net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE;

public class CommandPortableBeacons implements TabExecutor {
    private static final String COMMAND_PERM = "portablebeacons.command.";


    private static final String ITEM_USAGE = "item <give/add/set/subtract/setowner/filter>[-silently/-modify-all] <players> <...>";
    private static final String RECIPE_USAGE = "recipe <list/create/info/enable/disable/testinput> [...]";
    private static final String RECIPE_CREATE_USAGE = "recipe create <id> <type> [item] [itemAmount] <expCost> <action> <modifications...>";

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        Stream<String> tabCompletion = tabComplete(sender, args);
        if (tabCompletion == null)
            return Collections.emptyList();
        String input = args[args.length - 1];
        return tabCompletion
                .filter(completion -> completion.startsWith(input))
                .toList();
    }

    private static final Pattern EXP_COST_PATTERN = Pattern.compile("^\\d+|dy");
    private Stream<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return Stream.of("setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect", "toggle", "recipe")
                    .filter(subcommand -> sender.hasPermission(COMMAND_PERM + subcommand));
        } else if (args[0].equalsIgnoreCase("item") && sender.hasPermission(COMMAND_PERM + "item")) {
            String operationArg = args[1].split("-", 2)[0];
            if (args.length == 2) {
                String[] operations = {"give", "add", "subtract", "set", "filter", "setowner", "update"};
                String[] flags = {"", "-silently", "-modify-all", "-silently-modify-all", "-modify-all-silently"};
                List<String> completions = new ArrayList<>();
                for (String operation : operations) {
                    // check perm
                    if (!sender.hasPermission(COMMAND_PERM + "item." + operation))
                        continue;
                    // flags
                    if (operation.equalsIgnoreCase(operationArg)) {
                        completions.add(operation);
                        for (String flag : flags) {
                            completions.add(operation + flag);
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
                    return CommandUtils.listFilter(input);
                }
            }
            return CommandUtils.listModifications(args[args.length - 1], !"give".equalsIgnoreCase(args[1]));
        } else if (args[0].equalsIgnoreCase("inspect") && args.length == 2 &&
                sender.hasPermission(COMMAND_PERM + "inspect")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission(COMMAND_PERM + "toggle")) {
            if (args.length == 2) {
                return Config.TOGGLES.keySet().stream()
                        .filter(toggle -> sender.hasPermission(COMMAND_PERM + "toggle." + toggle));
            } else if (args.length == 3) {
                return Stream.of("true", "false");
            }
        } else if (args[0].equalsIgnoreCase("recipe") && sender.hasPermission(COMMAND_PERM + "recipe")) {
            if (args.length == 2) {
                return Stream.of("list", "create", "enable", "disable", "testinput", "info")
                        .filter(cmd -> sender.hasPermission(COMMAND_PERM + "recipe." + cmd));
            }
            switch (args[1]) {
                case "create" -> {
                    if (!sender.hasPermission(COMMAND_PERM + "recipe.create"))
                        return null;
                    boolean looksLikeItem = args.length >= 6 && !args[4].isEmpty() && !args[5].isEmpty() &&
                            !EXP_COST_PATTERN.matcher(args[4]).find();
                    return switch (args.length - (looksLikeItem ? 2 : 0)) {
                        // <id>
                        case 3 -> null;
                        // <type>
                        case 4 -> Stream.of("anvil", "smithing");
                        // [item]/<expCost>
                        case 5 -> {
                            if (looksLikeItem) {
                                yield CommandUtils.listExpCosts(args[args.length - 1]);
                            } else {
                                yield Arrays.stream(Material.values())
                                        .map(Material::getKey)
                                        .map(NamespacedKey::toString);
                            }
                        }
                        // [itemAmount]/<action>
                        case 6 -> {
                            if (looksLikeItem) {
                                yield Stream.of("add", "set", "subtract");
                            } else {
                                yield null;
                            }
                        }
                        // <modifications...>
                        default -> CommandUtils.listModifications(args[args.length - 1], true);
                    };
                }
                case "enable", "disable", "testinput", "info" -> {
                    if (!sender.hasPermission(COMMAND_PERM + "recipe." + args[1]))
                        return null;
                    if (args.length != 3)
                        return null;
                    return RecipeManager.RECIPES.keySet().stream();
                }
            }
        }
        return null;
    }

    private boolean checkPermission(CommandSender sender, String permission) {
        boolean result = sender.hasPermission(COMMAND_PERM + permission);
        if (!result)
            sender.sendMessage(RED + "You don't have permission to do this!");
        return result;
    }

    private void assertPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(COMMAND_PERM + permission))
            throw new IllegalStateException(RED + "You don't have permission to do this!");
    }

    private RuntimeException promptUsage(String label, String usage) {
        return new RuntimeException("Usage: /" + label + " " + usage);
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
        try {
            runCommand(sender, command, label, args);
        } catch (Exception ex) {
            sender.sendMessage(RED + ex.getMessage());
            Throwable cause = ex.getCause();
            while (cause != null) {
                sender.sendMessage(RED + "(Caused by " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")");
                cause = cause.getCause();
            }
            if (Config.debug) {
                ex.printStackTrace();
            }
        }
        return true;
    }

    public void runCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        switch (args[0].toLowerCase(Locale.ENGLISH)) {
            case "reload" -> {
                if (!checkPermission(sender, "reload"))
                    return;
                PortableBeacons.INSTANCE.reloadConfig();
                sender.sendMessage(GREEN + "Configuration reloaded.");
            }
            case "saveconfig" -> {
                if (!checkPermission(sender, "saveconfig"))
                    return;
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "Configuration saved to file.");
            }
            case "toggle" -> {
                if (!checkPermission(sender, "toggle"))
                    return;
                if (args.length < 2)
                    throw promptUsage(label, "toggle <toggleName> [true/false]");
                if ("anvil-combination".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(YELLOW + "Please use /" + label + " recipe disable anvil-combination");
                    return;
                }
                String toToggle = args[1].toLowerCase(Locale.ENGLISH);
                Field field = Config.TOGGLES.get(toToggle);
                if (field == null)
                    throw new IllegalArgumentException("Invalid toggle option " + toToggle);
                if (!checkPermission(sender, "toggle." + toToggle))
                    return;
                Boolean newValue = args.length == 3 ? Boolean.valueOf(args[2]) : null;
                try {
                    if (newValue == null)
                        newValue = !field.getBoolean(null);
                    field.setBoolean(null, newValue);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException("Couldn't toggle " + toToggle, ex);
                }
                sender.sendMessage(YELLOW + "Temporarily set " + GREEN + toToggle + YELLOW + " to " + (newValue ? GREEN : RED) + newValue);
                sender.sendMessage(YELLOW + "To make your change persist after a reload, do " + GREEN + "/" + label + " saveconfig");
            }
            case "setritualitem" -> {
                if (!checkPermission(sender, "setritualitem"))
                    return;
                ItemStack stack;
                if (args.length > 1) {
                    stack = Bukkit.getItemFactory().createItemStack(args[1]);
                    stack.setAmount(args.length >= 3 ? Integer.parseInt(args[2]) : 1);
                } else if (sender instanceof Player player) {
                    stack = player.getInventory().getItemInMainHand();
                } else {
                    throw promptUsage(label, "setritualitem [item] [amount]");
                }
                ItemStack oldRitualItem = Config.ritualItem;
                var builder = new ComponentBuilder("Changed ritual item from ").color(GREEN);
                if (oldRitualItem.getType() == Material.AIR)
                    builder.append("air").color(YELLOW);
                else
                    builder.append(displayItem(oldRitualItem));
                builder.append(" to ").color(GREEN);

                if (stack.getType() == Material.AIR) {
                    Config.ritualItem = new ItemStack(Material.AIR);
                    sender.spigot().sendMessage(builder.append("air").color(BLUE).create());
                    if (Config.pickupEnabled) {
                        sender.sendMessage("""
                                %sThis allows players to create portable beacons by breaking existing beacons%s
                                %sTo %sTURN OFF%s this feature, do /%s toggle world-pickup false
                                %sTo %sturn off%s the ritual, do /%s toggle ritual false""".formatted(
                                RED, (Config.pickupRequireSilkTouch ? " with silk touch" : ""),
                                YELLOW, BOLD, "" + RESET + YELLOW, label,
                                YELLOW, RED, YELLOW, label));
                    } else {
                        sender.sendMessage("""
                                %sThe ritual has been %sdisabled%s.
                                %sTo allow players to create portable beacons by breaking existing beacons%s,
                                %sdo /%s toggle world-placement. (The feature is currently %sOFF%s)""".formatted(
                                YELLOW, RED, YELLOW,
                                YELLOW, (Config.pickupRequireSilkTouch ? " with silk touch" : ""),
                                YELLOW, label, RED, YELLOW));
                    }
                } else {
                    Config.ritualItem = stack.clone();
                    sender.spigot().sendMessage(builder.append(displayItem(stack)).create());
                }
                // clear old ritual items
                Events.INSTANCE.ritualItems.clear();
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "Configuration saved to file.");
            }
            case "updateitems" -> {
                if (!checkPermission(sender, "updateitems"))
                    return;
                // six characters
                Config.itemCustomVersion = Integer.toHexString(ThreadLocalRandom.current().nextInt(0xFFFFFF + 1));
                PortableBeacons.INSTANCE.saveConfig();
                sender.sendMessage(GREEN + "All portable beacon items will be forced to update soon.\n" +
                        GREEN + "Configuration saved to file.");
            }
            case "inspect" -> {
                if (!checkPermission(sender, "inspect"))
                    return;
                Player target;
                if (args.length > 1) {
                    target = Objects.requireNonNull(Bukkit.getPlayer(args[1]), "Player " + args[1] + " not found");
                } else if (sender instanceof Player player) {
                    target = player;
                } else {
                    throw new RuntimeException("You must specify a player!");
                }
                doInspect(sender, target);
            }
            case "item" -> {
                if (!checkPermission(sender, "item"))
                    return;
                doItem(sender, args, label);
            }
            case "recipe" -> {
                if (!checkPermission(sender, "recipe"))
                    return;
                doRecipe(sender, args, label);
            }
            default -> throw promptUsage(label, "<reload/setritualitem/updateitems/inspect/item/toggle/recipe> [...]");
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
        Map<PotionEffectType, Integer> potions = effects.getEffects();
        Map<PotionEffectType, Integer> effectivePotions = effectiveEffects.getEffects();
        sender.sendMessage(GREEN + "Potion effects:");
        for (var entry : potions.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue();
            // format used in commands
            String internalFormat = PotionEffectUtils.getName(type) + (level != 1 ? "=" + level : "");
            BaseComponent[] potionDisplay = new ComponentBuilder()
                    .append("  ") // indentation
                    .append(TextComponent.fromLegacyText(PotionEffectUtils.getDisplayName(type, level)))
                    .append(" ", NONE)
                    .append("(" + internalFormat + ")").color(YELLOW)
                    .event(CommandUtils.showText(new ComponentBuilder("Used in commands\nClick to copy!").color(YELLOW).create()))
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, internalFormat))
                    .create();

            String disabledMsg = null; // hover event, null if not disabled
            if (isInDisabledWorld) {
                disabledMsg = "Target is in a world where Portable Beacons are disabled!\n" +
                        "Check config 'beacon-items.nerfs.disabled-worlds'.";
            } else if (!canUseBeacons) {
                disabledMsg = "Target is in a WorldGuard region where Portable Beacons are disabled!\n" +
                        "Check 'allow-portable-beacons' of the region.";
            } else if (!effectivePotions.containsKey(type)) {
                disabledMsg = "Target is in a WorldGuard region where " + PotionEffectUtils.getName(entry.getKey()) +
                        " is disabled!\nCheck 'allowed-beacon-effects'/'blocked-beacon-effects' of the region.";
            } else if (effects.getDisabledEffects().contains(type)) { // disabled
                disabledMsg = "Target has disabled the effect!";
            }

            if (disabledMsg != null) {
                // an interesting way to apply strikethrough to the entire component
                TextComponent potionDisplayStrikethrough = new TextComponent(potionDisplay);

                BaseComponent[] actualDisplay = new ComponentBuilder()
                        .append(potionDisplayStrikethrough).strikethrough(true)
                        .append(" DISABLED ", NONE)
                        .color(DARK_GRAY).italic(true)
                        .append("[?]", NONE)
                        .color(DARK_GRAY)
                        .event(CommandUtils.showText(new ComponentBuilder(disabledMsg).color(RED).create()))
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
            if (effects.expReductionLevel != 0) {
                double multiplier = Math.max(0, 1 - effects.expReductionLevel * Config.enchExpReductionReductionPerLevel) * 100;
                sender.spigot().sendMessage(new ComponentBuilder("  ")
                        .append(Config.enchExpReductionName + " " + effects.expReductionLevel)
                        .append(" (", NONE)
                        .append("%.2f".formatted(multiplier) + "% exp consumption)")
                        .create());
            }
            if (effects.soulboundLevel != 0) {
                var builder = new ComponentBuilder("  ")
                        .append(Config.enchSoulboundName + " " + effects.soulboundLevel);
                if (effects.soulboundOwner != null) {
                    String name = Bukkit.getOfflinePlayer(effects.soulboundOwner).getName();
                    builder.append("(" + (name != null ? name : effects.soulboundOwner.toString()) + ")", NONE)
                            .event(CommandUtils.showEntity(EntityType.PLAYER, effects.soulboundOwner,
                                    name != null ? new TextComponent(name) : null));
                } else {
                    builder.append("(not bound to a player)", NONE);
                }
                sender.spigot().sendMessage(builder.create());
            }
        }
        if (Config.nerfExpLevelsPerMinute > 0) {
            double perMinute = effects.calcExpPerMinute();
            String expUnit = " levels";
            if (showHoverMessages) {
                TextComponent consumptionText = new TextComponent(
                        new ComponentBuilder(String.format("%.2f%s/min", perMinute, expUnit)).color(YELLOW)
                                .append(" [hover for details]").color(GRAY)
                                .create()
                );
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

    public void doItem(CommandSender sender, String[] args, String label) {
        CommandUtils.ArgumentParser parser = CommandUtils.parse(sender, label, ITEM_USAGE, args, 1);

        String[] operationWithFlags = parser.popWord().split("-");
        if (operationWithFlags[0].equals("remove"))
            operationWithFlags[0] = "subtract";

        String operation = operationWithFlags[0];
        assertPermission(sender, "item." + operation);
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

        List<Player> players = parser.popPlayers(false);

        Predicate<BeaconEffects> modification;
        switch (operation) {
            case "give" -> {
                parser.updateUsage("item give <players> <effects...>");
                BeaconEffects beaconEffects = CommandUtils.parseEffects(sender, parser.popRemainingInput(), false);
                ItemStack stack = ItemUtils.createStack(beaconEffects);
                Set<Player> failedPlayers = new HashSet<>();
                for (Player p : players) {
                    Map<Integer, ItemStack> unfit = p.getInventory().addItem(stack);
                    if (unfit.size() != 0 && unfit.get(0) != null && unfit.get(0).getAmount() != 0)
                        failedPlayers.add(p);
                    else if (!silent)
                        p.sendMessage(GREEN + "You were given a portable beacon!");
                }

                sender.sendMessage(GREEN + "Given " + players.stream()
                        .filter(player -> !failedPlayers.contains(player))
                        .map(Player::getName)
                        .collect(Collectors.joining(", "))
                        + " a portable beacon with " + String.join(WHITE + ", ", beaconEffects.toLore()));
                if (failedPlayers.size() != 0)
                    sender.sendMessage(RED + failedPlayers.stream().map(Player::getName).collect(Collectors.joining(", ")) +
                            " couldn't be given a portable beacon because their inventory is full.");
                return; // don't need to modify inventory
            }
            case "update" -> modification = ignored -> true;
            case "setowner" -> {
                parser.updateUsage("item setowner <players> <ownerUUID/ownerName>");
                UUID uuid;
                try {
                    uuid = UUID.fromString(parser.popWord());
                } catch (IllegalArgumentException ignored) {
                    Player newOwner = Bukkit.getPlayer(parser.popWord());
                    if (newOwner != null) {
                        uuid = newOwner.getUniqueId();
                    } else {
                        throw parser.throwUsage("Invalid UUID or player name '" + args[3] + "'");
                    }
                }
                UUID finalUuid = uuid;
                modification = effects -> {
                    effects.soulboundOwner = finalUuid;
                    return true;
                };
            }
            case "filter" -> {
                parser.updateUsage("item filter <players> <allow/block> <filters...>");
                boolean whitelist = parser.popWord().equalsIgnoreCase("allow");
                String[] filtersString = parser.popRemainingInput();
                Set<BeaconEffectsFilter> filters = new HashSet<>();
                for (String filterString : filtersString) {
                    try {
                        filters.add(BeaconEffectsFilter.fromString(filterString));
                    } catch (IllegalArgumentException e) {
                        throw parser.throwUsage("Invalid filter " + filterString + ": " + e.getMessage());
                    }
                }
                modification = effects -> {
                    effects.filter(filters, whitelist);
                    return true;
                };
            }
            default -> {
                parser.updateUsage("item " + args[1] + " <players> <effects...>");
                var modificationType = BeaconModification.Type.parseType(operation);

                BeaconEffects virtual = CommandUtils.parseEffects(sender, parser.popRemainingInput(), true);
                modification = new BeaconModification(modificationType, virtual, true);
            }
        }
        Set<Player> failedPlayers = new LinkedHashSet<>();

        for (Player player : players) {
            if (modifyAll) { // modify inventory
                boolean success = false;
                for (ListIterator<ItemStack> iterator = player.getInventory().iterator(); iterator.hasNext(); ) {
                    ItemStack stack = iterator.next();
                    BeaconEffects effects = ItemUtils.getEffects(stack);
                    if (effects == null)
                        continue;
                    success = modification.test(effects);
                    if (success)
                        iterator.set(ItemUtils.createStackCopyItemData(effects, stack));
                }
                if (success) {
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
                if (modification.test(effects)) {
                    inventory.setItemInMainHand(ItemUtils.createStackCopyItemData(effects, hand));
                    if (!silent)
                        player.sendMessage(GREEN + "Your portable beacon was modified!");
                } else {
                    failedPlayers.add(player);
                }
            }
        }

        sender.sendMessage(GREEN + "Modified " + (modifyAll ? "beacons on " : "held beacon of ") +
                players.stream()
                        .filter(player -> !failedPlayers.contains(player))
                        .map(Player::getName)
                        .collect(Collectors.joining(", ")));
        if (failedPlayers.size() != 0)
            sender.sendMessage(RED + "Failed to apply the operation on " + failedPlayers.stream()
                    .map(Player::getName)
                    .collect(Collectors.joining(", ")) +
                    " because they " + (modifyAll ? "did not own a portable beacon" : "were not holding a portable beacon."));
    }

    public void doRecipe(CommandSender sender, String[] args, String label) {
        CommandUtils.ArgumentParser parser = CommandUtils.parse(sender, label, RECIPE_USAGE, args, 1);
        switch (parser.popWord()) {
            default -> throw parser.throwUsage();
            case "list" -> {
                assertPermission(sender, "recipe.list");
                sender.sendMessage(GREEN + "Enabled recipes:");
                for (var entry : RecipeManager.RECIPES.entrySet())
                    sender.sendMessage("  " + entry.getKey());
                if (RecipeManager.DISABLED_RECIPES.size() != 0) {
                    sender.sendMessage(RED + "Disabled recipes:");
                    for (var entry : RecipeManager.DISABLED_RECIPES)
                        sender.sendMessage("  " + entry);
                }
            }
            case "create" -> {
                assertPermission(sender, "recipe.create");

                parser.updateUsage(RECIPE_CREATE_USAGE);
                String id = parser.popWord();
                InventoryType type = InventoryType.valueOf(parser.popWord().toUpperCase(Locale.ENGLISH));
                ItemStack stack = parser.tryPopItemStack();

                ExpCostCalculator expCost = ExpCostCalculator.deserialize(parser.popWord());
                BeaconModification.Type action = BeaconModification.Type.parseType(parser.popWord());
                BeaconEffects virtualEffects = CommandUtils.parseEffects(sender, parser.popRemainingInput(), true);
                BeaconModification modification = new BeaconModification(action, virtualEffects, false);

                SimpleRecipe recipe = new SimpleRecipe(id, type, stack, List.of(modification), expCost);

                // save in YAML
                var yaml = YamlConfiguration.loadConfiguration(RecipeManager.RECIPES_FILE);
                if (yaml.isConfigurationSection("recipes." + id)) {
                    throw new IllegalArgumentException("There is already a recipe with ID " + id);
                }
                yaml.createSection("recipes." + id, recipe.save());
                try {
                    yaml.save(RecipeManager.RECIPES_FILE);
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to save recipe " + id, ex);
                }
                sender.sendMessage(GREEN + "Recipe " + id + " created and saved to recipes.yml\n" +
                                   YELLOW + "To apply your changes, do /" + label + " reload");
            }
            case "enable", "disable" -> {
                assertPermission(sender, "recipe." + args[1]);

                parser.updateUsage("recipe " + args[1] + " <id>");
                String id = parser.popWord();
                var yaml = YamlConfiguration.loadConfiguration(RecipeManager.RECIPES_FILE);
                if (!yaml.isConfigurationSection("recipes." + id))
                    throw new IllegalArgumentException("Can't find recipe with ID " + id);
                yaml.set("recipes." + id + ".enabled", args[1].equals("enable"));
                yaml.setComments("recipes." + id + ".enabled", List.of("Modified by " + sender.getName()));
                try {
                    yaml.save(RecipeManager.RECIPES_FILE);
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to toggle recipe " + id, ex);
                }
                sender.sendMessage(GREEN + "Recipe " + id + " set to " + args[1] + "\n" +
                                   YELLOW + "To apply your changes, do /" + label + " reload");
            }
            case "testinput" -> {
                assertPermission(sender, "recipe.testinput");

                parser.updateUsage("recipe testinput <id> [item]");
                String id = parser.popWord();
                ItemStack stack = parser.tryPopItemStack();

                BeaconRecipe recipe = RecipeManager.RECIPES.get(id);
                if (!(recipe instanceof SimpleRecipe simpleRecipe))
                    throw new IllegalArgumentException("Can't find recipe with ID " + id);
                // ensure stack size
                stack.setAmount(simpleRecipe.input().getAmount());
                if (simpleRecipe.isApplicableTo(new ItemStack(Material.BEACON), stack)) {
                    sender.spigot().sendMessage(
                            new ComponentBuilder("Recipe " + id + " would accept ").color(GREEN)
                                    .append(displayItem(stack)).color(BLUE).create()
                    );
                } else {
                    if (Config.debug) // show Bukkit ItemMeta info
                        sender.sendMessage(RED + "Recipe " + id + " would reject " + BLUE + stack + RED + " because it wants " +
                                YELLOW + simpleRecipe.input());
                    else
                        sender.spigot().sendMessage(
                                new ComponentBuilder("Recipe " + id + " would reject ").color(RED)
                                        .append(displayItem(stack)).color(BLUE)
                                        .append(" because it wants ", NONE).color(RED)
                                        .append(displayItem(simpleRecipe.input())).color(YELLOW).create()
                        );
                }
            }
            case "info" -> {
                assertPermission(sender, "recipe.info");
                parser.updateUsage("recipe info <id>");

                String id = parser.popWord();
                BeaconRecipe recipe = RecipeManager.RECIPES.get(id);
                var builder = new ComponentBuilder("Recipe " + id + "\n").color(GREEN);
                if (recipe instanceof CombinationRecipe combinationRecipe) {
                    builder.append("Type: Beacon Combination\n").color(AQUA);
                    builder.append("Max. " + combinationRecipe.maxEffects() + " effects\n").color(YELLOW);
                    builder.append("Combining effects " + (combinationRecipe.combineEffectsAdditively() ?
                            "additively" : "like enchantments") + "\n").color(YELLOW);
                } else if (recipe instanceof SimpleRecipe simpleRecipe) {
                    builder.append("Type: Simple Recipe\n" +
                            "Recipe type: " + simpleRecipe.type()).color(YELLOW);
                    builder.append("\nAccepts: ").color(WHITE)
                            .append(displayItem(simpleRecipe.input()))
                            .append("\n").color(WHITE);
                    for (var modification : simpleRecipe.modifications()) {
                        builder.append(modification.type().name() + "s:\n").color(BLUE);
                        var effects = modification.virtualEffects().save(true);
                        for (var entry : effects.entrySet()) {
                            builder.append("  ").append(entry.getKey() + "=" + entry.getValue() + "\n").color(GRAY);
                        }
                    }
                    for (var special : simpleRecipe.specialOperations()) {
                        builder.append(switch (special) {
                            case SET_SOULBOUND_OWNER -> "Set soulbound owner to player\n";
                        }).color(YELLOW);
                    }
                } else {
                    throw new IllegalArgumentException("Can't find recipe with ID " + id);
                }
                builder.append("Exp cost: " + (recipe.expCost() instanceof ExpCostCalculator.Fixed fixed ?
                        fixed.level() + " levels" :
                        recipe.expCost().serialize()));
                sender.spigot().sendMessage(builder.create());
            }
        }
    }
}
