package dev.taikin.mcplugins.fishbattle;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.scheduler.BukkitTask;

public final class BattleSession {

    public enum Phase {
        IDLE,
        COUNTDOWN,
        MASHING,
        CHOOSING,
        ATTACKING,
        FINISHED
    }

    private final UUID a;
    private final UUID b;
    private Fish fishA;
    private Fish fishB;
    private Phase phase = Phase.IDLE;
    private boolean closed;
    private Rps choiceA;
    private Rps choiceB;
    private List<Rps> order = List.of(Rps.GU, Rps.CHOKI, Rps.PA);
    private Rps previewA;
    private Rps previewB;
    private UUID attacker;
    private int clicksA;
    private int clicksB;
    private double chargeA;
    private double chargeB;
    private long lastMashAtA;
    private long lastMashAtB;
    private float savedExpA;
    private float savedExpB;
    private int savedLevelA;
    private int savedLevelB;
    private BossBar oppBarA;
    private BossBar oppBarB;
    private BossBar selfBarA;
    private BossBar selfBarB;
    private UUID npcId;
    private String npcName;
    private boolean demo;
    private boolean resultGrace;
    private UUID villagerId;
    private UUID npcPet;
    private UUID petA;
    private UUID petB;
    private boolean followStateA = true;
    private boolean followStateB = true;
    private BukkitTask npcTask;
    private BukkitTask meterTask;
    private BukkitTask choiceTask;
    private org.bukkit.Location center;
    private double stake;
    private int round = 1;
    private int specialStreakA;
    private int specialStreakB;

    public BattleSession(UUID a, UUID b, Fish fishA, Fish fishB) {
        this.a = a;
        this.b = b;
        this.fishA = fishA;
        this.fishB = fishB;
    }

    public UUID npcId() {
        return npcId;
    }

    public void setNpc(UUID npcId, String npcName) {
        this.npcId = npcId;
        this.npcName = npcName;
    }

    public boolean isNpc(UUID player) {
        return npcId != null && npcId.equals(player);
    }

    public String npcName() {
        return npcName;
    }

