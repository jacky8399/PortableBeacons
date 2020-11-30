package com.jacky8399.main;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bukkit.ChatColor.GREEN;

public class BeaconEffects {

    private static final int DATA_VERSION = 2;

    public String customDataVersion = Config.itemCustomVersion;

    public static final NamespacedKey STORAGE_KEY = new NamespacedKey(PortableBeacons.INSTANCE, "beacon_effect");
    public static final BeaconEffectsDataType STORAGE_TYPE = new BeaconEffectsDataType();

    public BeaconEffects(PotionEffectType... effects) {
        this.effects = ImmutableMap.copyOf(Arrays.stream(effects).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.reducing(
                        (short)0, e -> (short)1, (s1, s2)->(short)(s1 + s2)
                ))));
    }

    public BeaconEffects(Map<PotionEffectType, Short> effects) {
        this.effects = ImmutableMap.copyOf(effects);
    }

    public final Map<PotionEffectType, Short> effects;
    public boolean needsUpdate = false;

    private static final int DURATION_CONST = 280; // 14s
    public PotionEffect[] toEffects() {
        return effects.entrySet().stream()
                .map(entry -> entry.getKey().createEffect(DURATION_CONST, entry.getValue() - 1))
                .toArray(PotionEffect[]::new);
    }

    public List<String> toLore() {
        return effects.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> stringifyEffect(entry.getKey(), entry.getValue().intValue()))
                .collect(Collectors.toList());
    }

    public double calcExpPerCycle() {
        if (Config.itemNerfsExpPercentagePerCycle <= 0)
            return 0;
        return effects.values().stream().mapToInt(Short::intValue).sum() * Config.itemNerfsExpPercentagePerCycle;
    }

    public Map<PotionEffectType, Short> getEffects() {
        return effects;
    }

    public static void loadConfig(FileConfiguration config) {
        EFFECT_TO_NAME.clear();
        config.getConfigurationSection("beacon-item.effects").getValues(false).forEach((effect, name)->{
            PotionEffectType type = CommandPortableBeacons.getType(effect); // for vanilla names
            if (type != null)
                EFFECT_TO_NAME.put(type, name.toString());
            else
                PortableBeacons.INSTANCE.getLogger().warning("Unknown potion effect type " + effect + " in configuration, skipping");
        });
    }

    private static HashMap<PotionEffectType, String> EFFECT_TO_NAME = Maps.newHashMap();

    private static String toRomanNumeral(int i) {
        switch (i) {
            case 1:
                return "";
            case 2:
                return " II";
            case 3:
                return " III";
            case 4:
                return " IV";
            case 5:
                return " V";
            case 6:
                return " VI";
            case 7:
                return " VII";
            case 8:
                return " VIII";
            case 9:
                return " IX";
            case 10:
                return " X";
            default:
                return " " + i;
        }
    }

    private static String stringifyEffect(PotionEffectType effect, int amplifier) {
        return EFFECT_TO_NAME.getOrDefault(effect, GREEN + StringUtils.capitalize(effect.getName().replace('_', ' ').toLowerCase()))
                + toRomanNumeral(amplifier);
    }

    public boolean shouldUpdate() {
        return needsUpdate || !Objects.equals(Config.itemCustomVersion, customDataVersion);
        //(Config.itemCustomVersion != null && !Config.itemCustomVersion.equals(customDataVersion));
    }

    public static class BeaconEffectsDataType implements PersistentDataType<PersistentDataContainer, BeaconEffects> {
        private static final NamespacedKey
                EFFECTS = new NamespacedKey(PortableBeacons.INSTANCE, "effects_v2"),
                DATA_VERSION_KEY = new NamespacedKey(PortableBeacons.INSTANCE, "data_version"),
                CUSTOM_DATA_VERSION_KEY = new NamespacedKey(PortableBeacons.INSTANCE, "custom_data_version");

        @Override
        public Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public Class<BeaconEffects> getComplexType() {
            return BeaconEffects.class;
        }

        @Override
        public PersistentDataContainer toPrimitive(BeaconEffects complex, PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();
            container.set(EFFECTS, PersistentDataType.STRING,
                    complex.effects.entrySet().stream().map(entry ->
                            entry.getKey().getName() + (entry.getValue() > 1 ? ":" + entry.getValue() : ""))
                            .collect(Collectors.joining(","))
            );
            container.set(DATA_VERSION_KEY, PersistentDataType.INTEGER, DATA_VERSION);
            if (complex.customDataVersion != null)
                container.set(CUSTOM_DATA_VERSION_KEY, PersistentDataType.STRING, complex.customDataVersion);
            return container;
        }

        @Override
        public BeaconEffects fromPrimitive(PersistentDataContainer primitive, PersistentDataAdapterContext context) {
            if (primitive.has(EFFECTS, PersistentDataType.STRING)) {
                String[] kvps = primitive.get(EFFECTS, PersistentDataType.STRING).split(",");
                HashMap<PotionEffectType, Short> effects = Maps.newHashMap();
                for (String kvp : kvps) {
                    String[] split = kvp.split(":");
                    PotionEffectType type;
                    Short amplifier = (short)1;
                    if (split.length == 1) {
                        type = PotionEffectType.getByName(kvp);
                    } else {
                        type = PotionEffectType.getByName(split[0]);
                        amplifier = Short.valueOf(split[1]);
                    }
                    effects.put(type, amplifier);
                }
                BeaconEffects ret = new BeaconEffects(effects);
                ret.customDataVersion = primitive.get(CUSTOM_DATA_VERSION_KEY, PersistentDataType.STRING);
                return ret;
            }
            return parseLegacy(primitive);
        }

        private static final NamespacedKey // LEGACY
                PRIMARY = new NamespacedKey(PortableBeacons.INSTANCE, "primary_effect"),
                SECONDARY = new NamespacedKey(PortableBeacons.INSTANCE, "secondary_effect"),
                EFFECTS_LEGACY = new NamespacedKey(PortableBeacons.INSTANCE, "effects");

        BeaconEffects parseLegacy(PersistentDataContainer primitive) {
            if (primitive.has(EFFECTS_LEGACY, PersistentDataType.STRING)) {
                // newer
                BeaconEffects ret = new BeaconEffects(Arrays.stream(primitive.get(EFFECTS_LEGACY, PersistentDataType.STRING).split(",")).map(PotionEffectType::getByName).toArray(PotionEffectType[]::new));
                ret.needsUpdate = true;
                ret.customDataVersion = primitive.get(CUSTOM_DATA_VERSION_KEY, PersistentDataType.STRING);
                return ret;
            } else if (primitive.has(PRIMARY, PersistentDataType.STRING)) {
                // oldest
                PotionEffectType primary = Optional.ofNullable(primitive.get(PRIMARY, PersistentDataType.STRING)).map(PotionEffectType::getByName).orElse(PotionEffectType.SPEED),
                        secondary = Optional.ofNullable(primitive.get(SECONDARY, PersistentDataType.STRING)).map(PotionEffectType::getByName).orElse(null);
                BeaconEffects ret;
                if (secondary != null) {
                    ret = new BeaconEffects(primary, secondary);
                } else {
                    ret = new BeaconEffects(primary);
                }
                ret.needsUpdate = true;
                return ret;
            }
            return null;
        }
    }

}
