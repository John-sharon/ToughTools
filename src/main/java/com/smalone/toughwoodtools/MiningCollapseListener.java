package com.smalone.toughwoodtools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Simulate ceiling collapses by turning overhead blocks into temporary falling blocks
 * when no nearby supports remain.
 */
public class MiningCollapseListener implements Listener {

    private final ToughTools plugin;
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();
    private final int collapseHeight;
    private final long restoreDelayTicks;
    private final long cooldownMillis;

    public MiningCollapseListener(ToughTools plugin) {
        this.plugin = plugin;
        this.collapseHeight = plugin.getConfig().getInt("collapse-height", 6);
        this.restoreDelayTicks = plugin.getConfig().getLong("collapse-restore-delay", 200L);
        this.cooldownMillis = plugin.getConfig().getLong("collapse-cooldown-ms", 2000L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (isProtected(broken)) {
            return;
        }

        Player player = event.getPlayer();
        if (isCoolingDown(player, broken.getLocation())) {
            return;
        }

        if (hasSupports(broken)) {
            return;
        }

        triggerCollapse(broken);
        markCooldown(player, broken.getLocation());
    }

    private boolean hasSupports(Block center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block neighbor = center.getRelative(dx, 0, dz);
                if (isSupportBlock(neighbor)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSupportBlock(Block block) {
        Material type = block.getType();
        return type != Material.AIR && !isLiquid(type) && type.isSolid() && !isProtected(block);
    }

    private void triggerCollapse(Block origin) {
        World world = origin.getWorld();
        List<BlockState> toRestore = new ArrayList<BlockState>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < collapseHeight; dy++) {
                    Block target = origin.getRelative(dx, dy, dz);
                    if (isProtected(target)) {
                        continue;
                    }

                    Material type = target.getType();
                    if (type == Material.AIR || isLiquid(type)) {
                        continue;
                    }

                    BlockState snapshot = target.getState();
                    toRestore.add(snapshot);

                    byte data = target.getData();
                    target.setType(Material.AIR);

                    FallingBlock falling = world.spawnFallingBlock(target.getLocation().add(0.5D, 0.0D, 0.5D), type, data);
                    try {
                        falling.setDropItem(false);
                        falling.setHurtEntities(false);
                    } catch (NoSuchMethodError ignored) {
                        // Older API revisions may not support these toggles; gravity is sufficient.
                    }
                }
            }
        }

        scheduleRestoration(toRestore);
    }

    private void scheduleRestoration(final List<BlockState> states) {
        if (states.isEmpty() || restoreDelayTicks <= 0L) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                for (BlockState state : states) {
                    Block live = state.getBlock();
                    if (live.getType() == Material.AIR) {
                        try {
                            state.update(true, false);
                        } catch (Exception ignored) {
                            // If the block cannot be restored, skip silently.
                        }
                    }
                }
            }
        }, restoreDelayTicks);
    }

    private boolean isProtected(Block block) {
        Material type = block.getType();
        return type == Material.BEDROCK || isLiquid(type);
    }

    private boolean isLiquid(Material type) {
        return type == Material.WATER || type == Material.STATIONARY_WATER
                || type == Material.LAVA || type == Material.STATIONARY_LAVA;
    }

    private boolean isCoolingDown(Player player, Location location) {
        String key = buildCooldownKey(player.getUniqueId(), location);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        return last != null && now - last < cooldownMillis;
    }

    private void markCooldown(Player player, Location location) {
        String key = buildCooldownKey(player.getUniqueId(), location);
        cooldowns.put(key, System.currentTimeMillis());
    }

    private String buildCooldownKey(UUID uuid, Location location) {
        return uuid.toString() + ":" + location.getWorld().getName() + ":" + location.getBlockX()
                + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
