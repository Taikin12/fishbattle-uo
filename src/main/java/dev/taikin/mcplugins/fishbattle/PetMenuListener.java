package dev.taikin.mcplugins.fishbattle;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PetMenuListener implements Listener {

    private final org.bukkit.plugin.Plugin plugin;
    private final PetMenu menu;

    public PetMenuListener(org.bukkit.plugin.Plugin plugin, PetMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!menu.ownsMenu(event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !menu.ownsMenu(event.getClickedInventory())) {
            return;
        }
        menu.handleClick(player, event.getRawSlot());
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> menu.refreshIfOpen(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (menu.ownsMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (menu.ownsMenu(event.getView().getTopInventory())) {
            menu.closeMenuOnly(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menu.close(event.getPlayer());
    }
}
