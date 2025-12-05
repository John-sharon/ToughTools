package com.smalone.toughwoodtools;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class GameplayListener implements Listener {

    private final ToughTools plugin;
    private final Random random = new Random();
    private final Set<String> platformBlocks = new HashSet<String>();
    private Location spectatorCenter;
    private static final int SPECTATOR_PLATFORM_Y = 110;
    private static final int SPECTATOR_PLATFORM_HALF_SIZE = 25; // results in 50x50 footprint
    private static final int SPECTATOR_PLATFORM_HEIGHT = 3;

    public GameplayListener(ToughTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }

        World world = getMainWorld();
        if (world != null) {
            Location spawn = world.getSpawnLocation();
            Location target = findRandomSpawn(world, spawn);
            if (target != null) {
                player.teleport(target);
            }
        }

        giveStartingBed(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location bedSpawn = player.getBedSpawnLocation();
        if (bedSpawn != null) {
            Block bedBlock = findNearbyBedBlock(bedSpawn, 2);
            if (bedBlock != null) {
                event.setRespawnLocation(bedSpawn);
                return;
            }
        }

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        Location platform = ensureSpectatorPlatform(world);
        if (platform != null) {
            event.setRespawnLocation(platform);
            giveSpectatorKit(player);
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) {
            return;
        }
        if (result.getType() != Material.BED) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player) {
            ((Player) event.getWhoClicked()).sendMessage("Beds cannot be crafted on this server.");
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        EntityType type = event.getEntityType();
        SpawnReason reason = event.getSpawnReason();

        if (type == EntityType.VILLAGER && isVillageRelatedSpawn(reason)) {
            event.setCancelled(true);
            return;
        }

        if (type == EntityType.IRON_GOLEM && isVillageRelatedSpawn(reason)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isPlatformBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BED_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Location bedLocation = clicked.getLocation().add(0.5D, 0.5D, 0.5D);
        try {
            player.setBedSpawnLocation(bedLocation, true);
        } catch (NoSuchMethodError ignored) {
            player.setBedSpawnLocation(bedLocation);
        }
        player.sendMessage("Your spawn point has been set to this bed.");
    }

    private boolean isPlatformBlock(Block block) {
        String key = buildKey(block.getLocation());
        return platformBlocks.contains(key);
    }

    private Location findRandomSpawn(World world, Location center) {
        Location fallback = center.clone().add(0.5D, 0.0D, 0.5D);
        for (int attempt = 0; attempt < 10; attempt++) {
            double radius = random.nextDouble() * 200.0D;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = center.getBlockX() + (int) Math.round(radius * Math.cos(angle));
            int z = center.getBlockZ() + (int) Math.round(radius * Math.sin(angle));

            Block highest = world.getHighestBlockAt(x, z);
            Material type = highest.getType();
            if (type == Material.WATER || type == Material.STATIONARY_WATER
                    || type == Material.LAVA || type == Material.STATIONARY_LAVA) {
                continue;
            }

            return highest.getLocation().add(0.5D, 1.0D, 0.5D);
        }
        return fallback;
    }

    private void giveStartingBed(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.addItem(new ItemStack(Material.BED, 1));
    }

    private Block findNearbyBedBlock(Location loc, int radius) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        World world = loc.getWorld();
        int ox = loc.getBlockX();
        int oy = loc.getBlockY();
        int oz = loc.getBlockZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (block.getType() == Material.BED_BLOCK) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private void giveSpectatorKit(Player player) {
        if (player == null) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.BOW, 1));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 64));

        try {
            player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD, 1));
        } catch (NoSuchMethodError ignored) {
            // Off-hand may not be available on some server versions.
        }
    }

    private Location ensureSpectatorPlatform(World world) {
        Location spawn = world.getSpawnLocation();
        int yFloor = Math.max(1, Math.min(SPECTATOR_PLATFORM_Y, world.getMaxHeight() - SPECTATOR_PLATFORM_HEIGHT));
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();

        if (spectatorCenter != null) {
            return spectatorCenter.clone();
        }

        int startX = centerX - SPECTATOR_PLATFORM_HALF_SIZE;
        int endX = centerX + SPECTATOR_PLATFORM_HALF_SIZE - 1;
        int startZ = centerZ - SPECTATOR_PLATFORM_HALF_SIZE;
        int endZ = centerZ + SPECTATOR_PLATFORM_HALF_SIZE - 1;

        int yRoof = yFloor + SPECTATOR_PLATFORM_HEIGHT - 1;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                setGlass(world, x, yFloor, z);
            }
        }

        for (int y = yFloor; y <= yRoof; y++) {
            for (int x = startX; x <= endX; x++) {
                setGlass(world, x, y, startZ);
                setGlass(world, x, y, endZ);
            }
            for (int z = startZ; z <= endZ; z++) {
                setGlass(world, startX, y, z);
                setGlass(world, endX, y, z);
            }
        }

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                setGlass(world, x, yRoof, z);
            }
        }

        spectatorCenter = new Location(world, centerX + 0.5D, yFloor + 1, centerZ + 0.5D);
        return spectatorCenter.clone();
    }

    private void setGlass(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.GLASS);
        platformBlocks.add(buildKey(block.getLocation()));
    }

    private boolean isVillageRelatedSpawn(SpawnReason reason) {
        return reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN
                || reason == SpawnReason.VILLAGE_DEFENSE || reason == SpawnReason.VILLAGE_INVASION;
    }

    private World getMainWorld() {
        World world = Bukkit.getWorld("world");
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }
        return world;
    }

    private String buildKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }
}
