package com.example.fastwheat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.event.block.Action;
import org.bukkit.material.Crops;
import org.bukkit.material.CropState;

/**
 * A small Spigot plugin that makes planted wheat seeds mature fully one second
 * after they are planted.  This works by listening for a player interaction
 * when they right‑click farmland with seeds.  A delayed task runs one
 * second later (20 ticks) and updates the placed crop to its maximum
 * growth stage.  The implementation attempts to support both modern
 * Spigot APIs (where crops implement {@link Ageable}) and older APIs
 * (where crops are represented by the {@link org.bukkit.material.Crops}
 * MaterialData).
 */
public final class FastWheatGrowth extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register event listener when the plugin is enabled
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FastWheatGrowth has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FastWheatGrowth has been disabled.");
    }

    /**
     * Handles player interactions to detect when seeds are planted.  When a
     * player right‑clicks farmland with seeds in their hand, schedule a
     * delayed task that will mature the resulting wheat crop after 20 ticks.
     *
     * @param event the {@link PlayerInteractEvent}
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only react to right‑clicks on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Only proceed if an item is in the player's hand
        if (event.getItem() == null) {
            return;
        }
        Material item = event.getItem().getType();
        // Accept both the modern and legacy names for wheat seeds
        boolean isSeed = (item == Material.WHEAT_SEEDS || item.name().equalsIgnoreCase("SEEDS"));
        if (!isSeed) {
            return;
        }
        // Determine the block where the seeds will be placed (above the clicked block)
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        BlockFace face = event.getBlockFace();
        Block cropBlock = clickedBlock.getRelative(face);

        // Schedule a delayed task to set the crop to full growth after 20 ticks
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.runTaskLater(this, () -> {
            try {
                // Re‑verify that the block is still a crop (wheat) before changing it
                Material currentType = cropBlock.getType();
                // Modern names use WHEAT for the block; legacy uses CROPS
                if (currentType != Material.WHEAT && !currentType.name().equalsIgnoreCase("CROPS")) {
                    return;
                }
                BlockState state = cropBlock.getState();
                BlockData data = null;
                try {
                    // Attempt to use the Ageable interface if present
                    data = state.getBlockData();
                    if (data instanceof Ageable) {
                        Ageable ageable = (Ageable) data;
                        ageable.setAge(ageable.getMaximumAge());
                        state.setBlockData(ageable);
                        state.update(true, true);
                        return;
                    }
                } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
                    // Ignore – fall back to legacy MaterialData below
                }
                // Legacy API: use Crops MaterialData to set the crop state to ripe
                org.bukkit.material.MaterialData md = state.getData();
                if (md instanceof Crops) {
                    Crops crops = (Crops) md;
                    crops.setState(CropState.RIPE);
                    state.setData(crops);
                    state.update(true, true);
                }
            } catch (Exception ex) {
                getLogger().warning("Failed to grow wheat instantly: " + ex.getMessage());
            }
        }, 20L);
    }
}