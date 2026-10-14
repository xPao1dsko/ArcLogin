package com.arc.arclogin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArcLogin extends JavaPlugin {

    private AccountStore store;
    private SpawnManager spawns;
    private final Map<UUID, Location> origin = new ConcurrentHashMap<>();
    private final Set<UUID> logged = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new AccountStore(this);
        spawns = new SpawnManager(this);

        getServer().getPluginManager().registerEvents(new AuthListener(this), this);

        AuthCommands cmds = new AuthCommands(this);
        for (String name : List.of("register", "login", "changepassword", "premium", "arclogin", "setloginspawn", "setregisterspawn")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) cmd.setExecutor(cmds);
        }

        // Recordatorio periodico a los que no han iniciado sesion
        long period = Math.max(1, getConfig().getInt("settings.reminder-seconds", 10)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!isLogged(p)) p.sendMessage(joinPrompt(p));
            }
        }, period, period);

        getLogger().info("ArcLogin activado.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    public AccountStore store() { return store; }
    public SpawnManager spawns() { return spawns; }

    /** Manda al jugador no logueado al spawn de login (recordando donde estaba). */
    public void sendToLoginSpawn(Player p) {
        Location l = spawns.get("login");
        if (l == null) return;
        origin.putIfAbsent(p.getUniqueId(), p.getLocation());
        p.teleport(l);
    }

    /** Si el jugador sale sin loguearse, lo devuelve a su sitio para que no quede guardado en el spawn de login. */
    public void restoreOrigin(Player p) {
        Location o = origin.remove(p.getUniqueId());
        if (o != null) p.teleport(o);
    }

    /** Marca al jugador como autenticado y lo teletransporta segun la config. */
    public void finishAuth(Player p, boolean firstRegister) {
        setLogged(p, true);
        Location orig = origin.remove(p.getUniqueId());
        Location target = firstRegister ? spawns.get("register") : null;
        if (target == null && orig != null && getConfig().getBoolean("spawn.return-to-last-location", true)) {
            target = orig;
        }
        if (target != null) p.teleport(target);
    }

    /** UUID version 4 = autenticado por Mojang. Los UUID offline son version 3. */
    public static boolean isVerifiedPremium(Player p) {
        return p.getUniqueId().version() == 4;
    }

    public boolean isLogged(Player p) { return logged.contains(p.getUniqueId()); }
    public void setLogged(Player p, boolean value) {
        if (value) {
            logged.add(p.getUniqueId());
            attempts.remove(p.getUniqueId());
        } else {
            logged.remove(p.getUniqueId());
        }
    }

    public int addAttempt(Player p) {
        return attempts.merge(p.getUniqueId(), 1, Integer::sum);
    }

    public String joinPrompt(Player p) {
        return msg(store.hasPassword(p.getName()) ? "join-login" : "join-register");
    }

    /** Mensaje con prefijo, colores y placeholders {clave} -> valor. */
    public String msg(String key, String... kv) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            text = text.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        String prefix = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + text);
    }

    /** Igual que msg pero sin prefijo (para kicks). */
    public String raw(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key, key));
    }
}
