package com.jacky8399.portablebeacons.utils;

import com.jacky8399.portablebeacons.BeaconEffects;
import com.jacky8399.portablebeacons.recipes.CombinationRecipe;
import com.jacky8399.portablebeacons.recipes.SimpleRecipe;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
    private static final RecipeChoice.ExactChoice EMPTY_CHOICE;
    static {
        var emptyStack = new ItemStack(Material.BARRIER);
        ItemMeta meta = emptyStack.getItemMeta();
        meta.setDisplayName(ChatColor.RED.toString());
        emptyStack.setItemMeta(meta);
        EMPTY_CHOICE = new RecipeChoice.ExactChoice(emptyStack);
    }

    public static RecipeChoice getBeaconPlaceholder() {
        return new RecipeChoice.ExactChoice(EMPTY_BEACON.clone());
    }

    public static ItemStack getBeaconCondition(ItemStack base, @Nullable BeaconCondition condition) {
        if (condition == null) {
            return base;
        }
        base = base.clone();
        ItemMeta meta = base.getItemMeta();
        List<String> rawLore = meta.hasLore() ? new ArrayList<>(ItemUtils.getRawLore(meta)) : new ArrayList<>();

        rawLore.add("{\"text\":\"\"}");

        TextUtils.formatCondition(condition).forEach(baseComponent -> rawLore.add(ComponentSerializer.toString(baseComponent)));

        ItemUtils.setRawLore(meta, rawLore);
        base.setItemMeta(meta);
        return base;
    }

    public static ItemStack getBeaconResult(List<BeaconModification> modifications, @Nullable BeaconCondition condition) {
        var effects = new BeaconEffects();
        for (BeaconModification modification : modifications) {
            modification.modify(effects);
        }
        var stack = ItemUtils.createStack(null, effects);
        return getBeaconCondition(stack, condition);
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
                getBeaconResult(combinationRecipe.modifications(), combinationRecipe.resultCondition()),
                combinationRecipe.template() != null ?
                        new RecipeChoice.ExactChoice(combinationRecipe.template()) :
                        EMPTY_CHOICE, // 1.20.5+ will no longer allow air as recipe choice
                new RecipeChoice.ExactChoice(getBeaconCondition(EMPTY_BEACON, combinationRecipe.beaconCondition())),
                new RecipeChoice.ExactChoice(getBeaconCondition(EMPTY_BEACON, combinationRecipe.sacrificeCondition()))
        );
    }

    public static SmithingRecipe makeSmithingRecipe(SimpleRecipe simpleRecipe) {
        return new SmithingTransformRecipe(
                simpleRecipe.id(),
                getBeaconResult(simpleRecipe.modifications(), simpleRecipe.resultCondition()),
                simpleRecipe.template() != null ?
                        new RecipeChoice.ExactChoice(simpleRecipe.template()) :
                        EMPTY_CHOICE,
                new RecipeChoice.ExactChoice(getBeaconCondition(EMPTY_BEACON, simpleRecipe.beaconCondition())),
                new RecipeChoice.ExactChoice(simpleRecipe.input())
        );
    }
}
