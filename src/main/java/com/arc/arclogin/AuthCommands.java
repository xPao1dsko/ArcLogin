package com.arc.arclogin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AuthCommands implements CommandExecutor {

    private final ArcLogin plugin;

    public AuthCommands(ArcLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "arclogin": return admin(sender, args);
            case "setloginspawn": return setSpawn(sender, "login");
            case "setregisterspawn": return setSpawn(sender, "register");
            default: break;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "register": return register(p, args);
            case "login": return login(p, args);
            case "changepassword": return changePassword(p, args);
            case "premium": return premium(p);
            default: return false;
        }
    }

    // ---------------------------------------------------------------- /register
    private boolean register(Player p, String[] a) {
        AccountStore store = plugin.store();
        if (ArcLogin.isVerifiedPremium(p)) { p.sendMessage(plugin.msg("register-premium")); return true; }
        if (store.hasPassword(p.getName())) { p.sendMessage(plugin.msg("register-already")); return true; }
        if (a.length != 2) { p.sendMessage(plugin.msg("register-usage")); return true; }
        if (!a[0].equals(a[1])) { p.sendMessage(plugin.msg("register-mismatch")); return true; }
        int min = plugin.getConfig().getInt("settings.min-password-length", 6);
        if (a[0].length() < min) { p.sendMessage(plugin.msg("register-short", "min", String.valueOf(min))); return true; }

        store.setPassword(p.getName(), a[0]);
        p.sendMessage(plugin.msg("register-done"));
        plugin.finishAuth(p, true);
        return true;
    }

    // ---------------------------------------------------------------- /login
    private boolean login(Player p, String[] a) {
        AccountStore store = plugin.store();
        if (plugin.isLogged(p)) { p.sendMessage(plugin.msg("login-already")); return true; }
        if (!store.hasPassword(p.getName())) { p.sendMessage(plugin.msg("login-not-registered")); return true; }
        if (a.length != 1) { p.sendMessage(plugin.msg("login-usage")); return true; }

        if (store.checkPassword(p.getName(), a[0])) {
            p.sendMessage(plugin.msg("login-done"));
            plugin.finishAuth(p, false);
        } else {
            int max = plugin.getConfig().getInt("settings.max-login-attempts", 3);
            int used = plugin.addAttempt(p);
            if (used >= max) {
                p.kickPlayer(plugin.raw("too-many-attempts"));
            } else {
                p.sendMessage(plugin.msg("login-wrong", "left", String.valueOf(max - used)));
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- /changepassword
    private boolean changePassword(Player p, String[] a) {
        AccountStore store = plugin.store();
        if (!plugin.isLogged(p)) { p.sendMessage(plugin.msg("not-logged")); return true; }
        if (!store.hasPassword(p.getName())) { p.sendMessage(plugin.msg("cp-premium")); return true; }
        if (a.length != 2) { p.sendMessage(plugin.msg("cp-usage")); return true; }
        if (!store.checkPassword(p.getName(), a[0])) { p.sendMessage(plugin.msg("cp-wrong")); return true; }
        int min = plugin.getConfig().getInt("settings.min-password-length", 6);
        if (a[1].length() < min) { p.sendMessage(plugin.msg("register-short", "min", String.valueOf(min))); return true; }

        store.setPassword(p.getName(), a[1]);
        p.sendMessage(plugin.msg("cp-done"));
        return true;
    }

    // ---------------------------------------------------------------- /premium
    private boolean premium(Player p) {
        AccountStore store = plugin.store();
        String name = p.getName();

        if (store.isPremium(name)) { p.sendMessage(plugin.msg("premium-already")); return true; }

        // Ya verificado por Mojang (online-mode / proxy): no hace falta consultar la API
        if (ArcLogin.isVerifiedPremium(p)) {
            store.setPremium(name, true);
            plugin.setLogged(p, true);
            p.sendMessage(plugin.msg("premium-ok"));
            return true;
        }

        p.sendMessage(plugin.msg("premium-checking"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int status = lookupMojang(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                if (status == 200) {
                    store.setPremium(name, true);
                    if (plugin.getConfig().getBoolean("premium.require-verified", true)) {
                        p.kickPlayer(plugin.raw("premium-kick-after"));
                    } else {
                        p.sendMessage(plugin.msg("premium-ok-offline"));
                    }
                } else if (status == 204 || status == 404) {
                    p.sendMessage(plugin.msg("premium-not-found"));
                } else {
                    p.sendMessage(plugin.msg("premium-error"));
                }
            });
        });
        return true;
    }

    /** Devuelve el codigo HTTP de la API de Mojang (200 = existe cuenta premium con ese nombre). */
    private int lookupMojang(String name) {
        try {
            String url = "https://api.mojang.com/users/profiles/minecraft/"
                    + URLEncoder.encode(name, StandardCharsets.UTF_8);
            HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setRequestProperty("User-Agent", "ArcLogin/1.0");
            int code = con.getResponseCode();
            con.disconnect();
            return code;
        } catch (Exception ex) {
            plugin.getLogger().warning("Error consultando Mojang: " + ex.getMessage());
            return -1;
        }
    }

    // ---------------------------------------------------------------- /arclogin (admin)
    private boolean admin(CommandSender s, String[] a) {
        if (!s.hasPermission("arclogin.admin")) { s.sendMessage(plugin.msg("no-permission")); return true; }
        if (a.length == 0) { s.sendMessage(plugin.msg("admin-usage")); return true; }

        switch (a[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage(plugin.msg("reloaded"));
            }
            case "unregister" -> {
                if (a.length < 2) { s.sendMessage(plugin.msg("admin-usage")); return true; }
                if (!plugin.store().exists(a[1])) { s.sendMessage(plugin.msg("admin-unknown")); return true; }
                plugin.store().remove(a[1]);
                s.sendMessage(plugin.msg("admin-unregistered", "player", a[1]));
            }
            case "setpremium" -> {
                if (a.length < 3) { s.sendMessage(plugin.msg("admin-usage")); return true; }
                boolean value = Boolean.parseBoolean(a[2]);
                plugin.store().setPremium(a[1], value);
                s.sendMessage(plugin.msg("admin-set", "player", a[1], "value", String.valueOf(value)));
            }
            case "setloginspawn" -> setSpawn(s, "login");
            case "setregisterspawn" -> setSpawn(s, "register");
            case "delspawn", "tpspawn" -> {
                String type = a.length > 1 ? a[1].toLowerCase() : "";
                if (!type.equals("login") && !type.equals("register")) { s.sendMessage(plugin.msg("admin-usage")); return true; }
                if (a[0].equalsIgnoreCase("delspawn")) {
                    plugin.spawns().delete(type);
                    s.sendMessage(plugin.msg("spawn-deleted", "type", type));
                } else if (s instanceof Player p) {
                    var loc = plugin.spawns().get(type);
                    if (loc == null) { s.sendMessage(plugin.msg("spawn-none")); return true; }
                    p.teleport(loc);
                    s.sendMessage(plugin.msg("spawn-tp", "type", type));
                } else {
                    s.sendMessage(plugin.msg("only-players"));
                }
            }
            default -> s.sendMessage(plugin.msg("admin-usage"));
        }
        return true;
    }

    // ---------------------------------------------------------------- spawns
    private boolean setSpawn(CommandSender s, String type) {
        if (!s.hasPermission("arclogin.admin")) { s.sendMessage(plugin.msg("no-permission")); return true; }
        if (!(s instanceof Player p)) { s.sendMessage(plugin.msg("only-players")); return true; }
        plugin.spawns().set(type, p.getLocation());
        p.sendMessage(plugin.msg(type.equals("login") ? "spawn-set-login" : "spawn-set-register"));
        return true;
    }
}
