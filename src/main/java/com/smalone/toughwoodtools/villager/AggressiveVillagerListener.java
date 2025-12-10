// src/main/java/com/smalone/toughwoodtools/villager/AggressiveVillagerListener.java
package com.smalone.toughwoodtools.villager;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listener that triggers villager aggression according to plugin config.
 * This class does NOT reference any project-specific main-class.
 */
public class AggressiveVillagerListener implements Listener {

    private final JavaPlugin plugin;
    private final AggroManager manager;

    public AggressiveVillagerListener(JavaPlugin plugin, AggroManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("villager-aggro.enabled", true)) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) return;

        if (!(event.getDamager() instanceof Player)) return;
        Player damager = (Player) event.getDamager();

        String trigger = plugin.getConfig().getString("villager-aggro.trigger", "retaliate");
        if ("retaliate".equalsIgnoreCase(trigger)) {
            manager.makeAggressive((Villager) entity, damager);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("villager-aggro.enabled", true)) return;

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager)) return;

        String trigger = plugin.getConfig().getString("villager-aggro.trigger", "retaliate");
        if ("toggle-on-interact".equalsIgnoreCase(trigger)) {
            Villager v = (Villager) clicked;
            Player p = event.getPlayer();
            if (manager.isAggressive(v)) {
                manager.removeAggro(v);
                if (plugin.getConfig().getBoolean("villager-aggro.debug", false)) {
                    plugin.getLogger().info("[AggressiveVillagerListener] " + p.getName() + " toggled OFF aggression for villager " + v.getUniqueId());
                }
            } else {
                manager.makeAggressive(v, p);
                if (plugin.getConfig().getBoolean("villager-aggro.debug", false)) {
                    plugin.getLogger().info("[AggressiveVillagerListener] " + p.getName() + " toggled ON aggression for villager " + v.getUniqueId());
                }
            }
        }
    }
}
