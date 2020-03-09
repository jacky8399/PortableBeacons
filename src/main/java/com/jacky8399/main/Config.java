package com.jacky8399.main;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class Config {

    public static void loadConfig() {
        FileConfiguration config = PortableBeacons.INSTANCE.getConfig();
        ritualItem = config.getItemStack("item-used",
                config.getItemStack("item_used",  new ItemStack(Material.NETHER_STAR, 32))); // migrate old data

        itemCustomVersion = config.getString("item-custom-version-do-not-edit");

        itemName = config.getString("beacon-item.name", "");
        itemLore = config.getStringList("beacon-item.lore");
        itemCustomModelData = config.getInt("beacon-item.custom-model-data");

        itemCreationReminderEnabled = config.getBoolean("beacon-item.creation-reminder.enabled");
        itemCreationReminderMessage = config.getString("beacon-item.creation-reminder.message");
        itemCreationReminderRadius = config.getDouble("beacon-item.creation-reminder.radius");
        itemCreationReminderDisableIfAlreadyOwnBeaconItem = config.getBoolean("beacon-item.creation-reminder.disable-if-already-own-beacon-item");

        itemNerfsExpPercentagePerCycle = config.getDouble("beacon-item.nerfs.exp-percentage-per-cycle");
        itemNerfsOnlyApplyInHotbar = config.getBoolean("beacon-item.nerfs.only-apply-in-hotbar");

        anvilCombinationEnabled = config.getBoolean("anvil-combination.enabled");
        anvilCombinationMaxEffects = config.getInt("anvil-combination.max-effects");
        anvilCombinationMaxAmplifier = config.getInt("anvil-combination.max-effect-amplifier");
        anvilCombinationCombineEffectsAdditively = config.getBoolean("anvil-combination.combine-effects-additively");
    }

    public static ItemStack ritualItem;

    public static String itemName;
    public static List<String> itemLore;
    public static int itemCustomModelData;
    public static String itemCustomVersion = null;

    public static boolean itemCreationReminderEnabled;
    public static String itemCreationReminderMessage;
    public static double itemCreationReminderRadius;
    public static boolean itemCreationReminderDisableIfAlreadyOwnBeaconItem;

    public static double itemNerfsExpPercentagePerCycle;
    public static boolean itemNerfsOnlyApplyInHotbar;

    public static boolean anvilCombinationEnabled;
    public static int anvilCombinationMaxEffects;
    public static int anvilCombinationMaxAmplifier;
    public static boolean anvilCombinationCombineEffectsAdditively;
}
