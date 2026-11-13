package dev.taikin.mcplugins.fishbattle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class BattleManager {

    private static final long MASH_MILLIS = 3000L;
    private static final int MASH_TICKS = (int) (MASH_MILLIS / 50L);
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int RESULT_HOLD_TICKS = 60;
    private static final int ATTACK_DELAY_TICKS = 20;
    private static final int REVEAL_STEP_TICKS = 15;
    private static final int ATTACK_RESULT_TICKS = 40;
    private static final int CHOICE_TIMEOUT_TICKS = 200;
    private static final double MAX_BATTLE_DISTANCE = 32.0;
    private static final double CHALLENGE_RANGE = 16.0;

    private record Challenge(UUID from, UUID to, long expiresAt, double stake) {
    }

    private final Plugin plugin;
    private final FishMotion motion;
    private final BattleEffects effects;
    private final EconomyHook economy;
    private final BattlePets pets;
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final Map<UUID, BattleSession> sessions = new HashMap<>();

    public BattleManager(Plugin plugin, FishMotion motion) {
        this.plugin = plugin;
        this.motion = motion;
        this.economy = EconomyHook.load();
        this.effects = new BattleEffects(plugin);
        this.pets = new BattlePets(motion);
    }

    public boolean inBattle(UUID player) {
        return sessions.containsKey(player);
    }

    public void challengeNearby(Player from, double stake) {
        if (inBattle(from.getUniqueId())) {
            from.sendMessage(Component.text("すでにバトル中です。", NamedTextColor.RED));
            return;
        }
        if (motion.petOf(from) == null) {
            from.sendMessage(Component.text("まず魚を出してください（魚入りバケツを右クリック）。", NamedTextColor.RED));
            return;
        }
        List<Player> nearby = new ArrayList<>();
        for (Player other : from.getWorld().getPlayers()) {
            if (other.equals(from)) {
                continue;
            }
            if (other.getLocation().distance(from.getLocation()) <= CHALLENGE_RANGE
                    && motion.petOf(other) != null) {
                nearby.add(other);
            }
        }
        if (nearby.isEmpty()) {
            from.sendMessage(Component.text("近くに対戦できる相手（魚を出している人）がいません（"
                    + (int) CHALLENGE_RANGE + "ブロック以内）。", NamedTextColor.RED));
            return;
        }
        int sent = 0;
        for (Player other : nearby) {
            if (challenge(from, other, stake)) {
                sent++;
            }
        }
        if (sent == 0) {
            from.sendMessage(Component.text("申し込める相手がいませんでした。", NamedTextColor.RED));
        } else {
            from.sendMessage(Component.text(sent + " 人にバトルを申し込みました。", NamedTextColor.GREEN));
        }
    }

    public boolean challenge(Player from, Player to, double stake) {
        if (inBattle(from.getUniqueId())) {
            from.sendMessage(Component.text("すでにバトル中です。", NamedTextColor.RED));
            return false;
        }
        if (inBattle(to.getUniqueId())) {
            from.sendMessage(Component.text(to.getName() + " はバトル中です。", NamedTextColor.RED));
            return false;
        }
        if (motion.petOf(from) == null) {
            from.sendMessage(Component.text("まず魚を出してください（魚入りバケツを右クリック）。", NamedTextColor.RED));
            return false;
        }
        if (motion.petOf(to) == null) {
            from.sendMessage(Component.text(to.getName() + " は魚を出していません。", NamedTextColor.RED));
            return false;
        }
        double bet = Math.max(0, stake);
        if (bet > 0 && !economy.available()) {
            from.sendMessage(Component.text("賭け金は利用できません（Vault/Essentials 未導入）。",
                    NamedTextColor.RED));
            return false;
        }
        if (bet > 0 && economy.balance(from) < bet) {
            from.sendMessage(Component.text("残高が足りません（必要 " + format(bet) + "）。", NamedTextColor.RED));
            return false;
        }
        challenges.put(to.getUniqueId(), new Challenge(from.getUniqueId(), to.getUniqueId(),
                System.currentTimeMillis() + 60_000L, bet));

        from.sendMessage(Component.text(to.getName() + " にバトルを申し込みました"
                + (bet > 0 ? "（賭け金 " + format(bet) + "）" : "") + "。", NamedTextColor.GREEN));
        to.sendMessage(Component.text()
                .append(Component.text(from.getName() + " からバトルの申し込み！", NamedTextColor.GOLD))
                .append(bet > 0 ? Component.text(" 賭け金 " + format(bet), NamedTextColor.YELLOW)
                        : Component.empty())
                .append(Component.newline())
                .append(button("[受ける]", NamedTextColor.GREEN, "/uo accept", "バトルを開始"))
                .append(Component.text(" "))
                .append(button("[断る]", NamedTextColor.RED, "/uo deny", "申し込みを断る"))
                .build());
        return true;
    }

    public void accept(Player to) {
        Challenge challenge = challenges.remove(to.getUniqueId());
        if (challenge == null || challenge.expiresAt() < System.currentTimeMillis()) {
            to.sendMessage(Component.text("有効な申し込みがありません。", NamedTextColor.RED));
            return;
        }
        if (inBattle(to.getUniqueId())) {
            to.sendMessage(Component.text("すでにバトル中です。", NamedTextColor.RED));
            return;
        }
        Player from = Bukkit.getPlayer(challenge.from());
        if (from == null) {
            to.sendMessage(Component.text("相手はオフラインです。", NamedTextColor.RED));
            return;
        }
        if (inBattle(from.getUniqueId())) {
            to.sendMessage(Component.text("相手は別のバトルを始めました。", NamedTextColor.RED));
            return;
        }
        if (!from.getWorld().equals(to.getWorld())
                || from.getLocation().distance(to.getLocation()) > CHALLENGE_RANGE) {
            to.sendMessage(Component.text("相手が遠すぎます（" + (int) CHALLENGE_RANGE
                    + "ブロック以内に近づいてください）。", NamedTextColor.RED));
            if (challenge.stake() > 0) {
                to.sendMessage(Component.text("賭け金は引き落としていません。", NamedTextColor.GRAY));
            }
            return;
        }
        var petA = motion.petOf(from);
        var petB = motion.petOf(to);
        Fish fishA = petA == null ? null : FishData.read(petA);
        Fish fishB = petB == null ? null : FishData.read(petB);
        if (fishA == null || fishB == null) {
            to.sendMessage(Component.text("どちらかの魚を出していません。", NamedTextColor.RED));
            return;
        }

        double stake = challenge.stake();
        if (stake > 0) {
            if (!economy.available()) {
                to.sendMessage(Component.text("賭け金は利用できません。", NamedTextColor.RED));
                return;
            }
            if (economy.balance(to) < stake) {
                to.sendMessage(Component.text("残高が足りません（必要 " + format(stake) + "）。", NamedTextColor.RED));
                return;
            }
            if (!economy.withdraw(from, stake) || !economy.withdraw(to, stake)) {
                economy.deposit(from, stake);
                economy.deposit(to, stake);
                to.sendMessage(Component.text("賭け金の引き落としに失敗しました。", NamedTextColor.RED));
                return;
            }
        }

        challenges.entrySet().removeIf(entry -> entry.getValue().from().equals(from.getUniqueId()));

        BattleSession session = new BattleSession(from.getUniqueId(), to.getUniqueId(), fishA, fishB);
        session.setPet(from.getUniqueId(), petA.getUniqueId());
        session.setPet(to.getUniqueId(), petB.getUniqueId());
        session.setCenter(midpoint(petA.getLocation(), petB.getLocation()));
        session.setStake(stake);
        sessions.put(from.getUniqueId(), session);
        sessions.put(to.getUniqueId(), session);

        pets.freeze(session, from.getUniqueId(), petA);
        pets.freeze(session, to.getUniqueId(), petB);
        pets.showBattleNames(session);
        var fA = from.getLocation();
        var fB = to.getLocation();
        var toB = fB.toVector().subtract(fA.toVector()).setY(0);
        var toA = fA.toVector().subtract(fB.toVector()).setY(0);
        if (toB.lengthSquared() > 1.0E-6) {
            var unit = toB.clone().normalize();
            var mid = fA.clone().add(toB.clone().multiply(0.5));
            pets.place(petA, mid.clone().add(unit.clone().multiply(-0.5)).add(0, 1.1, 0), unit);
            pets.place(petB, mid.clone().add(unit.clone().multiply(0.5)).add(0, 1.1, 0),
                    unit.clone().multiply(-1));
        }

        broadcast(session, Component.text("バトル開始！", NamedTextColor.GOLD));
        effects.battleStart(session);
        startMeters(session, from, to);
        beginRound(session, 1);
    }

    public void startDemo(Player player) {
        if (inBattle(player.getUniqueId())) {
            player.sendMessage(Component.text("すでにバトル中です。", NamedTextColor.RED));
            return;
        }
        var pet = motion.petOf(player);
        if (pet == null) {
            ItemStack granted = FishItems.randomBucket(FishType.random(), 5);
            Fish fish = FishItems.read(granted);
            var eye = player.getEyeLocation();
            Vector dir = eye.getDirection().setY(0);
            if (dir.lengthSquared() < 0.001) {
                dir = new Vector(0, 0, 1);
            }
            var spawnAt = eye.clone().add(dir.normalize().multiply(1.6));
            pet = CaptureListener.spawnFor(fish, spawnAt);
            if (pet == null) {
                player.sendMessage(Component.text("デモ用の魚を出せませんでした。", NamedTextColor.RED));
                return;
            }
            CaptureListener.applyData(pet, fish);
            pet.getPersistentDataContainer().set(FishKeys.OWNER,
                    PersistentDataType.STRING, player.getUniqueId().toString());
            pet.setPersistent(true);
            motion.track(pet);
            player.sendMessage(Component.text("デモ用に魚を出しました。", NamedTextColor.GREEN));
        }
        Fish mine = FishData.read(pet);
        if (mine == null) {
            return;
        }
        Fish npcFish = FishItems.read(FishItems.randomBucket(FishType.random(), 5));
        UUID npcId = UUID.randomUUID();
        BattleSession session = new BattleSession(player.getUniqueId(), npcId, mine, npcFish);
        session.setNpc(npcId, "デモの村人");
        session.setDemo(true);
        session.setPet(player.getUniqueId(), pet.getUniqueId());

        var location = player.getLocation();
        var direction = location.getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();
        var base = location.clone().add(0, 1.1, 0);

        pets.freeze(session, player.getUniqueId(), pet);
        pets.place(pet, base.clone().add(direction.clone().multiply(2.0)), direction);
        var npcFishEntity = CaptureListener.spawnFor(npcFish, base.clone()
                .add(direction.clone().multiply(3.0)));
        if (npcFishEntity != null) {
            npcFishEntity.setRotation(location.getYaw() + 180f, 0f);
        }
        var spawnAt = base.clone().add(direction.clone().multiply(5.0)).add(0, -1.0, 0);
        if (npcFishEntity != null) {
            CaptureListener.applyData(npcFishEntity, npcFish);
            npcFishEntity.getPersistentDataContainer().set(FishKeys.OWNER,
                    PersistentDataType.STRING, npcId.toString());
            npcFishEntity.setPersistent(true);
            motion.track(npcFishEntity);
            motion.setFollow(npcFishEntity, false);
            motion.place(npcFishEntity, npcFishEntity.getLocation());
            session.setNpcPetId(npcFishEntity.getUniqueId());
        }
        pets.showBattleNames(session);

        var villager = player.getWorld().spawn(spawnAt, Villager.class,
                CreatureSpawnEvent.SpawnReason.CUSTOM, v -> {
                    v.setAI(false);
                    v.setSilent(true);
                    v.setInvulnerable(true);
                    v.setCollidable(false);
                    v.setGravity(false);
                    v.setPersistent(false);
                    v.setRemoveWhenFarAway(true);
                    v.customName(Component.text("デモの村人",
                            NamedTextColor.GOLD).decoration(
                            TextDecoration.ITALIC, false));
                    v.setCustomNameVisible(true);
                    v.getPersistentDataContainer().set(FishKeys.DEMO, PersistentDataType.BYTE, (byte) 1);
                });
        session.setVillagerId(villager.getUniqueId());
        session.setCenter(midpoint(pet.getLocation(),
                npcFishEntity != null ? npcFishEntity.getLocation() : villager.getLocation()));
        sessions.put(player.getUniqueId(), session);
        sessions.put(npcId, session);

        effects.battleStart(session);
        broadcast(session, Component.text("デモバトル開始！ 相手は NPC の "
                + npcFish.displayName() + " [" + npcFish.type().label() + "]", NamedTextColor.GOLD));
        startMeters(session, player, null);
        beginRound(session, 1);
    }

    public void deny(Player to) {
        Challenge challenge = challenges.remove(to.getUniqueId());
        if (challenge == null) {
            to.sendMessage(Component.text("有効な申し込みがありません。", NamedTextColor.RED));
            return;
        }
        Player from = Bukkit.getPlayer(challenge.from());
        if (from != null) {
            from.sendMessage(Component.text(to.getName() + " はバトルを断りました。", NamedTextColor.YELLOW));
        }
    }

    private void beginRound(BattleSession session, int round) {
        if (session.closed()
                || (session.phase() != BattleSession.Phase.IDLE
                        && session.phase() != BattleSession.Phase.FINISHED)) {
            return;
        }
        session.setRound(round);
        session.setPhase(BattleSession.Phase.COUNTDOWN);
        session.clearChoices();
        session.resetMash();
        session.setAttacker(null);

        Player a = Bukkit.getPlayer(session.playerA());
        Player b = Bukkit.getPlayer(session.playerB());
        if (a != null) {
            status(a, session);
        }
        if (b != null) {
            status(b, session);
        }
        broadcast(session, Component.text("── 第" + round + "ラウンド ──", NamedTextColor.GOLD));
        startCountdown(session, a, b);
    }

    private void startCountdown(BattleSession session, Player a, Player b) {
        session.setPhase(BattleSession.Phase.COUNTDOWN);
        broadcast(session, Component.text("クリックしろ！", NamedTextColor.RED));
        effects.countdownTitle(a, "クリックしろ！", COUNTDOWN_SECONDS);
        effects.countdownTitle(b, "クリックしろ！", COUNTDOWN_SECONDS);
        for (int i = 1; i <= COUNTDOWN_SECONDS; i++) {
            int remaining = COUNTDOWN_SECONDS - i + 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session.closed() || session.phase() != BattleSession.Phase.COUNTDOWN) {
                    return;
                }
                effects.countdownTitle(a, Integer.toString(remaining), COUNTDOWN_SECONDS);
                effects.countdownTitle(b, Integer.toString(remaining), COUNTDOWN_SECONDS);
                effects.countdownNote(a);
                effects.countdownNote(b);
            }, i * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed() || session.phase() != BattleSession.Phase.COUNTDOWN) {
                return;
            }
            broadcast(session, Component.text("スタート！", NamedTextColor.GREEN));
            effects.countdownTitle(a, "スタート！", 1);
            effects.countdownTitle(b, "スタート！", 1);
            startMashing(session, a, b);
        }, (COUNTDOWN_SECONDS + 1) * 20L);
    }

    private boolean checkDistance(BattleSession session) {
        org.bukkit.Location center = session.center();
        if (center == null) {
            return false;
        }
        for (UUID id : new UUID[] {session.playerA(), session.playerB()}) {
            if (session.isNpc(id)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                broadcast(session, Component.text(session.nameOf(id) + " が退出したためバトルを終了します。",
                        NamedTextColor.YELLOW));
                endQuietly(id);
                return true;
            }
            if (!center.getWorld().equals(player.getWorld())
                    || center.distance(player.getLocation()) > MAX_BATTLE_DISTANCE) {
                broadcast(session, Component.text(session.nameOf(id) + " が離れたためバトルを終了します。",
                        NamedTextColor.YELLOW));
                endQuietly(id);
                return true;
            }
        }
        return false;
    }

    private static org.bukkit.Location midpoint(org.bukkit.Location first, org.bukkit.Location second) {
        if (first == null || second == null || !first.getWorld().equals(second.getWorld())) {
            return first != null ? first : second;
        }
        return first.clone().add(second.toVector().subtract(first.toVector()).multiply(0.5));
    }

    public void endQuietly(UUID player) {
        BattleSession session = sessions.get(player);
        if (session == null) {
            return;
        }
        refund(session);
        cleanupTasks(session);
        cleanup(session);
    }

    public void abort(Player player) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        broadcast(session, Component.text(player.getName() + " がバトルを中断しました（賭け金は返金）。",
                NamedTextColor.YELLOW));
        refund(session);
        cleanupTasks(session);
        cleanup(session);
    }

    private void refund(BattleSession session) {
        double stake = session.stake();
        if (stake <= 0 || !economy.available()) {
            return;
        }
        Player a = Bukkit.getPlayer(session.playerA());
        Player b = Bukkit.getPlayer(session.playerB());
        if (a != null) {
            economy.deposit(a, stake);
            a.sendMessage(Component.text("賭け金 " + format(stake) + " を返金しました。", NamedTextColor.GRAY));
        }
        if (b != null) {
            economy.deposit(b, stake);
            b.sendMessage(Component.text("賭け金 " + format(stake) + " を返金しました。", NamedTextColor.GRAY));
        }
        session.setStake(0);
    }

    private static String format(double amount) {
        return amount == Math.floor(amount) ? Long.toString((long) amount)
                : String.format("%.2f", amount);
    }

    private void startMeters(BattleSession session, Player a, Player b) {
        BattleHud.init(session, a);
        BattleHud.init(session, b);
        session.setMeterTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.phase() == BattleSession.Phase.FINISHED && !session.resultGrace()) {
                return;
            }
            if (session.phase() == BattleSession.Phase.MASHING) {
                session.decay(0.012);
            }
            if (checkDistance(session)) {
                return;
            }
            pets.faceEachOther(session);
            pets.faceVillager(session);
            BattleHud.update(session, Bukkit.getPlayer(session.playerA()));
            BattleHud.update(session, Bukkit.getPlayer(session.playerB()));
        }, 2L, 2L));
    }

    private void startMashing(BattleSession session, Player a, Player b) {
        if (session.phase() != BattleSession.Phase.COUNTDOWN) {
            return;
        }
        session.setPhase(BattleSession.Phase.MASHING);
        session.resetMash();
        broadcast(session, Component.text("左クリックを連打しろ！ " + (MASH_MILLIS / 1000) + "秒間"
                + "（EXPバー＝自分 / ボスバー＝相手）", NamedTextColor.WHITE));
        promptMash(a);
        promptMash(b);

        if (session.npcId() != null) {
            UUID npc = session.npcId();
            if (session.npcTask() != null) {
                session.npcTask().cancel();
                session.setNpcTask(null);
            }
            BukkitTask npcTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (session.phase() != BattleSession.Phase.MASHING) {
                    return;
                }
                if (ThreadLocalRandom.current().nextDouble() < 0.85) {
                    session.registerMash(npc, System.currentTimeMillis());
                }
            }, 2L, 3L);
            session.setNpcTask(npcTask);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed()) {
                return;
            }
            beginChoosing(session);
        }, MASH_TICKS);
    }

    private void promptMash(Player player) {
        if (player == null) {
            return;
        }
        player.sendMessage(Component.text()
                .append(Component.text("左クリックを連打しろ！ ", NamedTextColor.RED))
                .append(button("【連打】", NamedTextColor.GOLD, "/uo mash", "クリックできない時はこれを連打"))
                .build());
    }

    private void beginChoosing(BattleSession session) {
        if (session.phase() != BattleSession.Phase.MASHING) {
            return;
        }
        if (session.npcTask() != null) {
            session.npcTask().cancel();
            session.setNpcTask(null);
        }
        session.setPhase(BattleSession.Phase.CHOOSING);
        session.clearChoices();
        List<Rps> order = new ArrayList<>(List.of(Rps.GU, Rps.CHOKI, Rps.PA));
        Collections.shuffle(order);
        session.setOrder(order);
        broadcast(session, Component.text("手を選べ！ " + session.order().stream()
                .map(Rps::emoji).collect(Collectors.joining(" "))
                + "（この順に並んでいます）", NamedTextColor.GOLD));
        broadcast(session, Component.text()
                .append(Component.text("気合  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(nameOf(session, session.playerA()) + " "
                        + Math.round(session.charge(session.playerA()) * 100) + "%",
                        NamedTextColor.GREEN))
                .append(Component.text("  ／  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(nameOf(session, session.playerB()) + " "
                        + Math.round(session.charge(session.playerB()) * 100) + "%",
                        NamedTextColor.RED))
                .build());
        Player a = Bukkit.getPlayer(session.playerA());
        Player b = Bukkit.getPlayer(session.playerB());
        if (a != null) {
            promptChoice(session, a);
        }
        if (b != null) {
            promptChoice(session, b);
        }
        broadcast(session, Component.text("1/2/3・チャットで選んで、右クリックかスニークで決定（10秒で自動確定）",
                NamedTextColor.GRAY));
        if (session.choiceTask() != null) {
            session.choiceTask().cancel();
        }
        session.setChoiceTask(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed() || session.phase() != BattleSession.Phase.CHOOSING) {
                return;
            }
            Rps[] values = Rps.values();
            boolean picked = false;
            for (UUID id : new UUID[] {session.playerA(), session.playerB()}) {
                if (session.choiceOf(id) != null) {
                    continue;
                }
                Rps preview = session.previewOf(id);
                Rps hand = preview != null ? preview : values[ThreadLocalRandom.current().nextInt(values.length)];
                session.setChoice(id, hand);
                Player p = Bukkit.getPlayer(id);
                if (p != null && !session.isNpc(id)) {
                    p.sendMessage(Component.text(preview != null
                            ? "時間切れ！ 選択中の手で確定しました。"
                            : "時間切れ！ 手がランダムで選ばれました。", NamedTextColor.YELLOW));
                }
                picked = true;
            }
            if (picked && session.bothChosen()) {
                resolveChoices(session);
            }
        }, CHOICE_TIMEOUT_TICKS));
        if (session.npcId() != null) {
            UUID npc = session.npcId();
            long delay = 20L + ThreadLocalRandom.current().nextInt(30);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session.closed() || session.phase() != BattleSession.Phase.CHOOSING
                        || session.choiceOf(npc) != null) {
                    return;
                }
                Rps[] values = Rps.values();
                session.setChoice(npc, values[ThreadLocalRandom.current().nextInt(values.length)]);
                if (session.bothChosen()) {
                    resolveChoices(session);
                }
            }, delay);
        }
    }

    private void promptChoice(BattleSession session, Player player) {
        player.sendActionBar(Component.text("1/2/3・チャットで選んで右クリックかスニーク", NamedTextColor.GOLD));
        TextComponent.Builder builder = Component.text();
        boolean first = true;
        for (Rps rps : session.order()) {
            if (!first) {
                builder.append(Component.text("   "));
            }
            first = false;
            builder.append(button(rps.emoji(), rps.color(), commandFor(rps),
                    rps.label() + "で勝負"));
        }
        player.sendMessage(builder.build());
    }

    private static String commandFor(Rps rps) {
        return switch (rps) {
            case GU -> "/uo rps gu";
            case CHOKI -> "/uo rps choki";
            case PA -> "/uo rps pa";
        };
    }

    public boolean isChoosing(UUID player) {
        BattleSession session = sessions.get(player);
        return session != null && session.phase() == BattleSession.Phase.CHOOSING;
    }

    public void selectBySlot(Player player, int slot) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() != BattleSession.Phase.CHOOSING) {
            return;
        }
        if (slot < 0 || slot >= session.order().size()) {
            return;
        }
        setPreview(player, session, session.order().get(slot));
    }

    private void setPreview(Player player, BattleSession session, Rps hand) {
        if (session.choiceOf(player.getUniqueId()) != null) {
            return;
        }
        if (session.phase() != BattleSession.Phase.CHOOSING) {
            return;
        }
        session.setPreview(player.getUniqueId(), hand);
        effects.previewTitle(player, hand);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
    }

    public boolean confirmSneak(Player player) {
        return confirm(player);
    }

    public boolean confirmPreview(Player player) {
        return confirm(player);
    }

    private boolean confirm(Player player) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() != BattleSession.Phase.CHOOSING) {
            return false;
        }
        Rps hand = session.previewOf(player.getUniqueId());
        if (hand == null) {
            int held = player.getInventory().getHeldItemSlot();
            if (held < 0 || held >= session.order().size()) {
                player.sendActionBar(Component.text("1-3で手を選んでから右クリック（またはスニーク）で決定",
                        NamedTextColor.YELLOW));
                return true;
            }
            hand = session.order().get(held);
        }
        choose(player, hand);
        return true;
    }

    public void preview(Player player, Rps choice) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() != BattleSession.Phase.CHOOSING) {
            player.sendMessage(Component.text("いまは手を選べません。", NamedTextColor.RED));
            return;
        }
        if (session.choiceOf(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("すでに確定しています。", NamedTextColor.GRAY));
            return;
        }
        setPreview(player, session, choice);
    }

    private void choose(Player player, Rps choice) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() != BattleSession.Phase.CHOOSING) {
            player.sendMessage(Component.text("いまは手を選べません。", NamedTextColor.RED));
            return;
        }
        if (session.choiceOf(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("すでに選んでいます。相手を待ちましょう。", NamedTextColor.GRAY));
            return;
        }
        session.setChoice(player.getUniqueId(), choice);
        player.sendMessage(Component.text("「" + choice.label() + "」で確定。", choice.color()));
        player.sendActionBar(Component.text("相手の選択を待っています…", NamedTextColor.GRAY));

        if (session.bothChosen()) {
            resolveChoices(session);
        }
    }

    private void resolveChoices(BattleSession session) {
        if (session.phase() != BattleSession.Phase.CHOOSING || !session.bothChosen()) {
            return;
        }
        session.setPhase(BattleSession.Phase.ATTACKING);
        Rps a = session.choiceOf(session.playerA());
        Rps b = session.choiceOf(session.playerB());
        Player pa = Bukkit.getPlayer(session.playerA());
        Player pb = Bukkit.getPlayer(session.playerB());

        effects.ownHand(pa, a);
        effects.ownHand(pb, b);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed() || session.phase() != BattleSession.Phase.ATTACKING) {
                return;
            }
            revealAndResolve(session, pa, pb, a, b);
        }, REVEAL_STEP_TICKS);
    }

    private void revealAndResolve(BattleSession session, Player pa, Player pb, Rps a, Rps b) {
        Component reveal = Component.text()
                .append(Component.text(nameOf(session, session.playerA()) + " ", NamedTextColor.WHITE))
                .append(Component.text(a.emoji() + a.label(), a.color()))
                .append(Component.text(" vs ", NamedTextColor.GRAY))
                .append(Component.text(nameOf(session, session.playerB()) + " ", NamedTextColor.WHITE))
                .append(Component.text(b.emoji() + b.label(), b.color()))
                .build();
        broadcast(session, reveal);

        Component result = a == b
                ? Component.text("あいこ！", NamedTextColor.YELLOW)
                : Component.text(a.beats(b) ? "勝ち！" : "負け…",
                        a.beats(b) ? NamedTextColor.GOLD : NamedTextColor.RED);
        Component titleText = Component.text()
                .append(Component.text(a.emoji(), a.color()))
                .append(Component.text(" vs ", NamedTextColor.GRAY))
                .append(Component.text(b.emoji(), b.color()))
                .append(Component.text("  ", NamedTextColor.WHITE))
                .append(result)
                .build();
        effects.duelTitle(pa, titleText, result);
        effects.duelTitle(pb, titleText,
                a == b ? result : Component.text(b.beats(a) ? "勝ち！" : "負け…",
                        b.beats(a) ? NamedTextColor.GOLD : NamedTextColor.RED));
        effects.resultSound(pa, a == b ? null : a.beats(b));
        effects.resultSound(pb, a == b ? null : b.beats(a));

        if (a == b) {
            resolveTie(session, pa, pb);
            return;
        }

        UUID attackerId = a.beats(b) ? session.playerA() : session.playerB();
        session.setAttacker(attackerId);
        Player defender = Bukkit.getPlayer(session.opponentOf(attackerId));
        double attackerCharge = session.charge(attackerId);
        double defenderCharge = session.charge(session.opponentOf(attackerId));
        int attackerClicks = session.clicks(attackerId);
        broadcast(session, Component.text(nameOf(session, attackerId) + " の勝ち！ 攻撃！",
                NamedTextColor.GOLD));
        if (defender != null) {
            defender.sendActionBar(Component.text("相手の攻撃…", NamedTextColor.DARK_GRAY));
        }
        scheduleAttack(session, attackerId, session.opponentOf(attackerId),
                attackerCharge, defenderCharge, attackerClicks, 1.0);
    }

    private void scheduleAttack(BattleSession session, UUID attackerId, UUID defenderId,
            double attackerCharge, double defenderCharge, int attackerClicks, double clashScale) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed() || session.phase() != BattleSession.Phase.ATTACKING) {
                return;
            }
            resolveAttack(session, attackerId, defenderId, attackerCharge, defenderCharge,
                    attackerClicks, clashScale);
        }, ATTACK_DELAY_TICKS);
    }

    private void resolveTie(BattleSession session, Player pa, Player pb) {
        UUID idA = session.playerA();
        UUID idB = session.playerB();
        double chargeA = session.charge(idA);
        double chargeB = session.charge(idB);
        double gap = Math.abs(chargeA - chargeB);

        if (gap <= 0.0) {
            broadcast(session, Component.text("あいこで互角！ もう一度連打から！", NamedTextColor.YELLOW));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session.closed()) {
                    return;
                }
                session.setPhase(BattleSession.Phase.IDLE);
                beginRound(session, session.round() + 1);
            }, 20L);
            return;
        }

        UUID winnerId = chargeA > chargeB ? idA : idB;
        UUID loserId = session.opponentOf(winnerId);
        double winnerCharge = session.charge(winnerId);
        double loserCharge = session.charge(loserId);
        int winnerClicks = session.clicks(winnerId);

        broadcast(session, Component.text("あいこ！ だが気合の差で "
                + nameOf(session, winnerId) + " が押し込む！", NamedTextColor.GOLD));
        effects.duelTitle(Bukkit.getPlayer(winnerId), Component.text("あいこ！", NamedTextColor.YELLOW),
                Component.text("気合差で押し込む！", NamedTextColor.GOLD));
        effects.duelTitle(Bukkit.getPlayer(loserId), Component.text("あいこ！", NamedTextColor.YELLOW),
                Component.text("押し込まれた…", NamedTextColor.GRAY));
        double scale = Math.max(0.15, Math.min(1.0, gap / 0.15));
        scheduleAttack(session, winnerId, loserId, winnerCharge, loserCharge, winnerClicks, scale);
    }

    public boolean isMashing(Player player) {
        BattleSession session = sessions.get(player.getUniqueId());
        return session != null && session.phase() == BattleSession.Phase.MASHING;
    }

    public void mash(Player player) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null || session.phase() != BattleSession.Phase.MASHING) {
            return;
        }
        if (session.registerMash(player.getUniqueId(), System.currentTimeMillis())) {
            int count = session.clicks(player.getUniqueId());
            if (count % 3 == 0) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f,
                        1.0f + Math.min(0.8f, count / 60.0f));
            }
        }
    }

    private void resolveAttack(BattleSession session, UUID attackerId, UUID defenderId,
            double attackerCharge, double defenderCharge, int attackerClicks) {
        resolveAttack(session, attackerId, defenderId, attackerCharge, defenderCharge, attackerClicks, 1.0);
    }

    private void resolveAttack(BattleSession session, UUID attackerId, UUID defenderId,
            double attackerCharge, double defenderCharge, int attackerClicks, double clashScale) {
        Fish attacker = session.fishOf(attackerId);
        Fish target = session.fishOf(defenderId);
        double multiplier = attacker.type().multiplier(target.type());

        int streak = attackerCharge >= 0.80 ? session.specialStreak(attackerId) + 1 : 0;
        session.setSpecialStreak(attackerId, streak);
        String specialName = null;
        double specialBonus = 1.0;
        if (streak >= 2) {
            specialName = attacker.species().superSpecial();
            specialBonus = 2.4;
            session.setSpecialStreak(attackerId, 0);
        } else if (streak == 1) {
            specialName = attacker.species().special();
            specialBonus = 1.6;
        }

        Technique technique = DamageCalc.bestTechnique(attacker);
        double techniqueBonus = technique != null ? technique.bonus() : 1.0;
        int damage = (int) Math.max(1, Math.round(DamageCalc.damage(attacker, target, attackerCharge, defenderCharge,
                multiplier * specialBonus * techniqueBonus) * clashScale));

        Fish damaged = target.withHp(target.hp() - damage);
        session.setFish(defenderId, damaged);

        Player attackerPlayer = Bukkit.getPlayer(attackerId);
        Player defender = Bukkit.getPlayer(defenderId);

        if (technique != null) {
            broadcast(session, Component.text("わざ「" + technique.name() + "」！", technique.color()));
        }
        if (specialName != null) {
            boolean isSuper = specialBonus >= 2.0;
            broadcast(session, Component.text()
                    .append(Component.text(isSuper ? "★超必殺技★ " : "☆必殺技☆ ",
                            isSuper ? NamedTextColor.DARK_PURPLE : NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(attacker.displayName() + " の " + specialName + "！",
                            isSuper ? NamedTextColor.DARK_PURPLE : NamedTextColor.LIGHT_PURPLE))
                    .build());
            Player attackerVoice = Bukkit.getPlayer(attackerId);
            if (attackerVoice != null) {
                attackerVoice.playSound(attackerVoice.getLocation(),
                        isSuper ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
            }
        }
        Component hit = Component.text()
                .append(Component.text(attacker.displayName() + " の攻撃！ ", NamedTextColor.WHITE))
                .append(Component.text("(気合 " + Math.round(attackerCharge * 100) + "% / 相手 "
                        + Math.round(defenderCharge * 100) + "%) ", NamedTextColor.DARK_GRAY))
                .append(Component.text(target.displayName() + " に " + damage + " ダメージ", NamedTextColor.WHITE))
                .build();
        if (multiplier > 1.0) {
            hit = hit.append(Component.text("（タイプ有利！）", NamedTextColor.GOLD));
        } else if (multiplier < 1.0) {
            hit = hit.append(Component.text("（タイプ不利…）", NamedTextColor.GRAY));
        }
        broadcast(session, hit);
        Component attackTitle = Component.text(damage + " ダメージ",
                multiplier > 1.0 ? NamedTextColor.GOLD : NamedTextColor.WHITE);
        Component attackSub = Component.text(multiplier > 1.0 ? "こうかはばつぐん！"
                : multiplier < 1.0 ? "こうかはいまひとつ…" : "ヒット！",
                multiplier > 1.0 ? NamedTextColor.GOLD : NamedTextColor.GRAY);
        effects.duelTitle(Bukkit.getPlayer(attackerId), attackTitle, attackSub);
        effects.duelTitle(Bukkit.getPlayer(defenderId),
                Component.text(damage + " ダメージ", NamedTextColor.RED), attackSub);
        effects.attack(session, attackerId, defenderId, pets.petEntity(session, defenderId),
                multiplier, specialName != null, specialBonus >= 2.0);
        if (defender != null) {
            defender.playSound(defender.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.2f);
        }
        if (attackerPlayer != null) {
            pets.write(session, attackerId, attacker);
            effects.flashAttackCharge(session, attackerPlayer, attackerCharge, attackerClicks);
        }
        if (defender != null) {
            pets.write(session, defenderId, damaged);
        }

        if (damaged.hp() <= 0) {
            broadcast(session, Component.text(target.displayName() + " は たおれた！", NamedTextColor.RED));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!session.closed() && session.phase() != BattleSession.Phase.FINISHED) {
                    finish(session, attackerId, defenderId);
                }
            }, ATTACK_RESULT_TICKS);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.closed() || session.phase() != BattleSession.Phase.ATTACKING) {
                return;
            }
            session.setPhase(BattleSession.Phase.IDLE);
            beginRound(session, session.round() + 1);
        }, ATTACK_RESULT_TICKS);
    }

    public void forfeit(Player player) {
        BattleSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("バトル中ではありません。", NamedTextColor.RED));
            return;
        }
        cleanupTasks(session);
        broadcast(session, Component.text(player.getName() + " は にげた！", NamedTextColor.YELLOW));
        finish(session, session.opponentOf(player.getUniqueId()), player.getUniqueId());
    }

    public void endQuietly(Player player) {
        endQuietly(player.getUniqueId());
    }

    private void finish(BattleSession session, UUID winnerId, UUID loserId) {
        session.setPhase(BattleSession.Phase.FINISHED);
        cleanupTasks(session);
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);

        if (winner != null) {
            Fish fish = session.fishOf(winnerId);
            Fish beaten = session.fishOf(loserId);
            int exp = Math.max(5, beaten.level() * 5 + beaten.species().hp() / 3);
            Fish grown = fish.gainExp(exp);
            grown = grown.withHp(grown.maxHp());
            session.setFish(winnerId, grown);
            pets.write(session, winnerId, grown);
            winner.sendMessage(Component.text("バトルに勝った！ +" + exp + " EXP", NamedTextColor.GOLD));
            if (grown.level() > fish.level()) {
                winner.sendMessage(Component.text("レベルアップ！ Lv" + grown.level(), NamedTextColor.AQUA));
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
        if (loser != null) {
            Fish fish = session.fishOf(loserId);
            Fish restored = fish.withHp(fish.maxHp());
            session.setFish(loserId, restored);
            pets.write(session, loserId, restored);
            loser.sendMessage(Component.text("バトルに負けた…", NamedTextColor.GRAY));
        }
        if (winner != null && loser != null) {
            effects.battleEnd(session, winner, loser);
        }
        if (session.stake() > 0 && winner != null) {
            double payout = session.stake() * 2;
            economy.deposit(winner, payout);
            broadcast(session, Component.text(winner.getName() + " は賭け金 " + format(payout)
                    + " を獲得した！", NamedTextColor.GOLD));
        }
        if (session.isDemo()) {
            broadcast(session, Component.text("（デモバトル終了）", NamedTextColor.DARK_GRAY));
        }
        session.setResultGrace(true);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            session.setResultGrace(false);
            cleanupTasks(session);
            cleanup(session);
        }, RESULT_HOLD_TICKS);
    }

    private void cleanupTasks(BattleSession session) {
        if (session.choiceTask() != null) {
            session.choiceTask().cancel();
            session.setChoiceTask(null);
        }
        if (session.npcTask() != null) {
            session.npcTask().cancel();
            session.setNpcTask(null);
        }
        BattleHud.stop(session);
    }

    private void cleanup(BattleSession session) {
        session.close();
        cleanupTasks(session);
        sessions.remove(session.playerA());
        sessions.remove(session.playerB());
        if (session.villagerId() != null) {
            Entity villager = Bukkit.getEntity(session.villagerId());
            if (villager != null) {
                villager.remove();
            }
        }
        if (session.npcPetId() != null) {
            Entity npcPet = Bukkit.getEntity(session.npcPetId());
            if (npcPet != null) {
                npcPet.remove();
            }
        }
        pets.hideBattleNames(session);
        pets.restore(session, session.playerA());
        pets.restore(session, session.playerB());
    }

    private void status(Player player, BattleSession session) {
        Fish mine = session.fishOf(player.getUniqueId());
        Fish theirs = session.fishOf(session.opponentOf(player.getUniqueId()));
        player.sendMessage(Component.text()
                .append(Component.text("あなた: " + mine.species().name() + " [" + mine.type().label() + "]",
                        mine.type().color()))
                .append(Component.text(" HP " + mine.hp() + "/" + mine.maxHp(), NamedTextColor.WHITE))
                .append(Component.text("  ／  あいて: " + theirs.species().name()
                        + " [" + theirs.type().label() + "]", theirs.type().color()))
                .append(Component.text(" HP " + theirs.hp() + "/" + theirs.maxHp(), NamedTextColor.WHITE))
                .build());
    }

    private void broadcast(BattleSession session, Component message) {
        Player a = Bukkit.getPlayer(session.playerA());
        Player b = Bukkit.getPlayer(session.playerB());
        if (a != null) {
            a.sendMessage(message);
        }
        if (b != null) {
            b.sendMessage(message);
        }
    }

    private static String nameOf(BattleSession session, UUID id) {
        if (session != null) {
            return session.nameOf(id);
        }
        Player player = Bukkit.getPlayer(id);
        return player != null ? player.getName() : id.toString().substring(0, 8);
    }

    private Component button(String label, NamedTextColor color, String command, String hover) {
        return Component.text(label, color)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

}
