package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.events.ReminderOutline;
import com.jacky8399.portablebeacons.recipes.RecipeManager;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

public final class PortableBeacons extends JavaPlugin {

    public static PortableBeacons INSTANCE;
    public static Logger LOGGER;

    public Logger logger = getLogger();
    public EffectsTimer effectsTimer;

    public boolean worldGuardInstalled = false;

    @Override
    public void onLoad() {
        try {
            worldGuardInstalled = WorldGuardHelper.tryAddFlag(this);
            logger.info("Registered WorldGuard flag");
        } catch (Exception | NoClassDefFoundError e) {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
                logger.info("Failed to register WorldGuard flag, but WorldGuard loaded??");
                e.printStackTrace();
                worldGuardInstalled = true;
            }
        }
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        LOGGER = logger;
        try {
            Metrics metrics = new Metrics(this, 10409);
        } catch (Exception ignored) {}

        getCommand("portablebeacons").setExecutor(new CommandPortableBeacons());

        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipes.yml").exists())
            saveResource("recipes.yml", false);
        reloadConfig();
        Events.registerEvents();
        effectsTimer = new EffectsTimer();
        effectsTimer.register();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        Config.loadConfig();
        RecipeManager.loadRecipes();
    }

    @Override
    public void saveConfig() {
        try {
            Files.copy(new File(getDataFolder(), "config.yml").toPath(),
                    new File(getDataFolder(), "config.yml.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            logger.severe("Failed to backup config.yml");
            ex.printStackTrace();
        }

        Config.saveConfig();
        super.saveConfig();
    }

    @Override
    public void onDisable() {
        ReminderOutline.cleanUp();
        RecipeManager.cleanUp();
    }
}
