package dev.taikin.mcplugins.fishbattle;

import java.time.Duration;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class BattleEffects {

    private final org.bukkit.plugin.Plugin plugin;

    BattleEffects(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    void duelTitle(Player player, Component title, Component subtitle) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(400))));
    }

    void countdownTitle(Player player, String text, int staySeconds) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                Component.text(text, NamedTextColor.GOLD),
                Component.text("左クリックを連打しろ！", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200),
                        Duration.ofSeconds(Math.max(1, staySeconds)), Duration.ofMillis(300))));
    }

    void ownHand(Player player, Rps hand) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                Component.text(hand.emoji(), hand.color()),
                Component.text("あなたの手: " + hand.label(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(1),
                        Duration.ofMillis(200))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
    }

    void resultSound(Player player, Boolean win) {
        if (player == null) {
            return;
        }
        if (win == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        } else if (win) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.6f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
        }
    }

    void countdownNote(Player player) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        }
    }

    void flashAttackCharge(BattleSession session, Player viewer, double charge, int clicks) {
        viewer.setExp(Math.min(0.999f, (float) charge));
        viewer.setLevel(clicks);
        viewer.sendActionBar(Component.text("攻撃！ 気合 " + Math.round(charge * 100) + "%",
                NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.phase() != BattleSession.Phase.FINISHED) {
                BattleHud.update(session, Bukkit.getPlayer(viewer.getUniqueId()));
            }
        }, 40L);
    }

    void attack(BattleSession session, UUID attackerId, UUID defenderId, Entity target,
            double multiplier, boolean special, boolean superSpecial) {
        if (target == null) {
            return;
        }
        Location location = target.getLocation().add(0, 0.6, 0);
        var world = location.getWorld();
        if (world == null) {
            return;
        }

        int count = superSpecial ? 60 : special ? 40 : 20;
        Particle particle = superSpecial ? Particle.TOTEM_OF_UNDYING
                : special ? Particle.EXPLOSION_EMITTER
                : multiplier > 1.0 ? Particle.CRIT : Particle.DAMAGE_INDICATOR;
        world.spawnParticle(particle, location, count, 0.6, 0.6, 0.6, 0.15);
        world.spawnParticle(Particle.SWEEP_ATTACK, location, 3, 0.8, 0.5, 0.8, 0.0);

        if (superSpecial) {
            world.strikeLightningEffect(location);
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        } else if (special) {
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        } else {
            world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.1f);
        }

        Player attacker = Bukkit.getPlayer(attackerId);
        if (attacker != null) {
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    attacker.getLocation().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0.05);
            attacker.sendHurtAnimation(0f);
        }
    }
}
