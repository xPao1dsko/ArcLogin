package com.arc.arclogin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Guarda las cuentas en plugins/ArcLogin/accounts.yml */
public class AccountStore {

    private final ArcLogin plugin;
    private final File file;
    private final YamlConfiguration data;

    public AccountStore(ArcLogin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "accounts.yml");
        plugin.getDataFolder().mkdirs();
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    private String key(String name) {
        return "accounts." + name.toLowerCase();
    }

    public synchronized boolean exists(String name) {
        return data.contains(key(name));
    }

    public synchronized boolean hasPassword(String name) {
        return data.getString(key(name) + ".password") != null;
    }

    public synchronized boolean isPremium(String name) {
        return data.getBoolean(key(name) + ".premium", false);
    }

    public synchronized void setPremium(String name, boolean premium) {
        data.set(key(name) + ".premium", premium);
        save();
    }

    public synchronized void setPassword(String name, String rawPassword) {
        data.set(key(name) + ".password", PasswordUtil.hash(rawPassword));
        save();
    }

    public synchronized boolean checkPassword(String name, String rawPassword) {
        return PasswordUtil.verify(rawPassword, data.getString(key(name) + ".password"));
    }

    public synchronized void setSession(String name, String ip) {
        data.set(key(name) + ".last-ip", ip);
        data.set(key(name) + ".last-seen", System.currentTimeMillis());
        save();
    }

    public synchronized boolean validSession(String name, String ip, int minutes) {
        if (minutes <= 0) return false;
        String last = data.getString(key(name) + ".last-ip");
        long seen = data.getLong(key(name) + ".last-seen", 0L);
        return ip.equals(last) && (System.currentTimeMillis() - seen) <= minutes * 60_000L;
    }

    public synchronized void remove(String name) {
        data.set(key(name), null);
        save();
    }

    public synchronized void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar accounts.yml: " + e.getMessage());
        }
    }
}
