package com.arc.arclogin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

public class AuthListener implements Listener {

    private static final Set<String> ALLOWED = Set.of("login", "l", "register", "reg", "premium");

    private final ArcLogin plugin;

    public AuthListener(ArcLogin plugin) {
        this.plugin = plugin;
    }

    private static String ip(Player p) {
        return p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
    }

    // ---------------------------------------------------------------- JOIN
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String name = p.getName();
        AccountStore store = plugin.store();
        boolean verified = ArcLogin.isVerifiedPremium(p);

        plugin.setLogged(p, false);

        // 1) Cuenta marcada premium pero sin verificacion de Mojang -> suplantacion
        if (store.isPremium(name) && !verified && plugin.getConfig().getBoolean("premium.require-verified", true)) {
            p.kickPlayer(plugin.raw("premium-kick"));
            return;
        }

        // 2) Premium verificado -> auto login
        if (verified && plugin.getConfig().getBoolean("premium.auto-login-verified", true)) {
            if (plugin.getConfig().getBoolean("premium.auto-mark-verified", true) && !store.isPremium(name)) {
                store.setPremium(name, true);
            }
            plugin.setLogged(p, true);
            p.sendMessage(plugin.msg("premium-auto"));
            return;
        }

        // 3) Sesion reciente con la misma IP
        int minutes = plugin.getConfig().getInt("settings.session-minutes", 10);
        if (store.hasPassword(name) && store.validSession(name, ip(p), minutes)) {
            plugin.setLogged(p, true);
            p.sendMessage(plugin.msg("session-auto"));
            return;
        }

        // 4) No premium -> debe registrarse / loguearse
        plugin.sendToLoginSpawn(p);
        p.sendMessage(plugin.joinPrompt(p));
        int timeout = plugin.getConfig().getInt("settings.login-timeout-seconds", 60);
        if (timeout > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !plugin.isLogged(p)) p.kickPlayer(plugin.raw("timeout-kick"));
            }, timeout * 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (plugin.isLogged(p) && plugin.store().hasPassword(p.getName())) {
            plugin.store().setSession(p.getName(), ip(p));
        }
        if (!plugin.isLogged(p)) plugin.restoreOrigin(p);
        plugin.setLogged(p, false);
    }

    // ---------------------------------------------------------------- BLOQUEOS
    private boolean block(Player p) {
        if (plugin.isLogged(p)) return false;
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        if (!block(e.getPlayer()) || e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX()
                || e.getFrom().getY() != e.getTo().getY()
                || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent e) {
        if (block(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.msg("not-logged"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!block(p)) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!ALLOWED.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(plugin.msg("not-logged"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (block(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (block(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (block(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (block(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (block(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && block(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && block(p)) e.setCancelled(true);
    }
}
