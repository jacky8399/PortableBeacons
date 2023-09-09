package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.RecipeUtils;
import com.jacky8399.portablebeacons.utils.SmithingUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class RecipeManager {

    public static final int CONFIG_VERSION = 1;

    public static final Map<String, BeaconRecipe> RECIPES = new LinkedHashMap<>();
    public static final List<String> DISABLED_RECIPES = new ArrayList<>();

    private static final PortableBeacons PLUGIN = PortableBeacons.INSTANCE;
    public static final File RECIPES_FILE = new File(PLUGIN.getDataFolder(), "recipes.yml");

    // lookup tables
    private static CombinationRecipe beaconCombinationRecipe;
    private static final Map<Material, List<SimpleRecipe>> anvilRecipes = new HashMap<>();
    private static final Map<Material, List<SimpleRecipe>> smithingRecipes = new HashMap<>();

    @Nullable
    public static BeaconRecipe findRecipeFor(InventoryType type, ItemStack beacon, ItemStack right) {
        if (Config.debug)
            PortableBeacons.LOGGER.info("[RecipeManager] Finding %s recipe for beacon=%s, input=%s".formatted(type, beacon, right));

        if (type == InventoryType.ANVIL && ItemUtils.isPortableBeacon(right) &&
            beaconCombinationRecipe.isApplicableTo(beacon, right))
            return beaconCombinationRecipe;
        Map<Material, List<SimpleRecipe>> recipeLookupMap = switch (type) {
            case ANVIL -> anvilRecipes;
            case SMITHING -> smithingRecipes;
            default -> throw new IllegalStateException();
        };
        List<SimpleRecipe> recipes = recipeLookupMap.get(right.getType());
        if (recipes == null)
            return null;
        for (var recipe : recipes) {
            if (recipe.isApplicableTo(beacon, right)) {
                return recipe;
            }
        }
        return null;
    }

    public static void cleanUp() {
        RECIPES.clear();
        DISABLED_RECIPES.clear();
        beaconCombinationRecipe = null;
        anvilRecipes.clear();

        smithingRecipes.values().forEach(recipes -> recipes.forEach(recipe -> {
            if (!Bukkit.removeRecipe(recipe.id()))
                PortableBeacons.INSTANCE.logger.warning("Failed to remove smithing recipe " + recipe.id());
        }));

        smithingRecipes.clear();
    }

    private static void addRecipe(SimpleRecipe simpleRecipe) {
        Map<Material, List<SimpleRecipe>> recipes;
        if (simpleRecipe.type() == InventoryType.SMITHING) {
            recipes = smithingRecipes;

            var bukkitRecipe = SmithingUtils.makeSmithingRecipe(
                    simpleRecipe.id(),
                    simpleRecipe.template() != null ?
                            new RecipeChoice.ExactChoice(simpleRecipe.template()) :
                            new RecipeChoice.MaterialChoice(Material.AIR),
                    new RecipeChoice.ExactChoice(simpleRecipe.input()),
                    simpleRecipe.modifications()
            );
            try {
                if (!Bukkit.addRecipe(bukkitRecipe)) // throws IllegalStateException for some reason
                    throw new IllegalStateException("Bukkit.addRecipe");
            } catch (Exception ex) {
                PortableBeacons.INSTANCE.logger.log(Level.WARNING, "Failed to register smithing recipe " + simpleRecipe.id(), ex);
            }
        } else {
            recipes = anvilRecipes;
        }
        recipes.computeIfAbsent(simpleRecipe.input().getType(), ignored -> new ArrayList<>()).add(simpleRecipe);
    }

    public static void loadRecipes() {
        cleanUp();

        PortableBeacons plugin = PortableBeacons.INSTANCE;
        var recipesFile = RECIPES_FILE;
        if (!recipesFile.exists()) {
            return;
        }
        Yaml yaml = YAML_SUPPLIER.get();
        try (var inputStream = new FileInputStream(recipesFile)) {
            Map<String, Object> map = yaml.load(inputStream);
            for (var entry : ((Map<String, Object>) map.get("recipes")).entrySet()) {
                String recipeName = entry.getKey();
                try {
                    Map<String, Object> recipeMap = (Map<String, Object>) entry.getValue();
                    BeaconRecipe recipe = BeaconRecipe.load(recipeName, recipeMap);
                    RECIPES.put(recipeName, recipe);
                    if (!(Boolean) recipeMap.getOrDefault("enabled", true)) {
                        DISABLED_RECIPES.add(recipeName);
                        continue;
                    }
                    if (recipe instanceof CombinationRecipe newCombinationRecipe) {
                        if (RecipeManager.beaconCombinationRecipe != null) {
                            plugin.logger.severe("Cannot have more than one beacon combination recipes! Skipping " + recipeName + ".");
                        } else {
                            RecipeManager.beaconCombinationRecipe = newCombinationRecipe;
                        }
                    } else if (recipe instanceof SimpleRecipe simpleRecipe) {
                        addRecipe(simpleRecipe);
                    }

                } catch (Exception ex) {
                    plugin.logger.log(Level.WARNING, "Failed to load recipe " + recipeName, ex);
                }
            }
            plugin.logger.info("Loaded " + RECIPES.size() + " recipes");
        } catch (IOException ex) {
            plugin.logger.severe("Failed to open recipes.yml: " + ex);
        }

        // try sending the new recipes or something
        if (!smithingRecipes.isEmpty())
            Bukkit.getScheduler().runTask(PortableBeacons.INSTANCE, RecipeUtils::updateRecipes);
    }

    // use Bukkit defaults
    private static final Supplier<? extends Yaml> YAML_SUPPLIER = () -> {
        var constructor = new YamlConstructor(new LoaderOptions().setProcessComments(true));

        var yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setProcessComments(true);
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        var representer = new YamlRepresenter(yamlDumperOptions);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        return new Yaml(constructor, representer, yamlDumperOptions, constructor.getLoadingConfig());
    };

}
