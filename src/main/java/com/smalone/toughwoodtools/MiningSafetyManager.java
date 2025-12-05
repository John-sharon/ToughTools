package com.smalone.toughwoodtools;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;

public class MiningSafetyManager {

    private final ToughTools plugin;
    private final Set<Material> collapseWhitelist;
    private static final EnumSet<Material> ORE_TYPES = EnumSet.of(
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DIAMOND_ORE,
            Material.LAPIS_ORE
    );
    private static final int REQUIRED_AIR_RUN = 6;
    private static final int MAX_HORIZONTAL_HEIGHT = 6;
    private static final int MAX_HORIZONTAL_DISTANCE = 6;
    private static final int MAX_FALLING_BLOCKS = 90;

    public MiningSafetyManager(ToughTools plugin, Set<Material> collapseWhitelist) {
        this.plugin = plugin;
        this.collapseWhitelist = collapseWhitelist == null ? EnumSet.noneOf(Material.class) : collapseWhitelist;
    }

    public boolean handleShaftAndTunnel(Block broken, Player player) {
        Material brokenType = broken.getType();
        if (!collapseWhitelist.contains(brokenType)) {
            return false;
        }

        // Allow ores to be mined without vertical collapse penalties
        if (ORE_TYPES.contains(brokenType)) {
            World world = broken.getWorld();
            Location origin = broken.getLocation();
            boolean horizontalTriggered = triggerHorizontalIfNeeded(world, origin, Axis.X, player)
                    || triggerHorizontalIfNeeded(world, origin, Axis.Z, player);
            return horizontalTriggered;
        }

        World world = broken.getWorld();
        Location origin = broken.getLocation();

        if (isDeepUnderground(origin, 6) && isCaveCeilingBreak(broken)) {
            triggerCaveCeilingCollapse(world, origin, brokenType);
            return true;
        }

        if (triggerVerticalIfNeeded(world, origin, player)) {
            return true;
        }

        if (triggerHorizontalIfNeeded(world, origin, Axis.X, player)) {
            return true;
        }

        return triggerHorizontalIfNeeded(world, origin, Axis.Z, player);
    }

    private boolean triggerVerticalIfNeeded(World world, Location origin, Player player) {
        if (!isDeepUnderground(origin, 6)) {
            return false;
        }

        boolean layer1Stable = isLayerStable(world, origin, 1, 1, 6);
        boolean layer2Stable = isLayerStable(world, origin, 2, 2, 17);
        boolean layer3Stable = isLayerStable(world, origin, 3, 3, 33);
        boolean layer4Stable = isLayerStable(world, origin, 4, 4, 55);

        if (layer1Stable && layer2Stable && layer3Stable && layer4Stable) {
            return false;
        }

        triggerSlopeCollapse(world, origin);

        if (plugin.isDebugCaveIns() && player != null) {
            player.sendMessage(ChatColor.GRAY + "[DEBUG] " + ChatColor.YELLOW
                    + "Slope collapse triggered at "
                    + origin.getBlockX() + ", " + origin.getBlockY() + ", " + origin.getBlockZ()
                    + " (layers: "
                    + "L1=" + layer1Stable + ", "
                    + "L2=" + layer2Stable + ", "
                    + "L3=" + layer3Stable + ", "
                    + "L4=" + layer4Stable + ")");
        }

        return true;
    }

    private boolean triggerHorizontalIfNeeded(World world, Location origin, Axis axis, Player player) {
        HorizontalRun run = countHorizontalAirRun(world, origin, axis, REQUIRED_AIR_RUN);
        if (run.total < REQUIRED_AIR_RUN) {
            return false;
        }

        int direction = run.positive >= run.negative ? 1 : -1;
        boolean hasSupport = hasWoodSupport(world, origin, axis, MAX_HORIZONTAL_DISTANCE, direction);
        if (hasSupport) {
            return false;
        }

        triggerTunnelCaveIn(world, origin, axis, direction, Math.min(run.total, MAX_HORIZONTAL_DISTANCE), player, hasSupport);
        return true;
    }

