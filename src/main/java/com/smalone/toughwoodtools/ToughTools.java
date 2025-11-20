package com.smalone.toughwoodtools;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class ToughTools extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getConfig().addDefault("collapse-height", 6);
        getConfig().addDefault("collapse-restore-delay", 200L);
        getConfig().addDefault("collapse-cooldown-ms", 2000L);
        getConfig().options().copyDefaults(true);
        saveConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new InstantWheatListener(this), this);
        getServer().getPluginManager().registerEvents(new MiningCollapseListener(this), this);
        getLogger().info("InstantWheatListener enabled: wheat matures in ~1s after planting.");
        getLogger().info("MiningCollapseListener enabled: unstable ceilings may collapse.");
        getLogger().info("ToughTools enabled: empowering wooden pickaxes and axes.");
    }

    /**
     * Prevent empowered wooden tools from losing durability.
     */
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!isWoodTool(item)) {
            return;
        }

        makePristine(item);
        event.setDamage(0);
    }

    /**
     * Instantly break blocks when using empowered wooden tools.
     */
    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        ItemStack item = event.getItemInHand();
        if (!isWoodTool(item)) {
            return;
        }

        ensureEnchants(item);
        event.setInstaBreak(true);
    }

    /**
     * Greatly increase melee damage dealt with empowered wooden tools.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player)) {
            return;
        }

        Player player = (Player) damager;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWoodTool(held)) {
            return;
        }

        double baseDamage = event.getDamage();
        double multiplier = 10.0D;
        event.setDamage(baseDamage * multiplier);

        ensureEnchants(held);
        makePristine(held);
    }

    /**
     * Automatically empower freshly crafted wooden tools.
     */
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (!isWoodTool(result)) {
            return;
        }

        ensureEnchants(result);
        makePristine(result);
        event.setCurrentItem(result);
    }

    private boolean isWoodTool(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.WOOD_PICKAXE || type == Material.WOOD_AXE;
    }

    private void ensureEnchants(ItemStack item) {
        if (item == null) {
            return;
        }
        try {
            item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 10);
            item.addUnsafeEnchantment(Enchantment.DURABILITY, 10);
            item.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 10);
            item.addUnsafeEnchantment(Enchantment.KNOCKBACK, 5);
            item.addUnsafeEnchantment(Enchantment.LOOT_BONUS_BLOCKS, 5);
        } catch (Exception ignored) {
            // Some servers may block unsafe enchants; the core buffs still apply.
        }
    }

    private void makePristine(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            } catch (NoSuchMethodError ignored) {
                // Older API revisions may not expose setUnbreakable; durability reset handles it.
            }
        }

        try {
            item.setDurability((short) 0);
        } catch (Exception ignored) {
            // Item types that cannot have durability simply ignore this.
        }
    }

    @SuppressWarnings("unused")
    private void replaceHeldItem(Player player, ItemStack replacement) {
        if (player == null || replacement == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setItemInMainHand(replacement);
    }
}
