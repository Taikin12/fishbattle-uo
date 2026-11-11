package dev.taikin.mcplugins.fishbattle;

import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class FishData {

    private FishData() {
    }

    public static Fish read(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String id = pdc.get(FishKeys.ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        String species = pdc.get(FishKeys.SPECIES, PersistentDataType.STRING);
        String typeName = pdc.get(FishKeys.TYPE, PersistentDataType.STRING);
        Integer level = pdc.get(FishKeys.LEVEL, PersistentDataType.INTEGER);
        if (species == null || typeName == null || level == null) {
            return null;
        }
        FishType type;
        try {
            type = FishType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = FishType.BLUE;
        }
        Integer exp = pdc.get(FishKeys.EXP, PersistentDataType.INTEGER);
        Integer hp = pdc.get(FishKeys.HP, PersistentDataType.INTEGER);
        String nick = pdc.get(FishKeys.NICK, PersistentDataType.STRING);
        String techniquesRaw = pdc.get(FishKeys.TECHNIQUES, PersistentDataType.STRING);
        int max = Fish.maxHp(FishSpecies.byKey(species), level);
        return new Fish(id, type, species, level, exp == null ? 0 : exp,
                hp == null ? max : Math.min(hp, max), nick == null ? "" : nick,
                FishItems.parseTechniques(techniquesRaw));
    }

    public static void write(Entity entity, Fish fish) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(FishKeys.ID, PersistentDataType.STRING, fish.id());
        pdc.set(FishKeys.SPECIES, PersistentDataType.STRING, fish.speciesKey());
        pdc.set(FishKeys.TYPE, PersistentDataType.STRING, fish.type().name());
        pdc.set(FishKeys.LEVEL, PersistentDataType.INTEGER, fish.level());
        pdc.set(FishKeys.EXP, PersistentDataType.INTEGER, fish.exp());
        pdc.set(FishKeys.HP, PersistentDataType.INTEGER, fish.hp());
        if (fish.nick() != null && !fish.nick().isBlank()) {
            pdc.set(FishKeys.NICK, PersistentDataType.STRING, fish.nick());
        } else {
            pdc.remove(FishKeys.NICK);
        }
        if (fish.techniques().isEmpty()) {
            pdc.remove(FishKeys.TECHNIQUES);
        } else {
            pdc.set(FishKeys.TECHNIQUES, PersistentDataType.STRING, String.join(",", fish.techniques()));
        }
        entity.customName(FishItems.name(fish));
        entity.setCustomNameVisible(true);
    }
}
