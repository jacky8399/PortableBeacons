package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public final class RecipeManager {


    public static final Map<String, BeaconRecipe> RECIPES = new LinkedHashMap<>();
    public static final Set<BeaconRecipe> DISABLED_RECIPES = new LinkedHashSet<>();

    private static final PortableBeacons PLUGIN = PortableBeacons.INSTANCE;
    public static final File RECIPES_FILE = new File(PLUGIN.getDataFolder(), "recipes.yml");

    // lookup tables
    private static CombinationRecipe combinationRecipe;
    private static final Map<Material, List<SimpleRecipe>> anvilRecipes = new HashMap<>();
    private static final Map<Material, List<SimpleRecipe>> smithingRecipes = new HashMap<>();

    @Nullable
    public static BeaconRecipe findRecipeFor(InventoryType type, ItemStack right) {
        if (ItemUtils.isPortableBeacon(right))
            return combinationRecipe;
        Map<Material, List<SimpleRecipe>> recipeLookupMap = switch (type) {
            case ANVIL -> anvilRecipes;
            case SMITHING -> smithingRecipes;
            default -> throw new IllegalStateException();
        };
        List<SimpleRecipe> recipes = recipeLookupMap.get(right.getType());
        if (recipes == null)
            return null;
        for (var recipe : recipes) {
            if (recipe.input().isSimilar(right)) {
                return recipe;
            }
        }
        return null;
    }

    public static void cleanUp() {
        RECIPES.clear();
        DISABLED_RECIPES.clear();
        combinationRecipe = null;
        anvilRecipes.clear();
        smithingRecipes.clear();
    }

    private static void addRecipe(SimpleRecipe simpleRecipe) {
        Map<Material, List<SimpleRecipe>> recipes = switch (simpleRecipe.type()) {
            case ANVIL -> anvilRecipes;
            case SMITHING -> smithingRecipes;
            default -> throw new IllegalStateException();
        };
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
                    var recipe = BeaconRecipe.load(recipeName, recipeMap);
                    RECIPES.put(recipeName, recipe);
                    if (!(Boolean) recipeMap.getOrDefault("enabled", true)) {
                        DISABLED_RECIPES.add(recipe);
                    }
                    if (recipe instanceof CombinationRecipe combinationRecipe) {
                        if (RecipeManager.combinationRecipe != null) {
                            plugin.logger.severe("Cannot have more than one anvil combination recipes! Skipping " + recipeName + ".");
                        } else {
                            RecipeManager.combinationRecipe = combinationRecipe;
                        }
                    } else if (recipe instanceof SimpleRecipe simpleRecipe) {
                        addRecipe(simpleRecipe);
                    }

                } catch (Exception ex) {
                    plugin.logger.warning("Failed to load recipe " + recipeName);
                    ex.printStackTrace();
                }
            }
            plugin.logger.info("Loaded " + RECIPES.size() + " recipes");
        } catch (IOException ex) {
            plugin.logger.severe("Failed to open recipes.yml: " + ex);
        }
    }

    // use Bukkit defaults
    private static final Supplier<? extends Yaml> YAML_SUPPLIER = () -> {
        var constructor = new YamlConstructor();
        var representer = new YamlRepresenter();
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        var yamlDumperOptions = new DumperOptions();
        yamlDumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        var yamlLoaderOptions = new LoaderOptions();

        return new Yaml(constructor, representer, yamlDumperOptions, yamlLoaderOptions);
    };

}
