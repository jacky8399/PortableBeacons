package com.jacky8399.portablebeacons;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jacky8399.portablebeacons.utils.BeaconEffectsFilter;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class BeaconEffects implements Cloneable {
    private static final int DATA_VERSION = 3;
    @Nullable
    public String customDataVersion = Config.itemCustomVersion;

    public BeaconEffects() {
        this.effects = ImmutableMap.of();
    }

    @Deprecated
    public BeaconEffects(PotionEffectType... effects) {
        this.effects = Arrays.stream(effects).filter(Objects::nonNull)
                .collect(collectingAndThen(
                        groupingBy(Function.identity(), collectingAndThen(counting(), Long::intValue)),
                        ImmutableMap::copyOf));
    }

    public BeaconEffects(Map<PotionEffectType, Integer> effects) {
        this.effects = ImmutableMap.copyOf(effects);
    }

    @NotNull
    private ImmutableMap<PotionEffectType, Integer> effects;
    public int expReductionLevel = 0;
    @Nullable
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

    @NotNull
    public ImmutableMap<PotionEffectType, Integer> getEffects() {
        return effects;
    }

    @NotNull
    public Map<PotionEffectType, Integer> getNormalizedEffects() {
        return Maps.transformValues(effects, Math::abs);
    }

    @NotNull
    public Map<PotionEffectType, Integer> getEnabledEffects() {
        return Maps.filterValues(effects, num -> num >= 0);
    }

    @NotNull
    public Set<PotionEffectType> getDisabledEffects() {
        return Maps.filterValues(effects, num -> num < 0).keySet();
    }

    public void setEffects(Map<PotionEffectType, Integer> effects) {
        this.effects = ImmutableMap.copyOf(effects);
    }

    public void filter(Set<BeaconEffectsFilter> filters, boolean whitelist) {
        HashMap<PotionEffectType, Integer> map = whitelist ? new HashMap<>() : new HashMap<>(effects);
        for (BeaconEffectsFilter filter : filters) {
            if (filter.contains(effects)) {
                if (whitelist)
                    map.put(filter.type, effects.get(filter.type));
                else
                    map.remove(filter.type);
            }
        }
        this.effects = ImmutableMap.copyOf(map);
    }

    public PotionEffect[] toEffects() {
        Map<PotionEffectType, Integer> actualEffects = getEnabledEffects();
        PotionEffect[] arr = new PotionEffect[actualEffects.size()];
        int i = 0;
        for (Map.Entry<PotionEffectType, Integer> entry : actualEffects.entrySet()) {
            if (entry.getValue() < 0) continue; // ignore disabled effects
            Config.PotionEffectInfo info = Config.getInfo(entry.getKey());
            int duration = info.getDuration();
            arr[i++] = new PotionEffect(entry.getKey(), duration, entry.getValue() - 1, true, !info.isHideParticles());
        }
        return arr;
    }

    public List<String> toLore() {
        List<String> effectsLore = effects.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(PotionEffectUtils.POTION_COMPARATOR))
                .map(entry -> {
                    String display = PotionEffectUtils.getDisplayName(entry.getKey(), Math.abs(entry.getValue()));
                    if (entry.getValue() < 0)
                        display = ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + ChatColor.stripColor(display);
                    return display;
                })
                .collect(toList());

        List<String> enchantsLore = new ArrayList<>();
        if (Config.enchExpReductionEnabled && expReductionLevel != 0) {
            enchantsLore.add(PotionEffectUtils.replacePlaceholders(null,
                    Config.enchExpReductionName, expReductionLevel));
        }
        if (Config.enchSoulboundEnabled && soulboundLevel != 0) {
            String owner = null;
            if (soulboundOwner != null) {
                owner = Bukkit.getOfflinePlayer(soulboundOwner).getName();
                if (owner == null) owner = "???"; // haven't seen player before?
            }
            enchantsLore.add(PotionEffectUtils.replacePlaceholders(null,
                    Config.enchSoulboundName, soulboundLevel, owner));
        }
        // merge
        if (enchantsLore.size() != 0) {
            effectsLore.add("");
            effectsLore.addAll(enchantsLore);
        }
        return effectsLore;
    }

    public double calcExpPerCycle() {
        if (Config.nerfExpPercentagePerCycle <= 0)
            return 0;
        double expMultiplier = Config.enchExpReductionEnabled ?
                Math.max(0, 1 - expReductionLevel * Config.enchExpReductionReductionPerLevel) : 1;
        return Math.max(0, effects.values().stream().reduce(0, Integer::sum) * Config.nerfExpPercentagePerCycle * expMultiplier);
    }

    public boolean shouldUpdate() {
        return needsUpdate || !Objects.equals(Config.itemCustomVersion, customDataVersion);
    }

    public BeaconEffects fixOpEffects() {
        BeaconEffects ret = clone();
        HashMap<PotionEffectType, Integer> newEffects = new HashMap<>(effects);
        Iterator<Map.Entry<PotionEffectType, Integer>> iterator = newEffects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PotionEffectType, Integer> entry = iterator.next();
            PotionEffectType effect = entry.getKey();
            Config.PotionEffectInfo info = Config.getInfo(effect);
            int maxAmplifier = info.getMaxAmplifier();
            if (entry.getValue() > maxAmplifier) {
                if (maxAmplifier == 0) // disallowed effect
                    iterator.remove();
                else
                    entry.setValue(maxAmplifier);
            }
        }
        // downgrade enchantments
        ret.expReductionLevel = Math.min(expReductionLevel, Config.enchExpReductionMaxLevel);
        ret.soulboundLevel = Math.min(soulboundLevel, Config.enchSoulboundMaxLevel);
        ret.setEffects(newEffects);
        return ret;
    }

    @SuppressWarnings({"deprecation", "unchecked", "ConstantConditions"})
    public static class BeaconEffectsDataType implements PersistentDataType<PersistentDataContainer, BeaconEffects> {
        private static final PortableBeacons plugin = PortableBeacons.INSTANCE;
        private static NamespacedKey key(String key) {
            return new NamespacedKey(plugin, key);
        }

        public static final NamespacedKey STORAGE_KEY = key("beacon_effect");
        public static final BeaconEffectsDataType STORAGE_TYPE = new BeaconEffectsDataType();

        private static final NamespacedKey
                DATA_VERSION_KEY = key("data_version"), CUSTOM_DATA_VERSION_KEY = key("custom_data_version"),
                EFFECTS = key("effects_v3"),
                ENCHANT_EXP_REDUCTION = key("enchant_exp_reduction_level"),
                ENCHANT_SOULBOUND = key("enchant_soulbound_level"),
                ENCHANT_SOULBOUND_OWNER = key("enchant_soulbound_owner");

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
            PersistentDataContainer effects = context.newPersistentDataContainer();
            complex.effects.forEach((type, level) -> {
                NamespacedKey key = new NamespacedKey(NamespacedKey.BUKKIT, type.getName().toLowerCase(Locale.US));
                effects.set(key, SHORT, level.shortValue());
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

        @Override
        public @NotNull BeaconEffects fromPrimitive(PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            Integer dataVersion = primitive.get(DATA_VERSION_KEY, INTEGER);
            if (dataVersion == null)
                return parseLegacyV1(primitive);
            else if (dataVersion == 3) {
                PersistentDataContainer effects = primitive.get(EFFECTS, TAG_CONTAINER);
                HashMap<PotionEffectType, Integer> effectsMap = new HashMap<>();
                for (NamespacedKey key : getKeys(effects)) {
                    PotionEffectType type = PotionEffectType.getByName(key.getKey());
                    if (type == null) continue;
                    short level = effects.get(key, SHORT);
                    effectsMap.put(type, (int) level);
                }
                BeaconEffects ret = new BeaconEffects(effectsMap);
                ret.customDataVersion = primitive.get(CUSTOM_DATA_VERSION_KEY, STRING);
                // enchants
                Integer enchantExpReduction = primitive.get(ENCHANT_EXP_REDUCTION, INTEGER);
                if (enchantExpReduction != null)
                    ret.expReductionLevel = enchantExpReduction;
                Integer enchantSoulbound = primitive.get(ENCHANT_SOULBOUND, INTEGER);
                if (enchantSoulbound != null)
                    ret.soulboundLevel = enchantSoulbound;
                long[] bits = primitive.get(ENCHANT_SOULBOUND_OWNER, LONG_ARRAY);
                if (bits != null) {
                    // bypass UUID.fromString
                    ret.soulboundOwner = new UUID(bits[0], bits[1]);
                }
                return ret;
            } else if (dataVersion == 2) {
                return parseLegacyV2(primitive);
            } else {
                throw new UnsupportedOperationException("Invalid data version " + dataVersion + ", only data versions null, 2 and 3 are supported");
            }
        }

        //<editor-fold desc="Utilities" defaultstate="collapsed">
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
                    }).collect(toSet());
                } catch (ReflectiveOperationException e) {
                    throw new Error("Failed to find keys for NBT tag!", e);
                }
            }
        }
        //</editor-fold>

        //<editor-fold desc="Legacy code" defaultstate="collapsed">
        private static final NamespacedKey LEGACY_PRIMARY = key("primary_effect"), LEGACY_SECONDARY = key("secondary_effect"), EFFECTS_LEGACY_V1 = key("effects"), EFFECTS_LEGACY_V2 = key("effects_v2");
        BeaconEffects parseLegacyV1(PersistentDataContainer primitive) {
            if (primitive.has(EFFECTS_LEGACY_V1, PersistentDataType.STRING)) { // older
                BeaconEffects ret = new BeaconEffects(Arrays.stream(primitive.get(EFFECTS_LEGACY_V1, PersistentDataType.STRING).split(",")).map(PotionEffectType::getByName).toArray(PotionEffectType[]::new));
                ret.needsUpdate = true;
                ret.customDataVersion = primitive.get(CUSTOM_DATA_VERSION_KEY, PersistentDataType.STRING);
                return ret;
            } else if (primitive.has(LEGACY_PRIMARY, PersistentDataType.STRING)) { // oldest
                PotionEffectType primary = Optional.ofNullable(primitive.get(LEGACY_PRIMARY, PersistentDataType.STRING)).map(PotionEffectType::getByName).orElse(PotionEffectType.SPEED),
                        secondary = Optional.ofNullable(primitive.get(LEGACY_SECONDARY, PersistentDataType.STRING)).map(PotionEffectType::getByName).orElse(null);
                BeaconEffects ret = new BeaconEffects(primary, secondary);
                ret.needsUpdate = true;
                return ret;
            }
            return null;
        }

        BeaconEffects parseLegacyV2(PersistentDataContainer primitive) {
            String effectsCombined = primitive.get(EFFECTS_LEGACY_V2, PersistentDataType.STRING);
            if (effectsCombined.isEmpty()) // empty string fix
                return new BeaconEffects();
            String[] kvps = effectsCombined.split(",");
            HashMap<PotionEffectType, Integer> effects = Maps.newHashMap();
            for (String kvp : kvps) {
                String[] split = kvp.split(":");
                PotionEffectType type;
                int amplifier = 1;
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
        //</editor-fold>
    }

}
