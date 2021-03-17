package com.jacky8399.portablebeacons;

import com.jacky8399.portablebeacons.events.EffectsTimer;
import com.jacky8399.portablebeacons.events.Events;
import com.jacky8399.portablebeacons.events.ReminderOutline;
import com.jacky8399.portablebeacons.utils.WorldGuardHelper;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
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
        Events.registerEvents();
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
        Config.saveConfig();
        super.saveConfig();
    }

    @Override
    public void onDisable() {
        ReminderOutline.cleanUp();
    }
}
