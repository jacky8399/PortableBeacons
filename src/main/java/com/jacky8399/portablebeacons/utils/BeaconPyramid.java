package com.jacky8399.portablebeacons.utils;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
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

public final class BeaconPyramid {

    /**
     * The tier of the stored beacon pyramid
     */
    public final int tier;

    /**
     * A block that makes up the beacon base
     * @param data The block data
     * @param relativeX The x coordinate relative to the beacon block
     * @param relativeY The y coordinate relative to the beacon block
     * @param relativeZ The z coordinate relative to the beacon block
     */
    public record BeaconBase(BlockData data, int relativeX, int relativeY, int relativeZ) {
        public Block getBlockRelativeTo(Block beaconBlock) {
            return beaconBlock.getRelative(relativeX, relativeY, relativeZ);
        }

        /* B = Beacon, T = Surface block, F = Other block
                B
               TFT
              TFFFT
             TFFFFFT
            TFFFFFFFT
         */
        /**
         * Return whether this block would be considered a surface of the beacon base
         */
        public boolean isSurfaceBlock() {
            int positiveY = -relativeY;
            return relativeX == relativeY || relativeZ == relativeY || relativeX == positiveY || relativeZ == positiveY;
        }

        public static List<BeaconBase> fromLegacyMap(Map<? extends Vector, ? extends BlockData> map) {
            return map.entrySet().stream()
                    .map(entry -> {
                        var vector = entry.getKey();
                        return new BeaconBase(entry.getValue(), vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
                    })
                    .toList();
        }
    }

    /**
     * The list of blocks that make up the beacon base
     */
    public final List<BeaconBase> beaconBaseBlocks;

    public BeaconPyramid(int tier, List<BeaconBase> beaconBase) {
        Preconditions.checkState(tier > 0 && tier <= 4, "Tier is invalid");
        Preconditions.checkState(beaconBase.size() != 0, "Beacon base is empty");
        // Minecraft wiki: 164 for tier 4
        Preconditions.checkState(beaconBase.size() < 165, "Too many beacon base blocks!");
        this.tier = tier;
        this.beaconBaseBlocks = List.copyOf(beaconBase);
    }

    public BlockData getMajority() {
        Map<BlockData, Long> count = beaconBaseBlocks.stream().collect(groupingBy(BeaconBase::data, counting()));
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

        // PersistentDataContainer keys

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
            Map<BlockData, Map<Integer, List<BeaconBase>>> blockToLayers = complex.beaconBaseBlocks.stream()
                    .filter(beaconBase -> !majority.equals(beaconBase.data))
                    .collect(groupingBy(BeaconBase::data,
                            groupingBy(BeaconBase::relativeY, mapping(Function.identity(), toList()))));

            var tags = serializeOutliers(context, blockToLayers);
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
                Map<BlockVector, BlockData> beaconBase = new HashMap<>();
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

                return new BeaconPyramid(tier, BeaconBase.fromLegacyMap(beaconBase));
            }
            throw new UnsupportedOperationException("Data version " + dataVersion + " is unsupported");
        }


        /**
         * Serialize outliers into NBT:
         * {@code [
         *   {block: "minecraft:stone", layers: ...},
         *   {block: "minecraft:dirt", layers: ...},
         *   ...
         * ]}
         */
        private static PersistentDataContainer[] serializeOutliers(PersistentDataAdapterContext context,
                                                                   Map<BlockData, Map<Integer, List<BeaconBase>>> blockToLayers) {
            return blockToLayers.entrySet().stream()
                    .map(entry -> {
                        var blockContainer = context.newPersistentDataContainer();
                        blockContainer.set(BLOCK, STRING, entry.getKey().getAsString());
                        var layerToPosList = entry.getValue();
                        blockContainer.set(LAYERS, TAG_CONTAINER_ARRAY, serializeLayers(context, layerToPosList));
                        return blockContainer;
                    })
                    .toArray(PersistentDataContainer[]::new);
        }

        /**
         * Serialize layers into NBT:
         * {@code [
         *   {y_offset: -1, locations: [x1, z1, x2, z2, ...]},
         *   {y_offset: -2, locations: [x1, z1, x2, z2, ...]},
         *   ...
         * ]}
         */
        private static PersistentDataContainer[] serializeLayers(PersistentDataAdapterContext context,
                                                                 Map<Integer, List<BeaconBase>> layerToPosList) {
            return layerToPosList.entrySet().stream()
                    .map(layer -> {
                        PersistentDataContainer layerContainer = context.newPersistentDataContainer();
                        layerContainer.set(Y_OFFSET, BYTE, layer.getKey().byteValue());
                        // pack the locations into a byte array:
                        // [x1, z1, x2, z2, ...]
                        List<BeaconBase> pos = layer.getValue();
                        byte[] bytes = new byte[pos.size() * 2];
                        ListIterator<BeaconBase> iterator = pos.listIterator();
                        while (iterator.hasNext()) {
                            int idx = iterator.nextIndex();
                            BeaconBase next = iterator.next();
                            bytes[idx * 2] = (byte) next.relativeX;
                            bytes[idx * 2 + 1] = (byte) next.relativeZ;
                        }
                        layerContainer.set(LOCATIONS, BYTE_ARRAY, bytes);
                        return layerContainer;
                    })
                    .toArray(PersistentDataContainer[]::new);
        }
    }
}
