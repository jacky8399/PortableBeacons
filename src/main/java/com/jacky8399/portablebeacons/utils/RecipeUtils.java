package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public class RecipeUtils {

    private static final ItemStack EMPTY_BEACON = ItemUtils.createStack(new BeaconEffects());

    public static RecipeChoice getBeaconPlaceholder() {
        return new RecipeChoice.ExactChoice(EMPTY_BEACON.clone());
    }

    public static ItemStack getBeaconResult(List<BeaconModification> modifications) {
        var effects = new BeaconEffects();
        for (BeaconModification modification : modifications) {
            modification.modify(effects);
        }
        return ItemUtils.createStack(effects);
    }

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
}
