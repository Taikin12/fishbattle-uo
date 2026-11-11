package dev.taikin.mcplugins.fishbattle;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class BattlePets {

    private final FishMotion motion;

    BattlePets(FishMotion motion) {
        this.motion = motion;
    }

    void freeze(BattleSession session, UUID playerId, Entity pet) {
        if (pet == null) {
            return;
        }
        session.setFollowState(playerId, motion.follows(pet));
        motion.setFollow(pet, false);
    }

    void place(Entity pet, Location location, Vector facing) {
        if (pet == null) {
            return;
        }
        Location placed = location.clone();
        placed.setDirection(facing);
        placed.setPitch(0f);
        motion.place(pet, placed);
    }

    void write(BattleSession session, UUID playerId, Fish fish) {
        if (session.isDemo()) {
            return;
        }
        Entity entity = entityById(session.petOf(playerId));
        if (entity != null && entity.isValid()) {
            FishData.write(entity, fish);
        }
    }

    void restore(BattleSession session, UUID playerId) {
        Entity entity = entityById(session.petOf(playerId));
        if (entity != null) {
            motion.setFollow(entity, session.followStateOf(playerId));
        }
    }

    void faceEachOther(BattleSession session) {
        Entity petA = entityById(session.petOf(session.playerA()));
        Entity petB = entityById(session.petOf(session.playerB()));
        Entity npcPet = entityById(session.npcPetId());

        if (petA != null && npcPet != null) {
            face(petA, npcPet.getLocation());
            face(npcPet, petA.getLocation());
        } else if (petA != null && petB != null) {
            face(petA, petB.getLocation());
            face(petB, petA.getLocation());
        }
    }

    void faceVillager(BattleSession session) {
        Entity villager = entityById(session.villagerId());
        Player player = Bukkit.getPlayer(session.playerA());
        if (villager != null && player != null) {
            face(villager, player.getLocation());
        }
    }

    Entity petEntity(BattleSession session, UUID playerId) {
        Entity pet = entityById(session.petOf(playerId));
        if (pet != null) {
            return pet;
        }
        Entity villager = entityById(session.villagerId());
        if (villager != null) {
            return villager;
        }
        Entity npcPet = entityById(session.npcPetId());
        if (npcPet != null) {
            return npcPet;
        }
        return Bukkit.getPlayer(playerId);
    }

    static Entity entityById(UUID id) {
        return id == null ? null : Bukkit.getEntity(id);
    }

    private static void face(Entity entity, Location target) {
        Location from = entity.getLocation();
        Vector direction = target.toVector().subtract(from.toVector()).setY(0);
        if (direction.lengthSquared() < 1.0E-6) {
            return;
        }
        from.setDirection(direction);
        from.setPitch(0f);
        entity.teleport(from);
    }
}
