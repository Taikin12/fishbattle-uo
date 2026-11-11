package dev.taikin.mcplugins.fishbattle;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.EntitiesLoadEvent;

public final class BattleListener implements Listener {

    private final BattleManager battles;
    private final FishMotion motion;

    public BattleListener(BattleManager battles, FishMotion motion) {
        this.battles = battles;
        this.motion = motion;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        battles.mash(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (battles.isMashing(event.getPlayer())) {
            event.setCancelled(true);
            battles.mash(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (battles.isMashing(event.getPlayer())) {
            event.setCancelled(true);
            battles.mash(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Player player) {
            battles.mash(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!battles.isChoosing(event.getPlayer().getUniqueId())) {
            return;
        }
        battles.selectBySlot(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!battles.isChoosing(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        battles.cyclePreview(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (battles.confirmPreview(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        battles.endQuietly(event.getPlayer());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (var entity : event.getEntities()) {
            if (FishMotion.hasData(entity)) {
                motion.track(entity);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (FishMotion.hasData(event.getEntity())) {
            event.setCancelled(true);
        }
        if (event.getEntity().getPersistentDataContainer()
                .has(FishKeys.DEMO, org.bukkit.persistence.PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
