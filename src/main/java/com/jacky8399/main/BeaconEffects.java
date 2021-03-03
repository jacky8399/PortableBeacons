package com.jacky8399.main;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.StringUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BeaconEffects implements Cloneable {

    private static final int DATA_VERSION = 3;

    public String customDataVersion = Config.itemCustomVersion;

    public static final NamespacedKey STORAGE_KEY = new NamespacedKey(PortableBeacons.INSTANCE, "beacon_effect");
    public static final BeaconEffectsDataType STORAGE_TYPE = new BeaconEffectsDataType();

    public BeaconEffects() {
        this.effects = ImmutableMap.of();
    }

    @Deprecated
    public BeaconEffects(PotionEffectType... effects) {
        this.effects = ImmutableMap.copyOf(
                Arrays.stream(effects)
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.reducing(
                                (short)0, e -> (short)1, (s1, s2)->(short)(s1 + s2)
                        )))
        );
    }

    public BeaconEffects(Map<PotionEffectType, Short> effects) {
        this.effects = ImmutableMap.copyOf(effects);
    }

    private Map<PotionEffectType, Short> effects;
    public int expReductionLevel = 0;
    public UUID soulboundOwner = null;
    public int soulboundLevel = 0;
    public boolean needsUpdate = false;

    @Override
    public BeaconEffects clone() {
        try {
            return (BeaconEffects) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    public void setEffects(Map<PotionEffectType, Short> effects) {
        this.effects = ImmutableMap.copyOf(effects);
    }

    public PotionEffect[] toEffects() {
        @SuppressWarnings("ConstantConditions")
        int defaultDuration = Config.effectsDefault.durationInTicks;
        PotionEffect[] arr = new PotionEffect[effects.size()];
        int i = 0;
        for (Map.Entry<PotionEffectType, Short> entry : effects.entrySet()) {
            Config.PotionEffectInfo info = Config.effects.get(entry.getKey());
            int duration = defaultDuration;
            if (info != null && info.durationInTicks != null) {
                duration = info.durationInTicks;
            }
            arr[i++] = new PotionEffect(entry.getKey(), duration, entry.getValue() - 1, true, info != null && info.isHideParticles());
        }
        return arr;
//        return effects.entrySet().stream()
//                .map(entry -> {
//                    Config.PotionEffectInfo info = Config.effects.get(entry.getKey());
//                    int duration = defaultDuration;
//                    if (info != null && info.durationInTicks != null) {
//                        duration = info.durationInTicks;
//                    }
//                    return new PotionEffect(entry.getKey(), duration, entry.getValue() - 1, true, info != null && info.isHideParticles());
//                })
//                .toArray(PotionEffect[]::new);
    }

    public void applyEffects(LivingEntity entity) {
        for (PotionEffect effect : toEffects()) {
            entity.addPotionEffect(effect);
        }
    }

    public List<String> toLore() {
        List<String> lore = effects.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .map(entry -> stringifyEffect(entry.getKey(), entry.getValue().intValue()))
                .collect(Collectors.toList());

        if (Config.customEnchantExpReductionEnabled && expReductionLevel != 0) {
            lore.add(Config.customEnchantExpReductionName + toRomanNumeral(expReductionLevel));
        }
        if (Config.customEnchantSoulboundEnabled && soulboundLevel != 0) {
            lore.add(Config.customEnchantSoulboundName + toRomanNumeral(soulboundLevel));
        }

        return lore;
    }

    public double calcExpPerCycle() {
        if (Config.itemNerfsExpPercentagePerCycle <= 0)
            return 0;
        double expMultiplier = Config.customEnchantExpReductionEnabled ?
                Math.max(0, 1 - expReductionLevel * Config.customEnchantExpReductionReductionPerLevel) :
                1;
        return Math.max(0, effects.values().stream().mapToInt(Short::intValue).sum() * Config.itemNerfsExpPercentagePerCycle * expMultiplier);
    }

    public Map<PotionEffectType, Short> getEffects() {
        return effects;
    }

    public static void loadConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("beacon-item.effects");
        if (section != null) {
            section.getValues(false).forEach((effect, name) -> {
                try {
                    PotionEffectType type = CommandPortableBeacons.parseType(effect); // for vanilla names
                    String newName = Config.translateColor((String) name);
                    // override PotionEffectInfo
                    Config.PotionEffectInfo info = Config.effects.get(type);
                    Config.PotionEffectInfo newInfo = new Config.PotionEffectInfo(newName, info != null ? info.durationInTicks : null, info != null ? info.maxAmplifier : null, info != null ? info.hideParticles : null);
                    Config.effects.put(type, newInfo);
                    config.set("effects." + effect + ".name", name);
                } catch (IllegalArgumentException ignored) {}
            });
            // delete section
            config.set("beacon-item.effects", null);
            PortableBeacons.INSTANCE.logger.info("Old config (beacon-item.effects) has been migrated automatically. Use '/pb saveconfig' to save to file.");
        }
    }

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
        Config.PotionEffectInfo info = Config.effects.get(effect);
        if (info != null && info.displayName != null)
            return info.displayName + toRomanNumeral(amplifier);
        else
            return ChatColor.GREEN + StringUtils.capitalize(effect.getName().replace('_', ' ').toLowerCase(Locale.ROOT)) + toRomanNumeral(amplifier);
    }

    public boolean shouldUpdate() {
        return needsUpdate || !Objects.equals(Config.itemCustomVersion, customDataVersion);
    }

    public BeaconEffects fixOpEffects() {
        BeaconEffects ret = clone();
        int defaultMax = Config.effectsDefault.maxAmplifier;
        HashMap<PotionEffectType, Short> newEffects = new HashMap<>(effects);
        Iterator<Map.Entry<PotionEffectType, Short>> iterator = newEffects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PotionEffectType, Short> entry = iterator.next();
            PotionEffectType effect = entry.getKey();
            Config.PotionEffectInfo info = Config.effects.get(effect);
            int maxAmplifier = info != null ? info.getMaxAmplifier() : defaultMax;
            if (entry.getValue() > maxAmplifier) {
                if (maxAmplifier == 0) // disallowed effect
                    iterator.remove();
                else
                    entry.setValue((short) maxAmplifier);
            }
        }
        // downgrade enchantments
        ret.expReductionLevel = Math.min(expReductionLevel, Config.customEnchantExpReductionMaxLevel);
        ret.soulboundLevel = Math.min(soulboundLevel, Config.customEnchantSoulboundMaxLevel);
        ret.setEffects(newEffects);
        return ret;
    }

    public static class BeaconEffectsDataType implements PersistentDataType<PersistentDataContainer, BeaconEffects> {
        private static final PortableBeacons plugin = PortableBeacons.INSTANCE;
        private static final NamespacedKey
                DATA_VERSION_KEY = new NamespacedKey(plugin, "data_version"),
                CUSTOM_DATA_VERSION_KEY = new NamespacedKey(plugin, "custom_data_version"),
                EFFECTS = new NamespacedKey(plugin, "effects_v3"),
                ENCHANT_EXP_REDUCTION = new NamespacedKey(plugin, "enchant_exp_reduction_level"),
                ENCHANT_SOULBOUND = new NamespacedKey(plugin, "enchant_soulbound_level"),
                ENCHANT_SOULBOUND_OWNER = new NamespacedKey(plugin, "enchant_soulbound_owner");

        @Override
        public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public @NotNull Class<BeaconEffects> getComplexType() {
            return BeaconEffects.class;
        }

        @Override
        public @NotNull PersistentDataContainer toPrimitive(BeaconEffects complex, PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();
//            container.set(EFFECTS_LEGACY_V2, PersistentDataType.STRING,
//                    complex.effects.entrySet().stream().map(entry ->
//                            entry.getKey().getName() + (entry.getValue() > 1 ? ":" + entry.getValue() : ""))
//                            .collect(Collectors.joining(","))
//            );
            PersistentDataContainer effects = context.newPersistentDataContainer();
            complex.effects.forEach((type, level) -> {
                @SuppressWarnings("deprecation")
                NamespacedKey key = new NamespacedKey(NamespacedKey.BUKKIT, type.getName().toLowerCase(Locale.US));
                effects.set(key, SHORT, level);
            });
            container.set(EFFECTS, TAG_CONTAINER, effects);
            // enchants
            if (complex.expReductionLevel != 0)
                container.set(ENCHANT_EXP_REDUCTION, INTEGER, complex.expReductionLevel);
            if (complex.soulboundLevel != 0)
                container.set(ENCHANT_SOULBOUND, INTEGER, complex.soulboundLevel);
            if (complex.soulboundOwner != null)
                container.set(ENCHANT_SOULBOUND_OWNER, LONG_ARRAY,
                        new long[]{complex.soulboundOwner.getMostSignificantBits(), complex.soulboundOwner.getLeastSignificantBits()});

            container.set(DATA_VERSION_KEY, INTEGER, DATA_VERSION);
            if (complex.customDataVersion != null)
                container.set(CUSTOM_DATA_VERSION_KEY, STRING, complex.customDataVersion);
            return container;
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public @NotNull BeaconEffects fromPrimitive(PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            if (primitive.has(EFFECTS, TAG_CONTAINER)) {
                PersistentDataContainer effects = primitive.get(EFFECTS, TAG_CONTAINER);
                HashMap<PotionEffectType, Short> effectsMap = new HashMap<>();
                for (NamespacedKey key : getKeys(effects)) {
                    PotionEffectType type = PotionEffectType.getByName(key.getKey());
                    if (type == null) {
                        PortableBeacons.INSTANCE.logger.warning("Found invalid potion effect type " + key.getKey() + ", skipping");
                        continue;
                    }
                    short level = effects.get(key, SHORT);
                    effectsMap.put(type, level);
                }
                BeaconEffects ret = new BeaconEffects(effectsMap);
                if (primitive.has(ENCHANT_EXP_REDUCTION, INTEGER)) {
                    ret.expReductionLevel = primitive.get(ENCHANT_EXP_REDUCTION, INTEGER);
                }
                if (primitive.has(ENCHANT_SOULBOUND, INTEGER)) {
                    ret.soulboundLevel = primitive.get(ENCHANT_SOULBOUND, INTEGER);
                    if (primitive.has(ENCHANT_SOULBOUND_OWNER, LONG_ARRAY)) {
                        long[] bits = primitive.get(ENCHANT_SOULBOUND_OWNER, LONG_ARRAY);
                        // bypass UUID.fromString
                        ret.soulboundOwner = new UUID(bits[0], bits[1]);
                    }
                }
                return ret;
            } else if (primitive.has(EFFECTS_LEGACY_V2, STRING) && primitive.get(DATA_VERSION_KEY, INTEGER) == 2) {
                return parseLegacyV2(primitive);
            }
            return parseLegacyV1(primitive);
        }

        private static boolean GET_KEYS = false;
        static {
            try {
                GET_KEYS = PersistentDataContainer.class.getMethod("getKeys") != null;
            } catch (NoSuchMethodException ignored) {}
        }
        private static Set<NamespacedKey> getKeys(PersistentDataContainer container) {
            if (GET_KEYS) {
                return container.getKeys();
            } else {
                try {
                    Class<? extends PersistentDataContainer> clazz = container.getClass();
                    Field internal = clazz.getDeclaredField("customDataTags");
                    internal.setAccessible(true);
                    Set<String> keys = ((Map<String, ?>) internal.get(container)).keySet();
                    return keys.stream().map(key -> {
                        String[] keyData = key.split(":", 2);
                        return new NamespacedKey(keyData[0], keyData[1]);
                    }).collect(Collectors.toSet());
                } catch (ReflectiveOperationException e) {
                    throw new Error("Failed to find keys for NBT tag!", e);
                }
            }
        }

        private static final NamespacedKey // LEGACY V1
                PRIMARY = new NamespacedKey(PortableBeacons.INSTANCE, "primary_effect"),
                SECONDARY = new NamespacedKey(PortableBeacons.INSTANCE, "secondary_effect"),
                EFFECTS_LEGACY_V1 = new NamespacedKey(PortableBeacons.INSTANCE, "effects"),
                EFFECTS_LEGACY_V2 = new NamespacedKey(PortableBeacons.INSTANCE, "effects_v2");

        @SuppressWarnings("derepcation")
        BeaconEffects parseLegacyV1(PersistentDataContainer primitive) {
            if (primitive.has(EFFECTS_LEGACY_V1, PersistentDataType.STRING)) {
                // newer
                BeaconEffects ret = new BeaconEffects(Arrays.stream(primitive.get(EFFECTS_LEGACY_V1, PersistentDataType.STRING).split(",")).map(PotionEffectType::getByName).toArray(PotionEffectType[]::new));
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

        @SuppressWarnings("ConstantConditions")
        BeaconEffects parseLegacyV2(PersistentDataContainer primitive) {
            String effectsCombined = primitive.get(EFFECTS_LEGACY_V2, PersistentDataType.STRING);
            if (effectsCombined.isEmpty()) // empty string fix
                return new BeaconEffects();
            String[] kvps = effectsCombined.split(",");
            HashMap<PotionEffectType, Short> effects = Maps.newHashMap();
            for (String kvp : kvps) {
                String[] split = kvp.split(":");
                PotionEffectType type;
                short amplifier = (short)1;
                if (split.length == 1) {
                    type = PotionEffectType.getByName(kvp);
                } else {
                    type = PotionEffectType.getByName(split[0]);
                    amplifier = Short.parseShort(split[1]);
                }
                effects.put(type, amplifier);
            }
            BeaconEffects ret = new BeaconEffects(effects);
            ret.needsUpdate = true;
            ret.customDataVersion = primitive.get(CUSTOM_DATA_VERSION_KEY, PersistentDataType.STRING);
            return ret;
        }
    }

}
