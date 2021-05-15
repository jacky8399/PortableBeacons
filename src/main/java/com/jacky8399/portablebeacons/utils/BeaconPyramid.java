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

import java.util.*;
import java.util.function.Function;

import static com.jacky8399.portablebeacons.BeaconEffects.BeaconEffectsDataType.key;
import static java.util.stream.Collectors.*;

public class BeaconPyramid {

    public final int tier;
    public final ImmutableMap<Vector, BlockData> beaconBase;

    public BeaconPyramid(int tier, Map<Vector, BlockData> beaconBase) {
        Preconditions.checkState(tier > 0 && tier <= 4, "Tier is invalid");
        Preconditions.checkState(beaconBase.size() != 0, "Beacon base is empty");
        // Minecraft wiki: 164 for tier 4
        Preconditions.checkState(beaconBase.size() < 165, "Too many beacon base blocks!");
        this.tier = tier;
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

        public static final NamespacedKey TIER = key("tier"), DATA_VERSION_KEY = key("data_version");
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

        static final NamespacedKey MAJORITY = key("majority"), BLOCKS = key("blocks"),
                BLOCK = key("block"), LOCATIONS = key("locations"), Y_OFFSET = key("y"), LAYERS = key("layers");

        @NotNull
        @Override
        public PersistentDataContainer toPrimitive(@NotNull BeaconPyramid complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();

            container.set(TIER, BYTE, (byte) complex.tier);
            // save space by storing only the outliers
            BlockData majority = complex.getMajority();
            container.set(MAJORITY, STRING, majority.getAsString());
            // group by block data then by y offset
            Map<BlockData, Map<Integer, List<Vector>>> layeredLocations = complex.beaconBase.entrySet().stream()
                    .filter(entry -> !entry.getValue().matches(majority))
                    .collect(groupingBy(Map.Entry::getValue, groupingBy(entry -> entry.getKey().getBlockY(), mapping(Map.Entry::getKey, toList()))));
            PersistentDataContainer[] tags = layeredLocations.entrySet().stream()
                    .map(entry -> {
                        PersistentDataContainer blockContainer = context.newPersistentDataContainer();
                        blockContainer.set(BLOCK, STRING, entry.getKey().getAsString());
                        Map<Integer, List<Vector>> layers = entry.getValue();
                        PersistentDataContainer[] layerTags = layers.entrySet().stream()
                                .map(layer -> {
                                    PersistentDataContainer layerContainer = context.newPersistentDataContainer();
                                    layerContainer.set(Y_OFFSET, BYTE, layer.getKey().byteValue());
                                    // store x and z only
                                    List<Vector> pos = layer.getValue();
                                    byte[] bytes = new byte[pos.size() * 2];
                                    ListIterator<Vector> iterator = pos.listIterator();
                                    while (iterator.hasNext()) {
                                        int idx = iterator.nextIndex();
                                        Vector next = iterator.next();
                                        bytes[idx * 2] = (byte) next.getBlockX();
                                        bytes[idx * 2 + 1] = (byte) next.getBlockZ();
                                    }
                                    layerContainer.set(LOCATIONS, BYTE_ARRAY, bytes);
                                    return layerContainer;
                                })
                                .toArray(PersistentDataContainer[]::new);
                        blockContainer.set(LAYERS, TAG_CONTAINER_ARRAY, layerTags);
                        return blockContainer;
                    })
                    .toArray(PersistentDataContainer[]::new);
            if (tags.length != 0)
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
                int tier = primitive.get(TIER, BYTE);
                if (tier > 4 || tier < 1) {
                    throw new IllegalStateException(tier + " is not a valid tier");
                }
                // validation of beacon base is done when placed
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
                if (tags != null) {
                    for (PersistentDataContainer container : tags) {
                        BlockData data = Bukkit.createBlockData(container.get(BLOCK, STRING));
                        PersistentDataContainer[] layersTags = container.get(LAYERS, TAG_CONTAINER_ARRAY);
                        for (PersistentDataContainer layersTag : layersTags) {
                            int yOffset = layersTag.get(Y_OFFSET, BYTE);
                            if (yOffset >= 0) {
                                throw new IllegalStateException(yOffset + " is an invalid offset");
                            }
                            byte[] locations = layersTag.get(LOCATIONS, BYTE_ARRAY);
                            for (int i = 0; i < locations.length; i += 2) {
                                BlockVector loc = new BlockVector(locations[i], yOffset, locations[i + 1]);
                                beaconBase.put(loc, data);
                            }
                        }
                    }
                }

                return new BeaconPyramid(tier, beaconBase);
            }
            throw new UnsupportedOperationException("Data version " + dataVersion + " is unsupported");
        }
    }
}
