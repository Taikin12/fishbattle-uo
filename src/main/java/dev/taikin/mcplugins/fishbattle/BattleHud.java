package dev.taikin.mcplugins.fishbattle;

import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class BattleHud {

    private BattleHud() {
    }

    static void init(BattleSession session, Player viewer) {
        if (viewer == null) {
            return;
        }
        UUID id = viewer.getUniqueId();
        session.setSavedExp(id, viewer.getExp());
        session.setSavedLevel(id, viewer.getLevel());

        Fish self = session.fishOf(id);
        Fish opponent = session.fishOf(session.opponentOf(id));

        BossBar oppBar = BossBar.bossBar(opponentLabel(session, id, opponent, false),
                hpRatio(opponent), BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        session.setOpponentBar(id, oppBar);
        BossBar selfBar = BossBar.bossBar(selfLabel(session, id, self, false),
                hpRatio(self), BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        session.setSelfBar(id, selfBar);

        viewer.showBossBar(oppBar);
        viewer.showBossBar(selfBar);
    }

    static void update(BattleSession session, Player viewer) {
        if (viewer == null
                || (session.phase() == BattleSession.Phase.FINISHED && !session.resultGrace())) {
            return;
        }
        UUID selfId = viewer.getUniqueId();
        UUID opponentId = session.opponentOf(selfId);
        boolean mashing = session.phase() == BattleSession.Phase.MASHING;
        boolean finished = session.phase() == BattleSession.Phase.FINISHED;

        Fish self = session.fishOf(selfId);
        Fish opponent = session.fishOf(opponentId);

        updateExp(viewer, session, self, mashing);

        BossBar oppBar = session.opponentBar(selfId);
        if (oppBar != null) {
            oppBar.progress(finished || !mashing ? hpRatio(opponent) : (float) session.charge(opponentId));
            oppBar.name(finished ? hpLabel("相手", opponent, NamedTextColor.RED)
                    : opponentLabel(session, selfId, opponent, mashing));
        }
        BossBar selfBar = session.selfBar(selfId);
        if (selfBar != null) {
            selfBar.progress(finished || !mashing ? hpRatio(self) : (float) session.charge(selfId));
            selfBar.name(finished ? hpLabel("自分", self, NamedTextColor.GREEN)
                    : selfLabel(session, selfId, self, mashing));
        }

        if (oppBar != null) {
            viewer.showBossBar(oppBar);
        }
        if (selfBar != null) {
            viewer.showBossBar(selfBar);
        }
    }

    static void stop(BattleSession session) {
        if (session.meterTask() != null) {
            session.meterTask().cancel();
            session.setMeterTask(null);
        }
        stopViewer(session, org.bukkit.Bukkit.getPlayer(session.playerA()));
        stopViewer(session, org.bukkit.Bukkit.getPlayer(session.playerB()));
    }

    private static void updateExp(Player viewer, BattleSession session, Fish self, boolean mashing) {
        if (!showsExpBar(viewer)) {
            return;
        }
        if (mashing) {
            float charge = (float) Math.min(1.0, session.charge(viewer.getUniqueId()));
            viewer.setExp(Math.min(0.999f, charge));
            viewer.setLevel(Math.round(charge * 100));
        } else {
            viewer.setExp(Math.max(0f, Math.min(0.999f, hpRatio(self))));
            viewer.setLevel(self.hp());
        }
    }

    private static void stopViewer(BattleSession session, Player viewer) {
        if (viewer == null) {
            return;
        }
        UUID id = viewer.getUniqueId();
        viewer.setExp(Math.min(0.999f, session.savedExp(id)));
        viewer.setLevel(session.savedLevel(id));
        BossBar oppBar = session.opponentBar(id);
        if (oppBar != null) {
            viewer.hideBossBar(oppBar);
            session.setOpponentBar(id, null);
        }
        BossBar selfBar = session.selfBar(id);
        if (selfBar != null) {
            viewer.hideBossBar(selfBar);
            session.setSelfBar(id, null);
        }
    }

    private static boolean showsExpBar(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE || mode == GameMode.SPECTATOR;
    }

    private static Component selfLabel(BattleSession session, UUID viewerId, Fish self, boolean mashing) {
        if (mashing) {
            return Component.text("自分 気合 " + Math.round(session.charge(viewerId) * 100) + "%",
                    NamedTextColor.GREEN);
        }
        return hpLabel("自分", self, NamedTextColor.GREEN);
    }

    private static Component opponentLabel(BattleSession session, UUID viewerId, Fish opponent,
            boolean mashing) {
        UUID opponentId = session.opponentOf(viewerId);
        if (mashing) {
            return Component.text(session.nameOf(opponentId) + "  気合 "
                    + Math.round(session.charge(opponentId) * 100) + "%", NamedTextColor.RED);
        }
        String who = session.nameOf(opponentId);
        return Component.text(who + "  " + opponent.displayName() + "  HP "
                + opponent.hp() + "/" + opponent.maxHp(),
                opponent.hp() * 3 <= opponent.maxHp() ? NamedTextColor.DARK_RED : NamedTextColor.RED);
    }

    private static Component hpLabel(String prefix, Fish fish, NamedTextColor color) {
        return Component.text(prefix + " " + fish.displayName() + "  HP "
                + fish.hp() + "/" + fish.maxHp(), color);
    }

    private static float hpRatio(Fish fish) {
        if (fish == null || fish.maxHp() <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, (float) fish.hp() / fish.maxHp()));
    }
}
