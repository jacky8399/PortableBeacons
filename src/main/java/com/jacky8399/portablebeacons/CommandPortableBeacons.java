package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.recipes.*;
import com.jacky8399.portablebeacons.utils.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jacky8399.portablebeacons.utils.CommandUtils.*;
import static net.md_5.bungee.api.ChatColor.*;
import static net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE;

public class CommandPortableBeacons implements TabExecutor {
    private static final String COMMAND_PERM = "portablebeacons.command.";


    private static final String ITEM_USAGE = "item <operation>[-silently/-modify-all] <players> [...]";
    private static final String RECIPE_USAGE = "recipe <...> [...]";
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

    private static final String[] ITEM_OPS = {"give", "add", "subtract", "set", "filter", "setowner", "update"};
    private static final String[] ITEM_OP_FLAGS = {"-silently", "-modify-all", "-silently-modify-all", "-modify-all-silently"};
    private Stream<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return Stream.of("help", "setritualitem", "item", "reload", "saveconfig", "updateitems", "inspect", "toggle", "recipe")
                    .filter(subcommand -> sender.hasPermission(COMMAND_PERM + subcommand));
        } else if (args[0].equalsIgnoreCase("item") && sender.hasPermission(COMMAND_PERM + "item")) {
            String operationInput = args[1];
            String operationArg = operationInput.split("-", 2)[0];
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();
                for (String operation : ITEM_OPS) {
                    // check perm
                    if (!sender.hasPermission(COMMAND_PERM + "item." + operation))
                        continue;
                    // flags
                    if (operation.equalsIgnoreCase(operationArg)) {
                        completions.add(operation);
                        for (String flag : ITEM_OP_FLAGS) {
                            completions.add(operation + flag);
                        }
                    } else if (operation.startsWith(operationInput)) {
                        completions.add(operation);
                    }
                }
                return completions.stream();
            } else if (args.length == 3) {
                return listSelectors(sender);
            }
            // Enchantment autocomplete
            if (operationArg.startsWith("setowner")) {
                if (args.length == 4)
                    return listPlayers(sender);
                else
                    return Stream.empty();
            } else if (operationArg.startsWith("filter")) { // filter autocomplete
                if (args.length == 4) {
                    return Stream.of("allow", "block");
                } else {
                    String input = args[args.length - 1];
                    return CommandUtils.listFilter(input);
                }
            }
            return CommandUtils.listModifications(args[args.length - 1], "set".equalsIgnoreCase(args[1]));
        } else if (args[0].equalsIgnoreCase("inspect") && args.length == 2 &&
                sender.hasPermission(COMMAND_PERM + "inspect")) {
            return listPlayers(sender);
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
                    if (args.length != 3)
                        return null;
                    if (!sender.hasPermission(COMMAND_PERM + "recipe." + args[1]))
                        return null;
                    return RecipeManager.RECIPES.keySet().stream();
                }
            }
        } else if (args[0].equalsIgnoreCase("help")) {
            return switch (args.length) {
                case 2 -> HELP_TOPICS.entrySet().stream()
                        // also check perms
                        .filter(entry -> entry.getValue().node == null ||
                                sender.hasPermission(COMMAND_PERM + entry.getValue().node))
                        .map(Map.Entry::getKey);
                case 3 -> {
                    HelpTopic topic = HELP_TOPICS.get(args[1]);
                    // also check perms
                    if (topic == null || topic.childrenTopics == null ||
                            (topic.node != null && !sender.hasPermission(COMMAND_PERM + topic.node)))
                        yield null;
                    yield topic.childrenTopics.entrySet().stream()
                            .filter(entry -> topic.node == null || entry.getValue().node == null ||
                                    sender.hasPermission(COMMAND_PERM + topic.node + "." + entry.getValue().node))
                            .map(Map.Entry::getKey);
                }
                default -> null;
            };
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
            if (cause != null) {
                sender.sendMessage(RED + cause.getMessage());
            }
            if (Config.debug) {
                while (cause != null) {
                    sender.sendMessage(RED + "(Caused by " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + ")");
                    cause = cause.getCause();
                }
                sender.sendMessage(YELLOW + "Refer to the console for more details.");
                PortableBeacons.LOGGER.log(Level.SEVERE, "Exception while handling command from " + sender + ": " +
                        "/" + label + " " + String.join(" ", args), ex);
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
                    throw promptUsage(label, "toggle <feature> [true/false]");
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
                    builder.append(TextUtils.displayItem(oldRitualItem));
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
                    sender.spigot().sendMessage(builder.append(TextUtils.displayItem(stack)).create());
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
            case "help" -> {
                if (!checkPermission(sender, "help"))
                    return;
                doHelp(sender, args, label);
            }
            default -> throw promptUsage(label, "<help/reload/setritualitem/updateitems/inspect/item/toggle/recipe> [...]");
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
                    .append(PotionEffectUtils.getDisplayName(type, level), NONE)
                    .append(" ", NONE)
                    .append("(" + internalFormat + ")").color(YELLOW)
                    .event(TextUtils.showText(new ComponentBuilder("Used in commands\nClick to copy!").color(YELLOW).create()))
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
                        .event(TextUtils.showText(new ComponentBuilder(disabledMsg).color(RED).create()))
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
        if (effects.soulboundLevel != 0 || effects.expReductionLevel != 0 || effects.beaconatorLevel != 0) {
            sender.sendMessage(GREEN + "Enchantments:");
            if (effects.expReductionLevel != 0) {
                double multiplier = Math.max(0, 1 - effects.expReductionLevel * Config.enchExpReductionReductionPerLevel) * 100;
                sender.spigot().sendMessage(new ComponentBuilder("  ")
                        .append(TextUtils.formatEnchantment(Config.enchExpReductionName, effects.expReductionLevel))
                        .append(" (" + DecimalFormat.getNumberInstance().format(multiplier) + "% exp consumption)", NONE)
                        .create());
            }
            if (effects.soulboundLevel != 0) {
                var builder = new ComponentBuilder("  ")
                        .append(TextUtils.formatEnchantment(Config.enchSoulboundName, effects.soulboundLevel));
                if (effects.soulboundOwner != null) {
                    String name = Bukkit.getOfflinePlayer(effects.soulboundOwner).getName();
                    builder.append(" (" + (name != null ? name : effects.soulboundOwner.toString()) + ")", NONE)
                            .event(TextUtils.showEntity(EntityType.PLAYER, effects.soulboundOwner,
                                    name != null ? new TextComponent(name) : null));
                } else {
                    builder.append("(not bound to a player)", NONE);
                }
                sender.spigot().sendMessage(builder.create());
            }
            if (effects.beaconatorLevel != 0) {
                var builder = new ComponentBuilder("  ")
                        .append(TextUtils.formatEnchantment(Config.enchBeaconatorName, effects.beaconatorLevel))
                        .append(" (" + Config.getBeaconatorLevel(effects.beaconatorLevel,
                                effects.beaconatorSelectedLevel).radius() + " block radius)", NONE);
                sender.spigot().sendMessage(builder.create());
            }
        }
        if (Config.nerfExpLevelsPerMinute > 0) {
            double perMinute = effects.calcBasicExpPerMinute(target);
            String expUnit = " levels";
            BaseComponent[] breakdown = effects.getExpCostBreakdown(target).stream()
                    .collect(TextUtils.joiningComponents(new TextComponent("\n")));
            if (showHoverMessages) {
                String consumptionHoverText = YELLOW + "Consumes " +
                        String.format(GREEN + "%.1f%3$s" + YELLOW + " / " + AQUA + "%.1f%3$s", perMinute / 16, perMinute * 60, expUnit) +
                        YELLOW + " per " + GREEN + "3.75s" + YELLOW + " / " + AQUA + "hour\n" +
                        YELLOW + "Consumes 1 exp level every " + GOLD + String.format("%.2fs", 60 / perMinute);

                sender.spigot().sendMessage(new ComponentBuilder("Exp upkeep: ").color(GREEN)
                        .append(new ComponentBuilder(String.format("%.2f%s/min", perMinute, expUnit)).color(YELLOW)
                                .append(" [Details]").color(GRAY)
                                .create(), NONE
                        )
                        .event(TextUtils.showText(TextComponent.fromLegacyText(consumptionHoverText)))
                        .append(new TextComponent(" [Breakdown]"), NONE).color(YELLOW)
                        .event(TextUtils.showText(breakdown))
                        .create());
            } else {
                sender.sendMessage(GREEN + "Exp upkeep: " + YELLOW + String.format(
                        "%.4f%4$s/minute, %.2f%4$s/3.75s, %.1f%4$s/hour",
                                perMinute, perMinute / 16, perMinute * 60, expUnit));
                sender.sendMessage(YELLOW + "Breakdown:");
                sender.spigot().sendMessage(breakdown);
            }

            if (effectiveEffects.beaconatorLevel != 0) {
                BeaconEffects.BeaconatorExpSummary expSummary = effectiveEffects.calcBeaconatorExpPerMinute(target);
                sender.sendMessage(TextUtils.formatEnchantment(Config.enchBeaconatorName, effectiveEffects.beaconatorLevel) + " upkeep: " + YELLOW +
                        TextUtils.TWO_DP.format(expSummary.getCost()) + "levels/minute");
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

        Function<BeaconEffects, Boolean> modification;
        switch (operation) {
            case "give" -> {
                parser.updateUsage("item give <players> <effects...>");
                BeaconEffects beaconEffects = CommandUtils.parseEffects(sender, parser.popRemainingInput(), false);
                var successfulPlayers = new ArrayList<String>();
                var failedPlayers = new ArrayList<String>();

                ItemStack stack = null;
                for (Player p : players) {
                    stack = ItemUtils.createStack(p, beaconEffects);
                    Map<Integer, ItemStack> unfit = p.getInventory().addItem(stack);
                    if (!unfit.isEmpty() && unfit.get(0) != null && unfit.get(0).getAmount() != 0) {
                        failedPlayers.add(p.getName());
                    } else {
                        successfulPlayers.add(p.getName());
                        if (!silent)
                            p.sendMessage(GREEN + "You were given a portable beacon!");
                    }
                }

                sender.spigot().sendMessage(
                        new ComponentBuilder("Given " + String.join(", ", successfulPlayers) + " a ").color(GREEN)
                                .append(TextUtils.displayItem(stack)) // not null since players is never empty
                                .create()
                );
                if (!failedPlayers.isEmpty())
                    sender.sendMessage(RED + String.join(", ", failedPlayers) +
                            " couldn't be given a portable beacon because their inventory is full.");
                return; // don't need to modify inventory
            }
            case "update" -> modification = ignored -> true;
            case "setowner" -> {
                parser.updateUsage("item setowner <players> <ownerUUID/ownerName>");
                UUID uuid;
                String input = parser.popWord();
                try {
                    uuid = UUID.fromString(input);
                } catch (IllegalArgumentException ignored) {
                    Player newOwner = Bukkit.getPlayer(input);
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
                var filters = new ArrayList<BeaconEffectsFilter>(filtersString.length);
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

                BeaconEffects virtual = CommandUtils.parseEffects(sender, parser.popRemainingInput(), modificationType.allowVirtual);
                modification = new BeaconModification(modificationType, virtual, true);
            }
        }
        Set<Player> failedPlayers = new LinkedHashSet<>();

        for (Player player : players) {
            PlayerInventory inventory = player.getInventory();
            if (modifyAll) { // modify inventory
                boolean success = false;
                for (ListIterator<ItemStack> iterator = inventory.iterator(); iterator.hasNext(); ) {
                    ItemStack stack = iterator.next();
                    BeaconEffects effects = ItemUtils.getEffects(stack);
                    if (effects == null)
                        continue;
                    success = modification.apply(effects);
                    if (success)
                        iterator.set(ItemUtils.createItemCopyItemData(player, effects, stack));
                }
                if (success) {
                    if (!silent)
                        player.sendMessage(GREEN + "One or more of your portable beacon was modified!");
                } else {
                    failedPlayers.add(player);
                }
            } else {
                ItemStack hand = inventory.getItemInMainHand();
                if (!ItemUtils.isPortableBeacon(hand)) {
                    failedPlayers.add(player);
                    continue;
                }

                BeaconEffects effects = ItemUtils.getEffects(hand);
                if (modification.apply(effects)) {
                    inventory.setItemInMainHand(ItemUtils.createItemCopyItemData(player, effects, hand));
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
        if (!failedPlayers.isEmpty())
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
                if (!RecipeManager.DISABLED_RECIPES.isEmpty()) {
                    sender.sendMessage(RED + "Disabled recipes:");
                    for (var entry : RecipeManager.DISABLED_RECIPES)
                        sender.sendMessage("  " + entry);
                }
            }
            case "create" -> {
                // TODO fix create command

                assertPermission(sender, "recipe.create");

                parser.updateUsage(RECIPE_CREATE_USAGE);
                NamespacedKey id = Objects.requireNonNull(NamespacedKey.fromString(parser.popWord(), PortableBeacons.INSTANCE));
                InventoryType type = InventoryType.valueOf(parser.popWord().toUpperCase(Locale.ENGLISH));
                ItemStack stack = parser.tryPopItemStack();

                ExpCostCalculator expCost = ExpCostCalculator.deserialize(parser.popWord());
                BeaconModification.Type action = BeaconModification.Type.parseType(parser.popWord());
                BeaconEffects virtualEffects = CommandUtils.parseEffects(sender, parser.popRemainingInput(), action.allowVirtual);
                BeaconModification modification = new BeaconModification(action, virtualEffects, false);

                if (type == InventoryType.SMITHING) {
                    sender.sendMessage(ChatColor.YELLOW + "SMITHING recipes are deprecated due to changes to Smithing Tables in 1.20!");
                }

                SimpleRecipe recipe = new SimpleRecipe(id, type, stack, null, List.of(modification), expCost, Set.of());

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
                                    .append(TextUtils.displayItem(stack)).color(BLUE).create()
                    );
                } else {
                    if (Config.debug) // show Bukkit ItemMeta info
                        sender.sendMessage(RED + "Recipe " + id + " would reject " + BLUE + stack + RED + " because it wants " +
                                YELLOW + simpleRecipe.input());
                    else
                        sender.spigot().sendMessage(
                                new ComponentBuilder("Recipe " + id + " would reject ").color(RED)
                                        .append(TextUtils.displayItem(stack)).color(BLUE)
                                        .append(" because it wants ", NONE).color(RED)
                                        .append(TextUtils.displayItem(simpleRecipe.input())).color(YELLOW).create()
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
                            .append(TextUtils.displayItem(simpleRecipe.input()))
                            .append("\n", NONE);
                    if (simpleRecipe.template() != null) {
                        builder.append("Smithing Template: ").color(net.md_5.bungee.api.ChatColor.of(new Color(0x1a1e1a)))
                                .append(TextUtils.displayItem(simpleRecipe.template()))
                                .append("\n", NONE);
                    }

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


    public void doHelp(CommandSender sender, String[] args, String label) {
        String topicName = (args.length != 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) + " " : "");
        String permPrefix;
        String desc;
        String fullCommand;
        Map<String, HelpTopic> children;

        if (args.length == 1) { // /pb help
            desc = "";
            fullCommand = null;
            children = HELP_TOPICS;
            permPrefix = COMMAND_PERM;
        } else { // /pb help <topic>
            HelpTopic topic = HELP_TOPICS.get(args[1]);
            // also check permission
            if (topic == null || (topic.node != null && !sender.hasPermission(COMMAND_PERM + topic.node)))
                throw new IllegalArgumentException("Topic " + args[1] + " not found");

            desc = topic.desc;
            fullCommand = topic.fullCommand;
            children = topic.childrenTopics;
            permPrefix = topic.node != null ? COMMAND_PERM + topic.node + "." : COMMAND_PERM;

            if (args.length == 3) { // /pb help <topic> [innerTopic]
                HelpTopic innerTopic;
                // also check permission
                if (children == null || (innerTopic = children.get(args[2])) == null ||
                        (topic.node != null && innerTopic.node != null &&
                                !sender.hasPermission(permPrefix + innerTopic.node)))
                    throw new IllegalArgumentException("Topic " + String.join(".", args) + " not found");

                desc = innerTopic.desc;
                fullCommand = innerTopic.fullCommand;
                children = null;
            }
        }

        // I miss Adventure :(
        var newLineText = new TextComponent("\n");

        var components = new ArrayList<BaseComponent>();
        var topicText = new TextComponent("/" + label + " " + topicName);
        topicText.setColor(YELLOW);
        var titleText = new TextComponent(new TextComponent("==== "), topicText, new TextComponent("help ====\n"));
        titleText.setColor(GOLD);
        components.add(titleText);

        if (fullCommand != null) {
            components.add(displayCommandSuggestion("/" + label + " " + fullCommand));
            components.add(newLineText);
        }

        if (!desc.isBlank()) {
            components.add(CommandUtils.parseHelpDescription(desc, label));
            components.add(newLineText);
        }

        if (children != null) {
            var seeMoreText = new TextComponent("Click on a topic or do /" + label + " help [subcommand] to see more.\n");
            seeMoreText.setColor(GRAY);
            components.add(seeMoreText);

            boolean firstLine = true;
            HoverEvent seeMoreHover = TextUtils.showText(new TextComponent("Click to view this topic"));
            for (var entry : children.entrySet()) {
                String id = entry.getKey();
                HelpTopic topic = entry.getValue();
                if (topic.node != null && !sender.hasPermission(permPrefix + topic.node))
                    continue;

                var component = new TextComponent();
                if (topic.fullCommand != null) {
                    component.setText("  " + topic.fullCommand);
                    component.setColor(AQUA);
                } else {
                    component.setText("  " + id);
                    component.setColor(YELLOW);
                }
                component.setHoverEvent(seeMoreHover);
                component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + label + " help " + topicName + id));

                if (!firstLine)
                    components.add(newLineText);
                firstLine = false;
                components.add(component);
            }
        }
        var endHelpText = new TextComponent("\n========");
        endHelpText.setColor(GOLD);
        components.add(endHelpText);

        sender.spigot().sendMessage(new TextComponent(components.toArray(new BaseComponent[0])));
    }

    // Help topics

    private record HelpTopic(@Nullable String node, @Nullable String fullCommand, String desc, @Nullable Map<String, HelpTopic> childrenTopics) {}

    private static HelpTopic topic(String desc) {
        return new HelpTopic(null, null, desc, null);
    }

    private static HelpTopic topic(@Nullable String node, String fullCommand, String desc) {
        return new HelpTopic(node, fullCommand, desc, null);
    }

    private static HelpTopic topic(@Nullable String node, String fullCommand, String desc, @NotNull Map<String, HelpTopic> childrenTopics) {
        return new HelpTopic(node, fullCommand, desc, childrenTopics);
    }

    private static final HelpTopic ITEM_TOPIC = topic("item", ITEM_USAGE, """
            Manipulates portable beacons in players' inventories. Run {@command help item} for more info.
            """, Map.of(
            "modifications", topic("""
                    Modifications are potion effects/enchantments in the format of type or type=level.
                    Three special values are also accepted:
                        {@color green all}: Targets all potion effects.
                        {@color green all-positive}: Targets all positive potion effects.
                        {@color green all-negative}: Targets all negative potion effects. Note that \
                    bad_omen and glowing are considered negative potion effects across the plugin.
                                       
                    Some commands may additionally accept 0 as the level.
                    For example, {@command item set @s wither=0} will remove the wither effect from your beacon.
                    """),
            "flags", topic("""
                    All item subcommands accept flags to change their behaviour.
                    Flags:
                        {@color green silently}: By default, all item subcommands will notify the target that \
                    their beacons were modified. To suppress these notifications, add the {@color green -silently} flag.
                        {@color green modify-all}:  By default, all item subcommands will only modify the \
                    portable beacons held in targets' main hands. To modify all items in targets' inventories, add the \
                    {@color green -modify-all} flag.
                    
                    For example, {@command item give-silently @a speed} will give all players a speed 1 beacon silently.
                    {@command item set-silently-modify-all @a exp-reduction=0} will remove exp-reduction from all \
                    players' beacons silently.
                    """),
            "add", topic("add", "item add <players> <modifications...>", """
                    Modifies beacons by adding the specified level onto the current level.
                    
                    For example, for a speed=2 beacon:
                        {@command item add jump_boost} will add jump_boost 1 to the beacon;
                        {@command item add speed=2} will set speed to 4.
                    """),
            "set", topic("set", "item set <players> <modifications...>", """
                    Modifies beacons by overriding the level.
                    
                    For example, for a speed=2 beacon:
                        {@command item set speed} will set speed to 1;
                        {@command item set speed=0} will remove speed.
                    """),
            "subtract", topic("subtract", "item subtract <players> <modifications...>", """
                    Modifies beacons by subtracting the specified level from the current level.
                    
                    For example, for a speed=2 beacon:
                        {@command item subtract speed} will set speed to 1;
                        {@command item subtract speed=2} will remove speed;
                        {@command item subtract jump_boost} will do nothing.
                    """),
            "filter", topic("filter", "item filter <allow/block> <conditions...>", """
                    Filters the effects on beacons so that it is compliant with the specified conditions.
                    Uses the same conditions (type[op + level]) as WorldGuard flags.
                    
                    For example, for a speed=2 jump_boost=3 beacon:
                        {@command item filter allow speed} will remove jump_boost;
                        {@command item filter allow speed<=1} will remove jump_boost and downgrade speed to 1;
                        {@command item filter block jump_boost>2} will downgrade jump_boost to 1;
                        {@command item filter block wither} will do nothing.
                    """),
            "setowner", topic("setowner", "item setowner <players> <soulboundPlayer>",
                    "Sets the soulbound owner. Accepts player names or UUIDs."),
            "update", topic("update", "item update <players>",
                    "Immediately updates the beacon item.")
    ));

    private static final HelpTopic RECIPE_TOPIC = topic("recipe", RECIPE_USAGE, """
            Manages recipes. Run {@command help recipe} for more info.
            """, Map.of(
            "create", topic("create", RECIPE_CREATE_USAGE, """
                    Creates a new recipe.
                                                        
                    {@color blue Arguments}
                    {@arg id}: The identifier of the recipe.
                    {@arg type}: The recipe type. Usually the crafting station.
                    {@arg item}: The sacrificial item. Defaults to the item you are holding.
                    {@arg itemAmount}: The amount required for the recipe.
                    {@arg expCost}: The experience cost.  Can be a number or one of these special values:
                        {@color green dynamic}: Calculate the cost dynamically, \
                    but disallow going beyond level 39. Equivalent to {@color green dynamic-max39}.
                        {@color green dynamic-max[level]}: Calculate the cost dynamically, \
                    but disallow going beyond the level specified.
                        {@color green dynamic-unrestricted}: Calculate the cost dynamically.
                    {@arg action}: The action to perform (add/subtract/set beacon effects)
                    {@arg modifications...}: The modifications to apply. \
                    See {@command help item modifications} for more info.
                    """),
            "disable", topic("disable", "recipe disable <id>", "Disables a recipe temporarily."),
            "enable", topic("enable", "recipe enable <id>", "Enables a recipe temporarily."),
            "info", topic("info", "recipe info <id>", "Shows the details of a recipe."),
            "list", topic("list", "recipe list", "Lists all recipes."),
            "testinput", topic("testinput", "recipe testinput <id> [item]", """
                    Tests whether an item is accepted as the sacrificial item of a recipe.
                                                
                    {@color blue Arguments}
                    {@arg item}: The item to test. Defaults to the item you are holding.
                    """)
    ));
    private static final Map<String, HelpTopic> HELP_TOPICS = Map.of(
            "help", topic("help", "help [subcommand]", """
                    Shows this help message.
                    Use {@command help [subcommand]} for more info on a subcommand.
                    """),
            "reload", topic("reload", "reload", "Reloads plugin configuration and recipes."),
            "saveconfig", topic("saveconfig", "saveconfig", "Saves the configuration."),
            "setritualitem", topic("setritualitem", "setritualitem [item] [amount]", """
                    Sets the ritual item.
                    
                    Note that this command {@color dark_red CANNOT} turn off the ritual, as the ritual item affects world-pickup.
                    To {@color red TURN OFF} the ritual, use {@command toggle ritual off}.
                    When the ritual item is set to air, and world-pickup is enabled,
                    the player will be able to create portable beacons by breaking existing beacons.
                    
                    {@color blue Arguments}
                    {@arg item}: Item to set as the ritual item. Accepts NBT tags. Defaults to the item in the player's hand.
                    {@arg amount}: Amount required. Defaults to 1 or the amount of item in the player's hand.
                    """),
            "updateitems", topic("updateitems", "updateitems", """
                    Request that all portable beacons be updated.
                    
                    The following will be updated:
                    - Item lore
                    - Custom model data
                    - Beacon effects (if force-downgrade is enabled)
                    
                    The following will not be updated:
                    - Item name
                    """),
            "inspect", topic("inspect", "inspect [player]", """
                    Inspects a player's held portable beacon. If player is not specified, defaults to the command sender.
                    """),
            "item", ITEM_TOPIC,
            "toggle", topic("toggle", "toggle <feature> [true/false]", """
                    Toggles a feature temporarily. To save your changes, run {@command saveconfig} afterwards.
                    
                    {@color blue Features and their corresponding config value:}
                        {@color green ritual}: ritual.enabled
                        {@color green toggle-gui}: effects.toggle.enabled
                        {@color green creation-reminder}: creation-reminder.enabled
                        {@color green world-placement}: world-interactions.placement-enabled
                        {@color green world-pickup}: world-interactions.pickup-enabled
                    """),
            "recipe", RECIPE_TOPIC
    );
}
