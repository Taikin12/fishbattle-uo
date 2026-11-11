package dev.taikin.mcplugins.fishbattle;

public record Fish(String id, FishType type, String speciesKey, int level, int exp, int hp, String nick,
        java.util.List<String> techniques) {

    public Fish {
        if (techniques == null) {
            techniques = java.util.List.of();
        } else {
            techniques = java.util.List.copyOf(techniques);
        }
    }

    public java.util.List<Technique> moves() {
        java.util.List<Technique> result = new java.util.ArrayList<>();
        for (String id : techniques) {
            Technique technique = Technique.byId(id);
            if (technique != null) {
                result.add(technique);
            }
        }
        return result;
    }

    public Fish withTechniques(java.util.List<String> ids) {
        return new Fish(id, type, speciesKey, level, exp, hp, nick, ids);
    }

    public String displayName() {
        return nick == null || nick.isBlank() ? species().name() : nick;
    }

    public FishSpecies species() {
        return FishSpecies.byKey(speciesKey);
    }

    public int maxHp() {
        return maxHp(species(), level);
    }

    public int power() {
        return species().power() + level;
    }

    public int technique() {
        return species().technique() + level / 2;
    }

    public int nextExp() {
        return level * 20;
    }

    public Fish withHp(int newHp) {
        return new Fish(id, type, speciesKey, level, exp, Math.max(0, Math.min(newHp, maxHp())), nick, techniques);
    }

    public static final int MAX_LEVEL = 10;

    public Fish gainExp(int amount) {
        int newLevel = level;
        int newExp = exp + amount;
        while (newLevel < MAX_LEVEL && newExp >= newLevel * 20) {
            newExp -= newLevel * 20;
            newLevel++;
        }
        if (newLevel >= MAX_LEVEL) {
            newExp = 0;
        }
        int newHp = newLevel > level ? maxHp(species(), newLevel) : hp;
        return new Fish(id, type, speciesKey, newLevel, newExp, newHp, nick, techniques);
    }

    public static int maxHp(FishSpecies species, int level) {
        return species.hp() + (level - 1) * 2;
    }
}
