package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.recipes.CombinationRecipe;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

public class RecipeUtils {

    private static final ItemStack EMPTY_BEACON = ItemUtils.createStack(null, new BeaconEffects());

    public static RecipeChoice getBeaconPlaceholder() {
        return new RecipeChoice.ExactChoice(EMPTY_BEACON.clone());
    }

    public static ItemStack getBeaconCondition(@Nullable BeaconCondition condition) {
        ItemStack base = EMPTY_BEACON.clone();
        if (condition == null)
            return base;
        ItemMeta meta = base.getItemMeta();
        List<String> rawLore = meta.hasLore() ? new ArrayList<>(ItemUtils.getRawLore(meta)) : new ArrayList<>();

        rawLore.add("{\"text\":\"\"}");

        TextUtils.formatCondition(condition).forEach(baseComponent -> rawLore.add(ComponentSerializer.toString(baseComponent)));

        ItemUtils.setRawLore(meta, rawLore);
        base.setItemMeta(meta);
        return base;
    }

    public static ItemStack getBeaconResult(List<BeaconModification> modifications) {
        var effects = new BeaconEffects();
        for (BeaconModification modification : modifications) {
            modification.modify(effects);
        }
        return ItemUtils.createStack(null, effects);
    }

    // Paper method to force recipes to update
    private static final @Nullable MethodHandle UPDATE_RECIPES;
    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle updateRecipes = null;
        try {
            updateRecipes = lookup.findStatic(Bukkit.class, "updateRecipes", MethodType.methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
        }
        UPDATE_RECIPES = updateRecipes;
    }

    public static void updateRecipes() {
        if (UPDATE_RECIPES != null) {
            try {
                UPDATE_RECIPES.invoke();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static SmithingRecipe makeSmithingRecipe(CombinationRecipe combinationRecipe) {
        return new SmithingTransformRecipe(
                combinationRecipe.id(),
                getBeaconCondition(combinationRecipe.resultCondition()),
                combinationRecipe.template() != null ?
                        new RecipeChoice.ExactChoice(combinationRecipe.template()) :
                        new RecipeChoice.MaterialChoice(Material.AIR),
                new RecipeChoice.ExactChoice(getBeaconCondition(combinationRecipe.beaconCondition())),
                new RecipeChoice.ExactChoice(getBeaconCondition(combinationRecipe.sacrificeCondition()))
        );
    }

    public static SmithingRecipe makeSmithingRecipe(NamespacedKey key, RecipeChoice template, RecipeChoice addition, List<BeaconModification> modifications) {
        ItemStack result = getBeaconResult(modifications);
        return new SmithingTransformRecipe(key, result, template, getBeaconPlaceholder(), addition);
    }
}
