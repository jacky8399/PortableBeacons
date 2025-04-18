package com.jacky8399.portablebeacons.recipes;

import com.jacky8399.portablebeacons.Config;
import com.jacky8399.portablebeacons.PortableBeacons;
import com.jacky8399.portablebeacons.utils.InventoryTypeUtils;
import com.jacky8399.portablebeacons.utils.ItemUtils;
import com.jacky8399.portablebeacons.utils.RecipeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
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
import java.util.logging.Level;

public final class RecipeManager {

    public static final int CONFIG_VERSION = 1;

    public static final Map<String, BeaconRecipe> recipes = new LinkedHashMap<>();
    public static final List<String> disabledRecipes = new ArrayList<>();

    private static final PortableBeacons PLUGIN = PortableBeacons.INSTANCE;
    public static final File RECIPES_FILE = new File(PLUGIN.getDataFolder(), "recipes.yml");

    // lookup tables
    private static final List<CombinationRecipe> smithingCombinationRecipes = new ArrayList<>();
    private static final List<CombinationRecipe> anvilCombinationRecipes = new ArrayList<>();
    private static final Map<Material, List<SimpleRecipe>> anvilRecipes = new HashMap<>();
    private static final Map<Material, List<SimpleRecipe>> smithingRecipes = new HashMap<>();

    @Nullable
    public static BeaconRecipe findRecipeFor(InventoryType type, Inventory inventory) {
        ItemStack beacon = inventory.getItem(InventoryTypeUtils.getBeaconSlot(type));
        ItemStack right = inventory.getItem(InventoryTypeUtils.getSacrificeSlot(type));
        if (Config.debug)
            PortableBeacons.LOGGER.info("[RecipeManager] Finding %s recipe for beacon=%s, input=%s".formatted(type, beacon, right));

        if (right == null)
            return null;

        boolean isBeacon = ItemUtils.isPortableBeacon(right);
        boolean isAnvil = type == InventoryType.ANVIL;
        if (isBeacon) {
            List<CombinationRecipe> recipes = isAnvil ? anvilCombinationRecipes : smithingCombinationRecipes;
            for (CombinationRecipe recipe : recipes) {
                if (recipe.isApplicableTo(type, inventory)) {
                    if (Config.debug)
                        PortableBeacons.LOGGER.info("[RecipeManager] Found combination recipe " + recipe.id());
                    return recipe;
                }
            }
        } else {
            var map = isAnvil ? anvilRecipes : smithingRecipes;
            for (var recipe : map.get(right.getType())) {
                if (recipe.isApplicableTo(type, inventory)) {
                    if (Config.debug)
                        PortableBeacons.LOGGER.info("[RecipeManager] Found simple recipe " + recipe.id());
                    return recipe;
                }
            }
        }
        return null;
    }

    public static void cleanUp() {
        recipes.clear();
        disabledRecipes.clear();
        anvilCombinationRecipes.clear();
        anvilRecipes.clear();

        smithingCombinationRecipes.forEach(recipe -> {
            if (!Bukkit.removeRecipe(recipe.id()))
                PortableBeacons.INSTANCE.logger.warning("Failed to remove smithing recipe " + recipe.id());
        });
        smithingRecipes.values().forEach(recipes -> recipes.forEach(recipe -> {
            if (!Bukkit.removeRecipe(recipe.id()))
                PortableBeacons.INSTANCE.logger.warning("Failed to remove smithing recipe " + recipe.id());
        }));

        smithingRecipes.clear();
    }

    private static void addCombinationRecipe(CombinationRecipe combinationRecipe) {
        if (combinationRecipe.type() == InventoryType.SMITHING) {
            smithingCombinationRecipes.add(combinationRecipe);

            var bukkitRecipe = RecipeUtils.makeSmithingRecipe(combinationRecipe);
            try {
                if (!Bukkit.addRecipe(bukkitRecipe)) // throws IllegalStateException for some reason
                    throw new IllegalStateException("Bukkit.addRecipe");
            } catch (Exception ex) {
                PortableBeacons.INSTANCE.logger.log(Level.WARNING, "Failed to register smithing recipe " + combinationRecipe.id(), ex);
            }
        } else {
            anvilCombinationRecipes.add(combinationRecipe);
        }
    }

    private static void addRecipe(SimpleRecipe simpleRecipe) {
        Map<Material, List<SimpleRecipe>> recipes;
        if (simpleRecipe.type() == InventoryType.SMITHING) {
            recipes = smithingRecipes;

            var bukkitRecipe = RecipeUtils.makeSmithingRecipe(simpleRecipe);
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
                    recipes.put(recipeName, recipe);
                    if (!(Boolean) recipeMap.getOrDefault("enabled", true)) {
                        disabledRecipes.add(recipeName);
                        continue;
                    }
                    if (recipe instanceof CombinationRecipe combinationRecipe) {
                        addCombinationRecipe(combinationRecipe);
                    } else if (recipe instanceof SimpleRecipe simpleRecipe) {
                        addRecipe(simpleRecipe);
                    }

                } catch (Exception ex) {
                    plugin.logger.log(Level.WARNING, "Failed to load recipe " + recipeName, ex);
                }
            }
            plugin.logger.info("Loaded " + recipes.size() + " recipes");
        } catch (IOException ex) {
            plugin.logger.severe("Failed to open recipes.yml: " + ex);
        }

        // try sending the new recipes or something
        if (!smithingRecipes.isEmpty() || !smithingCombinationRecipes.isEmpty())
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
