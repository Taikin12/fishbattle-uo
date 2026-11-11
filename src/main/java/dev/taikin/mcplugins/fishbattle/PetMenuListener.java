package dev.taikin.mcplugins.fishbattle;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!menu.isWaitingForRename(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                menu.cancelRename(player);
                player.sendMessage(net.kyori.adventure.text.Component.text("名前の変更を中止しました。",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
                return;
            }
            menu.completeRename(player, message);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menu.close(event.getPlayer());
    }
}
