package com.jacky8399.main;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class PortableBeacons extends JavaPlugin {

    public static PortableBeacons INSTANCE;

    public Logger logger = getLogger();

    @Override
    public void onEnable() {
        logger.info("PortableBeacons is starting up!");
        INSTANCE = this;

        CommandPortableBeacons cmd = new CommandPortableBeacons();
        getCommand("portablebeacons").setExecutor(cmd);

        saveDefaultConfig();
        reloadConfig();
        saveConfig();
        Bukkit.getPluginManager().registerEvents(new Events(this), this);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        Config.loadConfig();
        BeaconEffects.loadConfig(getConfig());
    }

    @Override
    public void saveConfig() {
        FileConfiguration config = getConfig();
        config.set("item-used", Config.ritualItem);
        if (Config.itemCustomVersion != null)
            config.set("item-custom-version-do-not-edit", Config.itemCustomVersion);
        config.options().copyDefaults(true).header("Template: https://github.com/jacky8399/PortableBeacons/blob/master/src/main/resources/config.yml").copyHeader(true);
        super.saveConfig();
    }

    @Override
    public void onDisable() {
        logger.info("PortableBeacons is shutting down!");
        Events.onDisable();
    }
}
