package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.Config;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CommandUtils {

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
}
