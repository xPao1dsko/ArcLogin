package com.arc.arclogin;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Guarda los spawns "login" y "register" en plugins/ArcLogin/spawns.yml */
public class SpawnManager {

    private final ArcLogin plugin;
    private final File file;
    private final YamlConfiguration data;

    public SpawnManager(ArcLogin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawns.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Devuelve null si no esta definido o su mundo no esta cargado. */
    public Location get(String type) {
        try {
            Location loc = data.getLocation(type);
            return (loc != null && loc.getWorld() != null) ? loc : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void set(String type, Location loc) {
        data.set(type, loc);
        save();
    }

    public void delete(String type) {
        data.set(type, null);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar spawns.yml: " + e.getMessage());
        }
    }
}
