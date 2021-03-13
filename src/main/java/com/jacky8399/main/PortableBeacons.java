package com.jacky8399.main;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class PortableBeacons extends JavaPlugin {

    public static PortableBeacons INSTANCE;

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
        Metrics metrics = new Metrics(this, 10409);

        getCommand("portablebeacons").setExecutor(new CommandPortableBeacons());

        saveDefaultConfig();
        reloadConfig();
        Bukkit.getPluginManager().registerEvents(new Events(this), this);
        effectsTimer = new EffectsTimer();
        effectsTimer.register();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        Config.loadConfig();
    }

    @Override
    public void saveConfig() {
        FileConfiguration config = getConfig();
        config.set("item-used", Config.ritualItem);
        if (Config.itemCustomVersion != null)
            config.set("item-custom-version-do-not-edit", Config.itemCustomVersion);
        config.options().copyDefaults(true).header("To see descriptions of different options: \n" +
                "https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml");
        super.saveConfig();
    }

    @Override
    public void onDisable() {
        Events.cleanUp();
    }
}