    private boolean isDeepUnderground(Location loc, int minDepth) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        int surfaceY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        int y = loc.getBlockY();
        return y <= surfaceY - minDepth;
    }

    private boolean isCaveCeilingBreak(Block broken) {
        World world = broken.getWorld();
        Location loc = broken.getLocation();
        Block below = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        return below.getType() == Material.AIR;
    }

    private boolean isLayerStable(World world, Location origin, int yOffset, int radius, int requiredAir) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY() + yOffset;
        int oz = origin.getBlockZ();

        int airCount = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block block = world.getBlockAt(ox + dx, oy, oz + dz);
                if (block.getType() == Material.AIR) {
                    airCount++;
                }
            }
        }

        return airCount >= requiredAir;
    }

    private HorizontalRun countHorizontalAirRun(World world, Location origin, Axis axis, int maxLength) {
        int positive = countDirectionalAir(world, origin, axis, maxLength, 1);
        int negative = countDirectionalAir(world, origin, axis, maxLength, -1);
        return new HorizontalRun(positive, negative, positive + negative + 1);
    }

    private int countDirectionalAir(World world, Location origin, Axis axis, int maxLength, int direction) {
        int count = 0;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= maxLength; d++) {
            int x = axis == Axis.X ? ox + (direction * d) : ox;
            int z = axis == Axis.Z ? oz + (direction * d) : oz;
            Block block = world.getBlockAt(x, oy, z);
            if (block.getType() != Material.AIR) {
                break;
            }
            count++;
        }
        return count;
    }

    private boolean hasWoodSupport(World world, Location origin, Axis axis, int maxDistance, int direction) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= maxDistance; d++) {
            int centerX = axis == Axis.X ? ox + (direction * d) : ox;
            int centerZ = axis == Axis.Z ? oz + (direction * d) : oz;

            for (int dy = -1; dy <= 1; dy++) {
                for (int offset = -1; offset <= 1; offset++) {
                    int x = axis == Axis.X ? centerX : centerX + offset;
                    int z = axis == Axis.Z ? centerZ : centerZ + offset;
                    Block block = world.getBlockAt(x, oy + dy, z);
                    if (block.getType() == Material.WOOD) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void triggerCaveCeilingCollapse(World world, Location origin, Material fillType) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        int spawned = 0;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int airCount = 0;

                for (int dy = 1; dy <= 5; dy++) {
                    int y = oy - dy;
                    if (y < 0) {
                        break;
                    }
                    Block target = world.getBlockAt(ox + dx, y, oz + dz);
                    if (target.getType() == Material.AIR) {
                        airCount++;
                    }
                }

                for (int i = 0; i < airCount; i++) {
                    if (spawned >= MAX_FALLING_BLOCKS) {
                        return;
                    }
                    Location spawnLoc = new Location(world, ox + dx + 0.5D, oy - 0.5D + i, oz + dz + 0.5D);
                    spawnFallingBlock(world, spawnLoc, fillType, (byte) 0);
                    spawned++;
                }
            }
        }
    }

    private void triggerSlopeCollapse(World world, Location origin) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        int spawned = 0;

        for (int layer = 1; layer <= 4; layer++) {
            int radius = layer;
            int y = oy + layer;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (spawned >= MAX_FALLING_BLOCKS) {
                        return;
                    }
                    Block block = world.getBlockAt(ox + dx, y, oz + dz);
                    Material type = block.getType();
                    if (type == Material.AIR) {
                        continue;
                    }
                    if (!collapseWhitelist.contains(type)) {
                        continue;
                    }

                    byte data = block.getData();
                    block.setType(Material.AIR);
                    Location spawnLoc = new Location(world, ox + dx + 0.5D, y + 0.0D, oz + dz + 0.5D);
                    spawnFallingBlock(world, spawnLoc, type, data);
                    spawned++;
                }
            }
        }
    }

    private void triggerTunnelCaveIn(World world, Location origin, Axis axis, int direction, int airRunLength, Player player, boolean hasSupport) {
        int spawned = 0;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int d = 1; d <= airRunLength; d++) {
            int baseX = axis == Axis.X ? ox + (direction * d) : ox;
            int baseZ = axis == Axis.Z ? oz + (direction * d) : oz;

            for (int side = -1; side <= 1; side++) {
                int x = axis == Axis.X ? baseX : baseX + side;
                int z = axis == Axis.Z ? baseZ : baseZ + side;
                spawned = collapseColumn(world, x, oy, z, spawned);
                if (spawned >= MAX_FALLING_BLOCKS) {
                    return;
                }
            }
        }

        if (plugin.isDebugCaveIns() && player != null) {
            player.sendMessage(ChatColor.GRAY + "[DEBUG] " + ChatColor.YELLOW
                    + "Tunnel cave-in triggered along axis " + axis.name()
                    + " at " + origin.getBlockX() + ", " + origin.getBlockY() + ", " + origin.getBlockZ()
                    + " (airRun=" + airRunLength + ", supportFound=" + hasSupport + ")");
        }
    }

    private int collapseColumn(World world, int x, int baseY, int z, int spawnedSoFar) {
        for (int dy = 1; dy <= MAX_HORIZONTAL_HEIGHT; dy++) {
            if (spawnedSoFar >= MAX_FALLING_BLOCKS) {
                return spawnedSoFar;
            }
            int y = baseY + dy;
            if (y >= world.getMaxHeight()) {
                return spawnedSoFar;
            }
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.AIR) {
                continue;
            }

            if (!collapseWhitelist.contains(type)) {
                continue;
            }

            byte data = block.getData();
            block.setType(Material.AIR);
            spawnFallingBlock(world, new Location(world, x + 0.5D, y + 0.0D, z + 0.5D), type, data);
            spawnedSoFar++;
        }
        return spawnedSoFar;
    }

    private void spawnFallingBlock(World world, Location location, Material type, byte data) {
        FallingBlock falling = world.spawnFallingBlock(location, type, data);
        try {
            falling.setDropItem(false);
            falling.setHurtEntities(false);
        } catch (NoSuchMethodError ignored) {
            // Older API revisions may not support these toggles; gravity is sufficient.
        }
    }

    public enum Axis {
        X,
        Z
    }

    private static class HorizontalRun {
        final int positive;
        final int negative;
        final int total;

        HorizontalRun(int positive, int negative, int total) {
            this.positive = positive;
            this.negative = negative;
            this.total = total;
        }
    }
}