    public String nameOf(UUID player) {
        if (npcId != null && npcId.equals(player)) {
            return npcName != null ? npcName : "NPC";
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        return "相手";
    }

    public boolean isDemo() {
        return demo;
    }

    public boolean resultGrace() {
        return resultGrace;
    }

    public void setResultGrace(boolean grace) {
        this.resultGrace = grace;
    }

    public boolean followStateOf(UUID player) {
        return player.equals(a) ? followStateA : followStateB;
    }

    public void setFollowState(UUID player, boolean state) {
        if (player.equals(a)) {
            followStateA = state;
        } else {
            followStateB = state;
        }
    }

    public UUID petOf(UUID player) {
        return player.equals(a) ? petA : petB;
    }

    public void setPet(UUID player, UUID pet) {
        if (player.equals(a)) {
            petA = pet;
        } else {
            petB = pet;
        }
    }

    public UUID npcPetId() {
        return npcPet;
    }

    public void setNpcPetId(UUID id) {
        this.npcPet = id;
    }

    public UUID villagerId() {
        return villagerId;
    }

    public void setVillagerId(UUID villagerId) {
        this.villagerId = villagerId;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public BukkitTask npcTask() {
        return npcTask;
    }

    public void setNpcTask(BukkitTask task) {
        this.npcTask = task;
    }

    public UUID playerA() {
        return a;
    }

    public UUID playerB() {
        return b;
    }

    public UUID opponentOf(UUID player) {
        return player.equals(a) ? b : a;
    }

    public Fish fishOf(UUID player) {
        return player.equals(a) ? fishA : fishB;
    }

    public void setFish(UUID player, Fish fish) {
        if (player.equals(a)) {
            fishA = fish;
        } else {
            fishB = fish;
        }
    }

    public boolean closed() {
        return closed;
    }

    public void close() {
        this.closed = true;
    }

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public Rps choiceOf(UUID player) {
        return player.equals(a) ? choiceA : choiceB;
    }

    public void setChoice(UUID player, Rps choice) {
        if (player.equals(a)) {
            choiceA = choice;
        } else {
            choiceB = choice;
        }
    }

    public List<Rps> order() {
        return order;
    }

    public void setOrder(List<Rps> order) {
        this.order = order;
    }

    public Rps previewOf(UUID player) {
        return player.equals(a) ? previewA : previewB;
    }

    public void setPreview(UUID player, Rps hand) {
        if (player.equals(a)) {
            previewA = hand;
        } else {
            previewB = hand;
        }
    }

    public boolean bothChosen() {
        return choiceA != null && choiceB != null;
    }

    public void clearChoices() {
        choiceA = null;
        choiceB = null;
        previewA = null;
        previewB = null;
    }

    public UUID attacker() {
        return attacker;
    }

    public void setAttacker(UUID attacker) {
        this.attacker = attacker;
    }

    public int clicks(UUID player) {
        return player.equals(a) ? clicksA : clicksB;
    }

    public double charge(UUID player) {
        return player.equals(a) ? chargeA : chargeB;
    }

    public void resetMash() {
        this.clicksA = 0;
        this.clicksB = 0;
        this.chargeA = 0.0;
        this.chargeB = 0.0;
        this.lastMashAtA = 0L;
        this.lastMashAtB = 0L;
    }

    public boolean registerMash(UUID player, long now) {
        boolean isA = player.equals(a);
        long last = isA ? lastMashAtA : lastMashAtB;
        if (now - last < 20L) {
            return false;
        }
        if (isA) {
            lastMashAtA = now;
            clicksA++;
            chargeA = gain(chargeA);
        } else {
            lastMashAtB = now;
            clicksB++;
            chargeB = gain(chargeB);
        }
        return true;
    }

    public void decay(double amount) {
        chargeA = Math.max(0.0, chargeA - amount);
        chargeB = Math.max(0.0, chargeB - amount);
    }

    public void decayNpc(double amount) {
        if (npcId == null) {
            return;
        }
        if (npcId.equals(a)) {
            chargeA = Math.max(0.0, chargeA - amount);
        } else {
            chargeB = Math.max(0.0, chargeB - amount);
        }
    }

    private static double gain(double current) {
        double remaining = Math.max(0.0, 1.0 - current);
        double next = current + 0.28 * remaining * remaining;
        return Math.min(1.0, next);
    }

    public float savedExp(UUID player) {
        return player.equals(a) ? savedExpA : savedExpB;
    }

    public void setSavedExp(UUID player, float exp) {
        if (player.equals(a)) {
            savedExpA = exp;
        } else {
            savedExpB = exp;
        }
    }

    public int savedLevel(UUID player) {
        return player.equals(a) ? savedLevelA : savedLevelB;
    }

    public void setSavedLevel(UUID player, int level) {
        if (player.equals(a)) {
            savedLevelA = level;
        } else {
            savedLevelB = level;
        }
    }

    public BossBar selfBar(UUID viewer) {
        return viewer.equals(a) ? selfBarA : selfBarB;
    }

    public void setSelfBar(UUID viewer, BossBar bar) {
        if (viewer.equals(a)) {
            selfBarA = bar;
        } else {
            selfBarB = bar;
        }
    }

    public BossBar opponentBar(UUID viewer) {
        return viewer.equals(a) ? oppBarA : oppBarB;
    }

    public void setOpponentBar(UUID viewer, BossBar bar) {
        if (viewer.equals(a)) {
            oppBarA = bar;
        } else {
            oppBarB = bar;
        }
    }

    public BukkitTask choiceTask() {
        return choiceTask;
    }

    public void setChoiceTask(BukkitTask task) {
        this.choiceTask = task;
    }

    public double stake() {
        return stake;
    }

    public void setStake(double stake) {
        this.stake = stake;
    }

    public org.bukkit.Location center() {
        return center;
    }

    public void setCenter(org.bukkit.Location location) {
        this.center = location;
    }

    public BukkitTask meterTask() {
        return meterTask;
    }

    public void setMeterTask(BukkitTask task) {
        this.meterTask = task;
    }

    public int specialStreak(UUID player) {
        return player.equals(a) ? specialStreakA : specialStreakB;
    }

    public void setSpecialStreak(UUID player, int value) {
        if (player.equals(a)) {
            specialStreakA = value;
        } else {
            specialStreakB = value;
        }
    }

    public int round() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

}
