package com.jacky8399.portablebeacons;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.jacky8399.portablebeacons.utils.BeaconEffectsFilter;
import com.jacky8399.portablebeacons.utils.PotionEffectUtils;
import com.jacky8399.portablebeacons.utils.TextUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public BeaconEffects(BeaconEffects other) {
        this.effects = other.effects;
        this.disabledEffects = other.disabledEffects;
        this.expReductionLevel = other.expReductionLevel;
        this.soulboundOwner = other.soulboundOwner;
        this.soulboundLevel = other.soulboundLevel;
        this.beaconatorLevel = other.beaconatorLevel;
        this.beaconatorSelectedLevel = other.beaconatorSelectedLevel;
        this.customDataVersion = other.customDataVersion;
        this.needsUpdate = other.needsUpdate;
    }

    @NotNull
    private ImmutableSortedMap<PotionEffectType, Integer> effects;
    @NotNull
    private Set<PotionEffectType> disabledEffects = Set.of();
    public int expReductionLevel = 0;
    @Nullable
    public UUID soulboundOwner = null;
    public int soulboundLevel = 0;

    public int beaconatorLevel = 0;
    /**
     * The beaconator level the player chooses to use
     */
    public int beaconatorSelectedLevel = 0;
    @Nullable
    public String beaconatorMode = null;

    public boolean needsUpdate = false;

    @Override
    @Deprecated // use copy constructor
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
        this.disabledEffects = Set.copyOf(disabledEffects);
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

    public void filter(Collection<? extends BeaconEffectsFilter> filters, boolean whitelist) {
        Map<PotionEffectType, Integer> map = whitelist ? new HashMap<>() : new HashMap<>(effects);
        for (BeaconEffectsFilter filter : filters) {
            if (filter.contains(effects)) {
                if (whitelist)
                    map.put(filter.type(), effects.get(filter.type()));
                else
                    map.remove(filter.type());
            }
        }
        setEffects(map);
    }

    public void filter(@Nullable Collection<? extends BeaconEffectsFilter> allowed,
                       @Nullable Collection<? extends BeaconEffectsFilter> blocked) {
        Map<PotionEffectType, Integer> map = allowed != null ? new HashMap<>() : new HashMap<>(effects);
        if (allowed != null) {
            for (BeaconEffectsFilter filter : allowed) {
                if (filter.contains(effects)) {
                    map.put(filter.type(), effects.get(filter.type()));
                }
            }
        }
        if (blocked != null) {
            for (BeaconEffectsFilter filter : blocked) {
                if (filter.contains(map)) {
                    map.remove(filter.type());
                }
            }
        }
        setEffects(map);
    }

    public List<PotionEffect> toEffects() {
        // don't need the map to be sorted, filter ourselves
        Map<PotionEffectType, Integer> allEffects = getEffects();
        int capacity = allEffects.size();
        List<PotionEffect> enabledEffects = new ArrayList<>(capacity);
        for (var entry : allEffects.entrySet()) {
            PotionEffectType effect = entry.getKey();
            if (disabledEffects.contains(effect)) continue;
            Config.PotionEffectInfo info = Config.getInfo(effect);
            int duration = info.getDuration();
            enabledEffects.add(new PotionEffect(effect, duration, entry.getValue() - 1, true, !info.isHideParticles()));
        }
        return enabledEffects;
    }

    public List<BaseComponent> toLore(boolean showEffects, boolean showEnchants) {
        List<BaseComponent> lines = new ArrayList<>();

        boolean hasExpReduction = showEnchants && Config.enchExpReductionEnabled && expReductionLevel != 0;
        boolean hasSoulbound = showEnchants && Config.enchSoulboundEnabled && soulboundLevel != 0;
        boolean hasBeaconator = showEnchants && Config.enchBeaconatorEnabled && beaconatorLevel != 0;
        boolean hasEnchants = hasExpReduction || hasSoulbound || hasBeaconator;

        if (showEffects) {
            for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
                BaseComponent display = PotionEffectUtils.getDisplayName(entry.getKey(), entry.getValue());
                display.setItalic(false);
                if (disabledEffects.contains(entry.getKey())) {
                    display.setColor(ChatColor.GRAY);
                    display.setStrikethrough(true);
                }
                lines.add(display);
            }
        }

        if (hasEnchants) {
            lines.add(new TextComponent());
        }

        if (hasExpReduction) {
            addLegacyLore(lines, TextUtils.replacePlaceholders(Config.enchExpReductionName,
                    new TextUtils.ContextLevel(expReductionLevel),
                    Map.of("exp-reduction", new TextUtils.ContextExpReduction(expReductionLevel))
            ));
        }
        if (hasSoulbound) {
            addLegacyLore(lines, TextUtils.replacePlaceholders(Config.enchSoulboundName,
                    new TextUtils.ContextLevel(soulboundLevel),
                    Map.of("soulbound-owner", new TextUtils.ContextUUID(soulboundOwner, "???"))
            ));
        }
        if (hasBeaconator) {
            addLegacyLore(lines, TextUtils.replacePlaceholders(Config.enchBeaconatorName,
                    new TextUtils.ContextLevel(beaconatorLevel),
                    Map.of()
            ));
        }
        return lines;
    }

    private static void addLegacyLore(List<BaseComponent> lines, String legacyString) {
        for (String line : legacyString.split("\n")) {
            TextComponent text = new TextComponent(TextComponent.fromLegacyText(line));
            text.setItalic(false);
            lines.add(text);
        }
    }

    public double calcExpPerMinute(Player owner) {
        if (Config.nerfExpLevelsPerMinute == 0)
            return 0;
        double cost = 0;
        int totalEffects = 0;
        for (var entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            if (disabledEffects.contains(type))
                continue;
            int level = entry.getValue();

            Config.PotionEffectInfo info = Config.getInfo(type);
            if (info.expCostOverride() != null) {
                cost += info.getExpCostCalculator(level).getCost(owner, this);
            } else {
                totalEffects += level;
            }
        }
        double expMultiplier = Config.enchExpReductionEnabled ?
                Math.max(0, 1 - expReductionLevel * Config.enchExpReductionReductionPerLevel) : 1;
        return Math.max(0, (cost + totalEffects * Config.nerfExpLevelsPerMinute) * expMultiplier);
    }

    public List<BaseComponent> getExpCostBreakdown(Player owner) {
        if (Config.nerfExpLevelsPerMinute == 0)
            return List.of();
        var list = new ArrayList<BaseComponent>();
        int totalEffects = 0;
        int totalEffectLevels = 0;
        for (var entry : effects.entrySet()) {
            PotionEffectType type = entry.getKey();
            int level = entry.getValue();
            boolean disabled = disabledEffects.contains(type);
            Config.PotionEffectInfo info = Config.getInfo(type);
            if (info.expCostOverride() == null) {
                if (!disabled) {
                    totalEffects++;
                    totalEffectLevels += level;
                }
                continue;
            }
            TextComponent display = new TextComponent(
                    PotionEffectUtils.getDisplayName(type, level),
                    new TextComponent(": " + TextUtils.TWO_DP.format(info.getExpCostCalculator(level).getCost(owner, this)) + " levels/min")
            );
            if (disabled) {
                display.setStrikethrough(true);
                display.setColor(ChatColor.GRAY);
            }
            list.add(display);
        }
        list.add(new TextComponent(totalEffects + " effects: " +
                TextUtils.TWO_DP.format(Config.nerfExpLevelsPerMinute * totalEffectLevels) + " levels/min"));

        if (expReductionLevel != 0 && Config.enchExpReductionEnabled) {
            double expMultiplier = Math.max(0, 1 - expReductionLevel * Config.enchExpReductionReductionPerLevel);
            list.add(new TextComponent(TextUtils.formatEnchantment(Config.enchExpReductionName, expReductionLevel) +
                    ": " + (int) (expMultiplier * 100) + "%"));
        }

        return list;
    }

    public boolean shouldUpdate() {
        return needsUpdate || !Objects.equals(Config.itemCustomVersion, customDataVersion);
    }

    public void validateEffects() {
        HashMap<PotionEffectType, Integer> newEffects = new LinkedHashMap<>(effects);
        HashSet<PotionEffectType> newDisabledEffects = null;

        for (var iterator = newEffects.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            PotionEffectType effect = entry.getKey();
            Config.PotionEffectInfo info = Config.getInfo(effect);
            int maxAmplifier = info.getMaxAmplifier();
            if (maxAmplifier == 0) { // disallowed effect
                iterator.remove();
                if (newDisabledEffects == null)
                    newDisabledEffects = new HashSet<>(disabledEffects);
                newDisabledEffects.remove(effect);
            } else if (entry.getValue() > maxAmplifier) {
                entry.setValue(maxAmplifier);
            }
        }
        setEffects(newEffects);
        if (newDisabledEffects != null)
            setDisabledEffects(newDisabledEffects);
        // downgrade enchantments
        expReductionLevel = Math.min(expReductionLevel, Config.enchExpReductionMaxLevel);
        soulboundLevel = Math.min(soulboundLevel, Config.enchSoulboundMaxLevel);
        beaconatorLevel = Math.max(beaconatorLevel, Config.enchBeaconatorLevels.size());
        beaconatorSelectedLevel = Math.min(beaconatorSelectedLevel, beaconatorLevel);
    }

    // Serialization

    public Map<String, Object> save(boolean allowVirtual) {
        // only save effects and custom enchants
        int minLevel = allowVirtual ? 0 : 1;
        var map = new LinkedHashMap<String, Object>();
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            PotionEffectType potion = entry.getKey();
            Integer level = entry.getValue();
            if (level >= minLevel)
                map.put(potion.getKey().toString(), level);
        }
        if (expReductionLevel >= minLevel)
            map.put("exp-reduction", expReductionLevel);
        if (soulboundLevel >= minLevel)
            map.put("soulbound", soulboundLevel);
        if (beaconatorLevel >= minLevel)
            map.put("beaconator", beaconatorLevel);
        return map;
    }

    public static BeaconEffects load(Map<String, ?> map, boolean allowVirtual) throws IllegalArgumentException {
        var beaconEffects = new BeaconEffects();
        int minLevel = allowVirtual ? 0 : 1;
        beaconEffects.expReductionLevel = minLevel - 1;
        beaconEffects.soulboundLevel = minLevel - 1;
        beaconEffects.beaconatorLevel = minLevel - 1;
        var effects = new HashMap<PotionEffectType, Integer>();
        map.forEach((key, obj) -> {
            if (!(obj instanceof Number number) || number.intValue() < minLevel)
                throw new IllegalArgumentException(obj + " is not a valid value for key " + key);
            String potionName = key.toLowerCase(Locale.ENGLISH);
            int level = number.intValue();
            switch (potionName) {
                case "exp-reduction" -> beaconEffects.expReductionLevel = level;
                case "soulbound" -> beaconEffects.soulboundLevel = level;
                case "beaconator" -> beaconEffects.beaconatorLevel = level;
                case "all" -> {
                    for (var effect : PotionEffectType.values()) {
                        effects.put(effect, level);
                    }
                }
                case "all-positive" -> {
                    for (var effect : PotionEffectType.values()) {
                        if (!PotionEffectUtils.isNegative(effect)) {
                            effects.put(effect, level);
                        }
                    }
                }
                case "all-negative" -> {
                    for (var effect : PotionEffectType.values()) {
                        if (PotionEffectUtils.isNegative(effect)) {
                            effects.put(effect, level);
                        }
                    }
                }
                default -> {
                    PotionEffectType type = PotionEffectUtils.parsePotion(potionName);
                    if (type == null)
                        throw new IllegalArgumentException(potionName + " is not a valid potion effect or enchantment");
                    effects.put(type, level);
                }
            }
        });
        beaconEffects.setEffects(effects);
        return beaconEffects;
    }

    @SuppressWarnings({"ConstantConditions"})
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
                ENCHANT_SOULBOUND_OWNER = key("enchant_soulbound_owner"),
                ENCHANT_BEACONATOR = key("enchant_beaconator"),
                ENCHANT_BEACONATOR_SELECTED = key("enchant_beaconator_selected"),
                ENCHANT_BEACONATOR_MODE = key("enchant_beaconator_mode");


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
                NamespacedKey key = type.getKey();
                effects.set(key, SHORT, level.shortValue());
                // disabled
                if (complex.disabledEffects.contains(type))
                    effectsDisabled.set(key, BYTE, (byte) 1);
            }
            container.set(EFFECTS, TAG_CONTAINER, effects);
            if (!effectsDisabled.getKeys().isEmpty())
                container.set(DISABLED_EFFECTS, TAG_CONTAINER, effectsDisabled);
            // enchants
            if (complex.expReductionLevel != 0)
                container.set(ENCHANT_EXP_REDUCTION, INTEGER, complex.expReductionLevel);
            if (complex.soulboundLevel != 0)
                container.set(ENCHANT_SOULBOUND, INTEGER, complex.soulboundLevel);
            if (complex.soulboundOwner != null)
                container.set(ENCHANT_SOULBOUND_OWNER, LONG_ARRAY,
                        new long[]{complex.soulboundOwner.getMostSignificantBits(), complex.soulboundOwner.getLeastSignificantBits()});
            if (complex.beaconatorLevel != 0) {
                container.set(ENCHANT_BEACONATOR, INTEGER, complex.beaconatorLevel);
                container.set(ENCHANT_BEACONATOR_SELECTED, INTEGER, complex.beaconatorSelectedLevel);
                if (complex.beaconatorMode != null)
                    container.set(ENCHANT_BEACONATOR_MODE, STRING, complex.beaconatorMode);
            }

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
                    PotionEffectType type = PotionEffectUtils.parsePotion(key);
                    if (type == null) continue;
                    short level = effects.get(key, SHORT);
                    effectsMap.put(type, (int) level);
                }
                BeaconEffects ret = new BeaconEffects(effectsMap.build());
                // disabled effects
                if (dataVersion == 4) {
                    PersistentDataContainer disabledEffects = primitive.get(DISABLED_EFFECTS, PersistentDataType.TAG_CONTAINER);
                    if (disabledEffects != null) {
                        var disabledEffectsSet = disabledEffects.getKeys().stream()
                                .map(PotionEffectUtils::parsePotion)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toUnmodifiableSet());
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
                Integer enchantBeaconator = primitive.get(ENCHANT_BEACONATOR, INTEGER);
                if (enchantBeaconator != null) {
                    ret.beaconatorLevel = enchantBeaconator;
                    ret.beaconatorSelectedLevel = primitive.get(ENCHANT_BEACONATOR_SELECTED, INTEGER);
                    ret.beaconatorMode = primitive.get(ENCHANT_BEACONATOR_MODE, STRING);
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
