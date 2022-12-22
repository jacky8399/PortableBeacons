package com.jacky8399.portablebeacons;

import com.google.common.collect.*;
import com.jacky8399.portablebeacons.utils.BeaconEffectsFilter;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class BeaconEffects implements Cloneable {
    private static final int DATA_VERSION = 4;
    @Nullable
    public String customDataVersion = Config.itemCustomVersion;

    public BeaconEffects() {
        this(ImmutableSortedMap.of());
    }

    @Deprecated
    public BeaconEffects(PotionEffectType... effects) {
        this(Arrays.stream(effects).filter(Objects::nonNull)
                .collect(groupingBy(Function.identity(), collectingAndThen(counting(), Long::intValue))));
    }

    public BeaconEffects(Map<PotionEffectType, Integer> effects) {
        this.effects = ImmutableSortedMap.copyOf(effects, PotionEffectUtils.POTION_COMPARATOR);
    }

    @NotNull
    private ImmutableSortedMap<PotionEffectType, Integer> effects;
    @NotNull
    private ImmutableSet<PotionEffectType> disabledEffects = ImmutableSortedSet.of();
    public int expReductionLevel = 0;
    @Nullable
    public UUID soulboundOwner = null;
    public int soulboundLevel = 0;
    public transient boolean needsUpdate = false;

    @Override
    public BeaconEffects clone() {
        try {
            return (BeaconEffects) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    @NotNull
    public SortedMap<PotionEffectType, Integer> getEffects() {
        return effects;
    }

    @NotNull
    public SortedMap<PotionEffectType, Integer> getEnabledEffects() {
        TreeMap<PotionEffectType, Integer> enabledEffects = new TreeMap<>(effects);
        enabledEffects.keySet().removeAll(disabledEffects);
        return enabledEffects;
    }

    @NotNull
    public Set<PotionEffectType> getDisabledEffects() {
        return disabledEffects;
    }

    public void setEffects(Map<PotionEffectType, Integer> effects) {
        this.effects = ImmutableSortedMap.copyOf(effects, PotionEffectUtils.POTION_COMPARATOR);
    }

    public void setDisabledEffects(Set<PotionEffectType> disabledEffects) {
        this.disabledEffects = ImmutableSet.copyOf(disabledEffects);
    }

    public boolean hasOwner() {
        return soulboundOwner != null;
    }

    public boolean isOwner(UUID uuid) {
        return soulboundOwner == null || soulboundOwner.equals(uuid);
    }

    public boolean isOwner(OfflinePlayer player) {
        return isOwner(player.getUniqueId());
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
        setEffects(map);
    }

    public List<PotionEffect> toEffects() {
        Map<PotionEffectType, Integer> allEffects = getEffects();
        List<PotionEffect> enabledEffects = new ArrayList<>(allEffects.size() - disabledEffects.size());
        for (Map.Entry<PotionEffectType, Integer> entry : allEffects.entrySet()) {
            Config.PotionEffectInfo info = Config.getInfo(entry.getKey());
            int duration = info.getDuration();
            enabledEffects.add(new PotionEffect(entry.getKey(), duration, entry.getValue() - 1, true, !info.isHideParticles()));
        }
        return enabledEffects;
    }

    public List<String> toLore() {
        return toLore(true, true);
    }

    public List<String> toLore(boolean showEffects, boolean showEnchants) {
        var loreBuilder = new StringBuilder();

        if (showEffects)
            loreBuilder.append(effects.entrySet().stream()
                    .map(entry -> {
                        String display = PotionEffectUtils.getDisplayName(entry.getKey(), entry.getValue());
                        if (disabledEffects.contains(entry.getKey()))
                            display = ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + ChatColor.stripColor(display);
                        return display;
                    })
                    .collect(joining("\n")));

        boolean showExpReduction = showEnchants && Config.enchExpReductionEnabled && expReductionLevel != 0;
        boolean showSoulbound = showEnchants && Config.enchSoulboundEnabled && soulboundLevel != 0;

        if (showEffects && (showExpReduction || showSoulbound)) {
            loreBuilder.append('\n').append('\n');
        }

        if (showExpReduction) {
            loreBuilder.append(ItemUtils.replacePlaceholders(null, Config.enchExpReductionName,
                    new ItemUtils.ContextLevel(expReductionLevel),
                    Map.of("exp-reduction", new ItemUtils.ContextExpReduction(expReductionLevel))
            ));
        }
        if (showSoulbound) {
            loreBuilder.append(ItemUtils.replacePlaceholders(null, Config.enchSoulboundName,
                    new ItemUtils.ContextLevel(soulboundLevel),
                    Map.of("soulbound-owner", new ItemUtils.ContextUUID(soulboundOwner, "???"))
            ));
        }
        return Arrays.asList(loreBuilder.toString().split("\n"));
    }

    public double calcExpPerMinute() {
        if (Config.nerfExpLevelsPerMinute == 0)
            return 0;
        int totalEffects = effects.entrySet().stream()
                .filter(entry -> !disabledEffects.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        double expMultiplier = Config.enchExpReductionEnabled ?
                Math.max(0, 1 - expReductionLevel * Config.enchExpReductionReductionPerLevel) : 1;
        return Math.max(0, totalEffects * Config.nerfExpLevelsPerMinute * expMultiplier);
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

    public Map<String, Object> save(boolean allowVirtual) {
        // only save effects and custom enchants
        int minLevel = allowVirtual ? 0 : 1;
        var map = new LinkedHashMap<String, Object>();
        effects.forEach((potion, level) -> {
            if (level >= minLevel)
                map.put(potion.getKey().toString(), level);
        });
        if (expReductionLevel >= minLevel)
            map.put("exp-reduction", expReductionLevel);
        if (soulboundLevel >= minLevel)
            map.put("soulbound", soulboundLevel);
        return map;
    }

    public static BeaconEffects load(Map<String, Object> map, boolean allowVirtual) throws IllegalArgumentException {
        var beaconEffects = new BeaconEffects();
        int minLevel = allowVirtual ? 0 : 1;
        beaconEffects.expReductionLevel = minLevel - 1;
        beaconEffects.soulboundLevel = minLevel - 1;
        var effects = new HashMap<PotionEffectType, Integer>();
        map.forEach((key, obj) -> {
            if (!(obj instanceof Number number) || number.intValue() < minLevel)
                throw new IllegalArgumentException(obj + " is not a valid value for key " + key);
            if ("exp-reduction".equals(key)) {
                beaconEffects.expReductionLevel = number.intValue();
            } else if ("soulbound".equals(key)) {
                beaconEffects.soulboundLevel = number.intValue();
            } else {
                PotionEffectType type = PotionEffectUtils.parsePotion(key);
                if (type == null)
                    throw new IllegalArgumentException(key + " is not a valid key");
                effects.put(type, number.intValue());
            }
        });
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public static class BeaconEffectsDataType implements PersistentDataType<PersistentDataContainer, BeaconEffects> {
        private static final PortableBeacons PLUGIN = PortableBeacons.INSTANCE;

        public static NamespacedKey key(String key) {
            return new NamespacedKey(PLUGIN, key);
        }

        public static final NamespacedKey STORAGE_KEY = key("beacon_effect");
        public static final BeaconEffectsDataType STORAGE_TYPE = new BeaconEffectsDataType();

        private static final NamespacedKey
                DATA_VERSION_KEY = key("data_version"), CUSTOM_DATA_VERSION_KEY = key("custom_data_version"),
                EFFECTS = key("effects_v3"), DISABLED_EFFECTS = key("disabled_effects"),
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
            // too bad there's no string list
            // use boolean map as a poor replacement
            PersistentDataContainer effectsDisabled = context.newPersistentDataContainer();
            for (Map.Entry<PotionEffectType, Integer> entry : complex.effects.entrySet()) {
                PotionEffectType type = entry.getKey();
                Integer level = entry.getValue();
                NamespacedKey key = new NamespacedKey(NamespacedKey.BUKKIT, type.getName().toLowerCase(Locale.US));
                effects.set(key, SHORT, level.shortValue());
                // disabled
                if (complex.disabledEffects.contains(type))
                    effectsDisabled.set(key, BYTE, (byte) 1);
            }
            container.set(EFFECTS, TAG_CONTAINER, effects);
            if (effectsDisabled.getKeys().size() != 0)
                container.set(DISABLED_EFFECTS, TAG_CONTAINER, effectsDisabled);
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
            else if (dataVersion == 3 || dataVersion == 4) {
                PersistentDataContainer effects = primitive.get(EFFECTS, TAG_CONTAINER);
                ImmutableMap.Builder<PotionEffectType, Integer> effectsMap = ImmutableMap.builder();
                for (NamespacedKey key : effects.getKeys()) {
                    PotionEffectType type = PotionEffectType.getByName(key.getKey());
                    if (type == null) continue;
                    short level = effects.get(key, SHORT);
                    effectsMap.put(type, (int) level);
                }
                BeaconEffects ret = new BeaconEffects(effectsMap.build());
                // disabled effects
                if (dataVersion == 4) {
                    PersistentDataContainer disabledEffects = primitive.get(DISABLED_EFFECTS, PersistentDataType.TAG_CONTAINER);
                    if (disabledEffects != null) {
                        ImmutableSet<PotionEffectType> disabledEffectsSet = disabledEffects.getKeys().stream()
                                .map(NamespacedKey::getKey)
                                .map(PotionEffectType::getByName)
                                .filter(Objects::nonNull)
                                .collect(ImmutableSet.toImmutableSet());
                        ret.setDisabledEffects(disabledEffectsSet);
                    }
                }

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
                throw new UnsupportedOperationException("Invalid data version " + dataVersion + ", only data versions 2-4 and null are supported");
            }
        }

        //<editor-fold desc="Legacy code" defaultstate="collapsed">
        private static final NamespacedKey LEGACY_PRIMARY = key("primary_effect"), LEGACY_SECONDARY = key("secondary_effect"), EFFECTS_LEGACY_V1 = key("effects"), EFFECTS_LEGACY_V2 = key("effects_v2");

        BeaconEffects parseLegacyV1(PersistentDataContainer primitive) {
            if (primitive.has(EFFECTS_LEGACY_V1, PersistentDataType.STRING)) { // older
                BeaconEffects ret = new BeaconEffects(
                        Arrays.stream(primitive.get(EFFECTS_LEGACY_V1, PersistentDataType.STRING).split(","))
                                .map(PotionEffectType::getByName)
                                .filter(Objects::nonNull)
                                .toArray(PotionEffectType[]::new)
                );
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
                if (type == null) continue;
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
