package dev.taikin.mcplugins.fishbattle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class FishMotion {

    private static final double TELEPORT_RANGE = 16.0;
    private static final double STOP_RANGE = 2.0;
    private static final double BASE_SPEED = 0.14 / 3.0;
    private static final double SPEED_GAIN = 0.16 / 3.0;
    private static final double MAX_SPEED = 1.10 / 3.0;

    private final Plugin plugin;
    private final Set<UUID> tracked = new HashSet<>();
    private BukkitTask task;
    private long tick;

    public FishMotion(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::step, 20L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tracked.clear();
    }

    public void track(Entity entity) {
        if (!hasData(entity)) {
            return;
        }
        ensureAnchor(entity);
        configure(entity);
        tracked.add(entity.getUniqueId());
    }

    public void untrack(Entity entity) {
        tracked.remove(entity.getUniqueId());
    }

    public boolean setFollow(Entity entity, boolean follow) {
        var pdc = pdc(entity);
        pdc.set(FishKeys.FOLLOW, PersistentDataType.BYTE, (byte) (follow ? 1 : 0));
        if (!follow) {
            Location loc = entity.getLocation();
            pdc.set(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE, loc.getZ());
        }
        return follow;
    }

    public boolean follows(Entity entity) {
        Byte value = pdc(entity).get(FishKeys.FOLLOW, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    public void place(Entity entity, Location location) {
        entity.teleport(location);
        var pdc = pdc(entity);
        pdc.set(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE, location.getX());
        pdc.set(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE, location.getY());
        pdc.set(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE, location.getZ());
    }

    public boolean toggleFollow(Entity entity) {
        var pdc = pdc(entity);
        boolean follow = !pdc.has(FishKeys.FOLLOW, PersistentDataType.BYTE)
                || pdc.get(FishKeys.FOLLOW, PersistentDataType.BYTE) != 0;
        boolean next = !follow;
        pdc.set(FishKeys.FOLLOW, PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        if (!next) {
            Location loc = entity.getLocation();
            pdc.set(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE, loc.getZ());
        }
        return next;
    }

    public Entity petOf(Player player) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.getWorld().getEntities()) {
            if (!hasData(entity)) {
                continue;
            }
            String owner = pdc(entity).get(FishKeys.OWNER, PersistentDataType.STRING);
            if (owner == null || !owner.equals(player.getUniqueId().toString())) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    public int clear(UUID owner) {
        int removed = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!hasData(entity)) {
                    continue;
                }
                String entityOwner = pdc(entity).get(FishKeys.OWNER, PersistentDataType.STRING);
                if (owner != null && (entityOwner == null || !entityOwner.equals(owner.toString()))) {
                    continue;
                }
                tracked.remove(entity.getUniqueId());
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public void trackLoaded() {
        for (var world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (hasData(entity)) {
                    track(entity);
                }
            }
        }
    }

    private void step() {
        tick++;
        tracked.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid();
        });

        for (UUID id : new ArrayList<>(tracked)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || !entity.isValid()) {
                continue;
            }
            configure(entity);
            double phase = (id.hashCode() & 0xFF) / 255.0 * Math.PI * 2.0;
            double t = tick * 0.053 + phase;
            double bob = Math.sin(t * 1.3) * 0.10;

            Player owner = ownerOf(entity);
            boolean follows = pdc(entity).get(FishKeys.FOLLOW, PersistentDataType.BYTE) == null
                    || pdc(entity).get(FishKeys.FOLLOW, PersistentDataType.BYTE) != 0;
            if (follows && owner != null && owner.isOnline() && owner.getWorld().equals(entity.getWorld())) {
                Location dest = followTarget(owner, bob);
                Location current = entity.getLocation();
                double distance = current.distance(dest);
                Location next;
                if (distance > TELEPORT_RANGE) {
                    next = dest;
                } else if (distance <= STOP_RANGE) {
                    next = current.clone();
                } else {
                    double speed = Math.min(MAX_SPEED, BASE_SPEED + (distance - STOP_RANGE) * SPEED_GAIN);
                    Vector delta = dest.toVector().subtract(current.toVector());
                    if (delta.lengthSquared() > 1.0E-6) {
                        delta.normalize().multiply(speed);
                    }
                    next = current.clone().add(delta);
                }
                next.setYaw(org.bukkit.Location.normalizeYaw(owner.getLocation().getYaw() + 180f));
                next.setPitch(0f);
                if (next.getBlock().isPassable()) {
                    move(entity, current, next);
                }
                continue;
            }

            Double ax = pdc(entity).get(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE);
            Double ay = pdc(entity).get(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE);
            Double az = pdc(entity).get(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE);
            if (ax == null || ay == null || az == null) {
                ensureAnchor(entity);
                continue;
            }
            Location current = entity.getLocation();
            Location target = new Location(entity.getWorld(),
                    ax + Math.sin(t) * 0.35,
                    ay + bob,
                    az + Math.cos(t * 0.9) * 0.35,
                    current.getYaw(), current.getPitch());
            move(entity, current, target);
        }
    }

    private static void move(Entity entity, Location current, Location next) {
        if (current.getWorld() != next.getWorld()
                || current.distanceSquared(next) > 1.0E-7) {
            entity.teleport(next);
        }
    }

    private static Location followTarget(Player owner, double bob) {
        Location base = owner.getLocation();
        Location target = base.clone();
        target.setY(base.getY() + 1.15 + bob);
        return target;
    }

    private static Player ownerOf(Entity entity) {
        String owner = pdc(entity).get(FishKeys.OWNER, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(owner));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void configure(Entity entity) {
        Fish data = FishData.read(entity);
        if (data != null) {
            entity.customName(FishItems.name(data));
        }
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setPersistent(true);
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
            living.setAI(false);
        }
        if (entity instanceof Axolotl axolotl) {
            axolotl.setPlayingDead(false);
        }
    }

    private static void ensureAnchor(Entity entity) {
        var pdc = pdc(entity);
        if (!pdc.has(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE)) {
            Location loc = entity.getLocation();
            pdc.set(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE, loc.getZ());
        }
    }

    private static org.bukkit.persistence.PersistentDataContainer pdc(Entity entity) {
        return entity.getPersistentDataContainer();
    }

    public static boolean hasData(Entity entity) {
        return entity.getPersistentDataContainer().has(FishKeys.ID, PersistentDataType.STRING);
    }
}
