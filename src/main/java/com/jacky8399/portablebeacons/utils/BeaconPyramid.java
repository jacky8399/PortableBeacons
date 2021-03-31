package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.jacky8399.portablebeacons.BeaconEffects.BeaconEffectsDataType.key;
import static java.util.stream.Collectors.*;

public class BeaconPyramid {

    public final int tier;
    public final ImmutableMap<Vector, BlockData> beaconBase;

    public BeaconPyramid(int tier, Map<Vector, BlockData> beaconBase) {
        this.tier = tier;
        Preconditions.checkState(beaconBase.size() != 0, "Beacon base is empty");
        this.beaconBase = ImmutableMap.copyOf(beaconBase);
    }

    public BlockData getMajority() {
        Map<BlockData, Long> count = beaconBase.values().stream().collect(groupingBy(Function.identity(), counting()));
        return Collections.max(count.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    public static class BeaconPyramidDataType implements PersistentDataType<PersistentDataContainer, BeaconPyramid> {

        public static NamespacedKey STORAGE_KEY = key("beacon_pyramid");
        public static BeaconPyramidDataType STORAGE_TYPE = new BeaconPyramidDataType();

        public static final int DATA_VERSION = 1;

        public static final NamespacedKey TIER = key("tier"), BEACON_BASE = key("beacon_base"), DATA_VERSION_KEY = key("data_version");
        @NotNull
        @Override
        public Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @NotNull
        @Override
        public Class<BeaconPyramid> getComplexType() {
            return BeaconPyramid.class;
        }

        static final NamespacedKey MAJORITY = key("majority"), BLOCKS = key("blocks"), BLOCK = key("block"), LOCATIONS = key("locations");

        @NotNull
        @Override
        public PersistentDataContainer toPrimitive(@NotNull BeaconPyramid complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();

            container.set(TIER, INTEGER, complex.tier);
            // save space by storing only the outliers
            BlockData majority = complex.getMajority();
            container.set(MAJORITY, STRING, majority.getAsString());
            Map<BlockData, Set<Vector>> locations = complex.beaconBase.entrySet().stream()
                    .filter(entry -> !entry.getValue().matches(majority))
                    .collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toSet())));
            PersistentDataContainer[] tags = locations.entrySet().stream()
                    .map(entry -> {
                        PersistentDataContainer inner = context.newPersistentDataContainer();
                        inner.set(BLOCK, STRING, entry.getKey().getAsString());
                        int[] locationsFlattened = entry.getValue().stream()
                                .flatMapToInt(vector -> IntStream.of(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ()))
                                .toArray();
                        inner.set(LOCATIONS, INTEGER_ARRAY, locationsFlattened);
                        return inner;
                    })
                    .toArray(PersistentDataContainer[]::new);
            container.set(BLOCKS, TAG_CONTAINER_ARRAY, tags);
            container.set(DATA_VERSION_KEY, INTEGER, DATA_VERSION);

            return container;
        }

        @SuppressWarnings("ConstantConditions")
        @NotNull
        @Override
        public BeaconPyramid fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            int dataVersion = primitive.get(DATA_VERSION_KEY, INTEGER);
            if (dataVersion == 1) {
                int tier = primitive.get(TIER, INTEGER);
                BlockData majority = Bukkit.createBlockData(primitive.get(MAJORITY, STRING));
                Map<Vector, BlockData> beaconBase = new HashMap<>();
                // fill beacon with majority
                for (int currentTier = 1; currentTier <= tier; currentTier++) {
                    for (int x = -currentTier; x <= currentTier; x++) {
                        for (int z = -currentTier; z <= currentTier; z++) {
                            beaconBase.put(new BlockVector(x, -currentTier, z), majority);
                        }
                    }
                }
                // overwrite individual outliers
                PersistentDataContainer[] tags = primitive.get(BLOCKS, TAG_CONTAINER_ARRAY);
                for (PersistentDataContainer container : tags) {
                    BlockData data = Bukkit.createBlockData(container.get(BLOCK, STRING));
                    int[] locations = container.get(LOCATIONS, INTEGER_ARRAY);
                    for (int i = 0; i < locations.length; i += 3) {
                        BlockVector vector = new BlockVector(locations[i], locations[i + 1], locations[i + 2]);
                        beaconBase.put(vector, data);
                    }
                }

                return new BeaconPyramid(tier, beaconBase);
            }
            throw new UnsupportedOperationException("Data version " + dataVersion + " is unsupported");
        }
    }
}
