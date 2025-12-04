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
    private static final int SPECTATOR_PLATFORM_Y = 120;
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
        Location bedSpawn = event.getPlayer().getBedSpawnLocation();
        if (bedSpawn != null) {
            event.setRespawnLocation(bedSpawn);
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

    private Location ensureSpectatorPlatform(World world) {
        if (spectatorCenter != null) {
            return spectatorCenter.clone();
        }

        Location spawn = world.getSpawnLocation();
        int yBase = Math.max(1, Math.min(SPECTATOR_PLATFORM_Y, world.getMaxHeight() - SPECTATOR_PLATFORM_HEIGHT));
        int centerX = spawn.getBlockX();
        int centerZ = spawn.getBlockZ();

        int startX = centerX - SPECTATOR_PLATFORM_HALF_SIZE;
        int endX = centerX + SPECTATOR_PLATFORM_HALF_SIZE - 1;
        int startZ = centerZ - SPECTATOR_PLATFORM_HALF_SIZE;
        int endZ = centerZ + SPECTATOR_PLATFORM_HALF_SIZE - 1;

        for (int dy = 0; dy < SPECTATOR_PLATFORM_HEIGHT; dy++) {
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    setGlass(world, x, yBase + dy, z);
                }
            }
        }

        spectatorCenter = new Location(world, centerX + 0.5D, yBase + (SPECTATOR_PLATFORM_HEIGHT / 2), centerZ + 0.5D);
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
