package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.Config;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.md_5.bungee.api.ChatColor.RED;
import static net.md_5.bungee.api.ChatColor.YELLOW;

public class CommandUtils {

    public static BaseComponent showItem(ItemStack stack) {
        var material = stack.getType();
        var materialKey = material.getKey().toString();
        var meta = stack.getItemMeta();
        var itemTag = stack.hasItemMeta() ?
                ItemTag.ofNbt(meta.getAsString()) :
                null;
        var hoverContent = new Item(materialKey, stack.getAmount(), itemTag);
        var hover = new HoverEvent(HoverEvent.Action.SHOW_ITEM, hoverContent);
        BaseComponent component;
        if (meta.hasDisplayName()) {
            component = new TextComponent(meta.getDisplayName());
        } else {
            var langKey = (material.isBlock() ? "block." : "item.") + materialKey.replace(':', '.');
            component = new TranslatableComponent(langKey);
        }
        component.setHoverEvent(hover);
        component.setColor(ChatColor.YELLOW);
        return component;
    }

    // Potion effect type autocomplete
    private static final List<String> VALID_MODIFICATIONS = Stream.concat(
            PotionEffectUtils.getValidPotionNames().stream(),
            Stream.of("exp-reduction", "soulbound", "all")
    ).toList();
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
        } else if (input.equalsIgnoreCase("all")) {
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
        return VALID_MODIFICATIONS.stream();
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
                if (s.indexOf('=') > -1 || s.indexOf('*') > -1) {
                    String splitChar = s.indexOf('=') > -1 ? "=" : "\\*";
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
                PotionEffectType type = PotionEffectUtils.parsePotion(potionName);
                if (type == null)
                    throw new IllegalArgumentException(s + " is not a valid potion effect or enchantment");
                Preconditions.checkArgument(level >= minLevel, "Level is negative");
                effects.put(type, level);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(RED + String.format("Skipped '%s' as it is not a valid potion effect (%s)", s, e.getMessage()));
            }
        }
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }
}
