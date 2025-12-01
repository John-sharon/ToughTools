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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class GameplayListener implements Listener {

    private final ToughTools plugin;
    private final Random random = new Random();
    private final Set<String> platformBlocks = new HashSet<String>();
    private Location spectatorCenter;

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
        if (event.isBedSpawn()) {
            return;
        }

        World world = getMainWorld();
        if (world == null) {
            return;
        }

        Location platform = ensureSpectatorPlatform(world);
        if (platform != null) {
            event.setRespawnLocation(platform);
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

    private Location ensureSpectatorPlatform(World world) {
        if (spectatorCenter != null) {
            return spectatorCenter.clone();
        }

        Location spawn = world.getSpawnLocation();
        int yBase = Math.max(10, world.getMaxHeight() - 5);
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setGlass(world, centerX + dx, yBase, centerZ + dz);
                setGlass(world, centerX + dx, yBase + 3, centerZ + dz);
            }
        }

        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (dx == -1 || dx == 1 || dz == -1 || dz == 1) {
                        setGlass(world, centerX + dx, yBase + dy, centerZ + dz);
                    }
                }
            }
        }

        spectatorCenter = new Location(world, centerX + 0.5D, yBase + 1, centerZ + 0.5D);
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
