// src/main/java/com/smalone/toughwoodtools/villager/AggroManager.java
package com.smalone.toughwoodtools.villager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AggroManager maintains a set of aggressive villagers and runs a repeating task
 * to move them toward players and apply damage when in range.
 *
 * This implementation uses only Bukkit/Spigot 1.12 APIs and avoids NMS.
 */
public class AggroManager {

    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final ConcurrentHashMap<UUID, AggroState> aggroMap = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tickCounter = 0L;

    public AggroManager(JavaPlugin plugin) {
        this(plugin, plugin.getConfig());
    }

    /**
     * Alternate constructor that accepts an explicit configuration object.
     * Useful if your main class stores config differently.
     */
    public AggroManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        // If already running, do nothing
        if (task != null && !task.isCancelled()) {
            return;
        }

        // If trigger == always, initialize existing villagers as aggressive
        if (getConfigBoolean("villager-aggro.enabled", true)
                && "always".equalsIgnoreCase(getConfigString("villager-aggro.trigger", "retaliate"))) {
            for (World w : plugin.getServer().getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e instanceof Villager) {
                        Villager v = (Villager) e;
                        makeAggressive(v, null);
                    }
                }
            }
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                processAggressiveVillagers();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
            task = null;
        }
        aggroMap.clear();
    }

    public void makeAggressive(Villager villager, Player target) {
        if (villager == null || villager.isDead()) return;
        UUID id = villager.getUniqueId();
        AggroState state = new AggroState(id, (target == null) ? null : target.getUniqueId(), tickCounter);
        aggroMap.put(id, state);
        debug("Villager " + id + " marked aggressive toward " + ((target == null) ? "NEAREST" : target.getName()));
    }

    public void removeAggro(Villager villager) {
        if (villager == null) return;
        UUID id = villager.getUniqueId();
        aggroMap.remove(id);
        debug("Villager " + id + " aggro removed");
    }

    public boolean isAggressive(Villager villager) {
        if (villager == null) return false;
        return aggroMap.containsKey(villager.getUniqueId());
    }

    private void processAggressiveVillagers() {
        if (!getConfigBoolean("villager-aggro.enabled", true)) {
            aggroMap.clear();
            return;
        }

        double followRange = getConfigDouble("villager-aggro.follow-range", 32);
        double followSpeed = getConfigDouble("villager-aggro.follow-speed", 0.5);
        double attackRange = getConfigDouble("villager-aggro.attack-range", 1.5);
        double damage = getConfigDouble("villager-aggro.damage-per-attack", 2.0);
        int attackInterval = getConfigInt("villager-aggro.attack-interval-ticks", 10);
        int aggroDuration = getConfigInt("villager-aggro.aggro-duration-ticks", 600);
        String trigger = getConfigString("villager-aggro.trigger", "retaliate");

        Collection<Map.Entry<UUID, AggroState>> entries = aggroMap.entrySet();
        Iterator<Map.Entry<UUID, AggroState>> it = entries.iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, AggroState> entry = it.next();
            AggroState state = entry.getValue();
            UUID villagerId = state.villagerUuid;

            // Find villager entity
            Villager villager = findVillagerByUUID(villagerId);
            if (villager == null || villager.isDead()) {
                it.remove();
                continue;
            }

            // Expire by duration
            if (aggroDuration > 0 && (tickCounter - state.startTick) > aggroDuration) {
                it.remove();
                continue;
            }

            // Resolve target player
            Player target = null;
            if (state.targetPlayerUuid != null) {
                target = plugin.getServer().getPlayer(state.targetPlayerUuid);
                if (target != null && (target.isDead() || !target.isOnline())) {
                    target = null;
                }
            }

            if (target == null) {
                // Try to find nearest player within followRange
                target = findNearestPlayer(villager.getLocation(), followRange);
                if (target == null) {
                    // If trigger is retaliate and no target, skip movement (villager will wait)
                    if (!"always".equalsIgnoreCase(trigger)) {
                        continue;
                    }
                } else {
                    // assign found player as current target
                    state.targetPlayerUuid = target.getUniqueId();
                }
            }

            if (target != null) {
                // Move villager toward player by setting velocity
                try {
                    Location vLoc = villager.getLocation();
                    Location pLoc = target.getLocation();
                    // compute vector from villager to player
                    double dx = pLoc.getX() - vLoc.getX();
                    double dy = pLoc.getY() - vLoc.getY();
                    double dz = pLoc.getZ() - vLoc.getZ();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > 0.0001) {
                        double nx = dx / distance;
                        double ny = dy / distance;
                        double nz = dz / distance;
                        // apply speed multiplier, but keep small Y component so they can step
                        float speed = (float) followSpeed;
                        villager.setVelocity(villager.getVelocity().clone().add(new org.bukkit.util.Vector(nx * speed, Math.max(0.05, ny * speed), nz * speed)));
                    }

                    // Attack if in range and attack interval elapsed
                    if (vLoc.distance(pLoc) <= attackRange) {
                        if ((tickCounter - state.lastAttackTick) >= attackInterval) {
                            // Damage the player and attribute to villager
                            try {
                                target.damage(damage, villager);
                                // simple knockback away from villager
                                org.bukkit.util.Vector kb = target.getLocation().toVector().subtract(villager.getLocation().toVector()).normalize().multiply(0.3);
                                target.setVelocity(kb);
                            } catch (Exception ignored) {
                                // If for some reason damage fails, continue.
                            }
                            state.lastAttackTick = tickCounter;
                        }
                    }
                } catch (Exception ex) {
                    debug("Error processing aggro for villager " + villagerId + ": " + ex.getMessage());
                }
            }
        }
    }

    private Villager findVillagerByUUID(UUID id) {
        // Search loaded worlds for the entity with this UUID
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getUniqueId().equals(id) && e instanceof Villager) {
                    return (Villager) e;
                }
            }
        }
        return null;
    }

    private Player findNearestPlayer(Location loc, double maxDistance) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p == null || p.isDead() || !p.isOnline()) continue;
            if (!p.getWorld().equals(loc.getWorld())) continue;
            double d = p.getLocation().distance(loc);
            if (d <= maxDistance && d < bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private boolean getConfigBoolean(String path, boolean def) {
        try {
            if (config != null) return config.getBoolean(path, def);
            return def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private String getConfigString(String path, String def) {
        try {
            if (config != null) return config.getString(path, def);
            return def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private int getConfigInt(String path, int def) {
        try {
            if (config != null) return config.getInt(path, def);
            return def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private double getConfigDouble(String path, double def) {
        try {
            if (config != null) {
                Object v = config.get(path);
                if (v instanceof Number) {
                    return ((Number) v).doubleValue();
                }
                return config.getDouble(path, def);
            }
            return def;
        } catch (Exception ignored) {
            return def;
        }
    }

    private void debug(String msg) {
        if (getConfigBoolean("villager-aggro.debug", false)) {
            plugin.getLogger().info("[AggroManager] " + msg);
        }
    }

    /**
     * Simple POJO for storing aggro state.
     */
    private static class AggroState {
        final UUID villagerUuid;
        UUID targetPlayerUuid; // nullable - nearest player used when null
        final long startTick;
        long lastAttackTick;

        AggroState(UUID villagerUuid, UUID targetPlayerUuid, long startTick) {
            this.villagerUuid = villagerUuid;
            this.targetPlayerUuid = targetPlayerUuid;
            this.startTick = startTick;
            this.lastAttackTick = startTick - 100; // allow immediate attack if necessary
        }
    }
}
