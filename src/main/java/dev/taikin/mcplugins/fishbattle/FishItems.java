package dev.taikin.mcplugins.fishbattle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.persistence.PersistentDataType;

public final class FishItems {

    private FishItems() {
    }

    public static ItemStack captureBucket() {
        ItemStack item = new ItemStack(Material.BUCKET);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(FishKeys.CAPTURE, PersistentDataType.BYTE, (byte) 1);
            meta.customName(Component.text("捕獲バケツ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    line("魚やウーパールーパーを右クリックですくう", NamedTextColor.GRAY),
                    line("バトル用の個体になる", NamedTextColor.GRAY)));
        });
        return item;
    }

    public static boolean isCaptureBucket(ItemStack item) {
        if (item == null || item.getType() != Material.BUCKET) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(FishKeys.CAPTURE, PersistentDataType.BYTE);
    }

    public static boolean isFish(ItemStack item) {
        if (item == null) {
            return false;
        }
        if (!isFishBucketMaterial(item.getType())) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(FishKeys.ID, PersistentDataType.STRING);
    }

    public static boolean isFishBucketMaterial(Material material) {
        return material == Material.TROPICAL_FISH_BUCKET
                || material == Material.AXOLOTL_BUCKET
                || material == Material.COD_BUCKET
                || material == Material.SALMON_BUCKET
                || material == Material.PUFFERFISH_BUCKET;
    }

    public static Fish read(ItemStack item) {
        if (!isFish(item)) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(FishKeys.ID, PersistentDataType.STRING);
        String species = pdc.get(FishKeys.SPECIES, PersistentDataType.STRING);
        String typeName = pdc.get(FishKeys.TYPE, PersistentDataType.STRING);
        Integer level = pdc.get(FishKeys.LEVEL, PersistentDataType.INTEGER);
        Integer exp = pdc.get(FishKeys.EXP, PersistentDataType.INTEGER);
        Integer hp = pdc.get(FishKeys.HP, PersistentDataType.INTEGER);
        String nick = pdc.get(FishKeys.NICK, PersistentDataType.STRING);
        String techniquesRaw = pdc.get(FishKeys.TECHNIQUES, PersistentDataType.STRING);
        if (id == null || species == null || typeName == null || level == null) {
            return null;
        }
        FishType type;
        try {
            type = FishType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = FishType.BLUE;
        }
        int max = Fish.maxHp(FishSpecies.byKey(species), level);
        return new Fish(id, type, species, level, exp == null ? 0 : exp,
                hp == null ? max : Math.min(hp, max), nick == null ? "" : nick, parseTechniques(techniquesRaw));
    }

    public static void write(ItemStack item, Fish fish) {
        item.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
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
                pdc.set(FishKeys.TECHNIQUES, PersistentDataType.STRING,
                        String.join(",", fish.techniques()));
            }
            meta.customName(name(fish));
            meta.lore(lore(fish));
        });
    }

    public static ItemStack bucketFor(Fish fish) {
        ItemStack item = createBucket(fish.speciesKey(), fish.type());
        write(item, fish);
        return item;
    }

    public static ItemStack randomBucket(FishType type, int level) {
        String[] keys = switch (type) {
            case RED -> new String[] {"salmon",
                    FishSpecies.keyOf(TropicalFish.Pattern.KOB),
                    FishSpecies.keyOf(TropicalFish.Pattern.BETTY),
                    FishSpecies.keyOf(Axolotl.Variant.LUCY)};
            case YELLOW -> new String[] {"pufferfish",
                    FishSpecies.keyOf(TropicalFish.Pattern.SUNSTREAK),
                    FishSpecies.keyOf(TropicalFish.Pattern.DASHER),
                    FishSpecies.keyOf(Axolotl.Variant.GOLD)};
            case BLUE -> new String[] {"cod",
                    FishSpecies.keyOf(TropicalFish.Pattern.FLOPPER),
                    FishSpecies.keyOf(TropicalFish.Pattern.STRIPEY),
                    FishSpecies.keyOf(Axolotl.Variant.BLUE)};
        };
        String key = keys[ThreadLocalRandom.current().nextInt(keys.length)];
        FishSpecies species = FishSpecies.byKey(key);
        Fish fish = new Fish(java.util.UUID.randomUUID().toString(), type, key, level, 0,
                Fish.maxHp(species, level), "", java.util.List.of());
        return bucketFor(fish);
    }

    public static Component name(Fish fish) {
        return Component.text(fish.displayName(), NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component battleName(Fish fish) {
        return Component.text()
                .append(Component.text("[" + fish.type().emoji() + "]", fish.type().color()))
                .append(Component.text(fish.displayName(), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false)
                .build();
    }

    public static List<Component> lore(Fish fish) {
        return List.of(
                line("属性: " + fish.type().label(), fish.type().color()),
                line("HP: " + fish.hp() + " / " + fish.maxHp(), NamedTextColor.RED),
                line("強さ " + fish.power() + "  体力 " + fish.maxHp() + "  テクニック " + fish.technique(),
                        NamedTextColor.GRAY),
                line("技: " + techniqueSummary(fish), NamedTextColor.AQUA),
                line("Lv" + fish.level() + " / つぎまで "
                        + Math.max(0, fish.nextExp() - fish.exp()) + " EXP", NamedTextColor.AQUA));
    }

    private static ItemStack createBucket(String speciesKey, FishType type) {
        if (speciesKey.equals("cod")) {
            return new ItemStack(Material.COD_BUCKET);
        }
        if (speciesKey.equals("salmon")) {
            return new ItemStack(Material.SALMON_BUCKET);
        }
        if (speciesKey.equals("pufferfish")) {
            return new ItemStack(Material.PUFFERFISH_BUCKET);
        }
        if (speciesKey.startsWith("axolotl:")) {
            Axolotl.Variant variant = Axolotl.Variant.valueOf(speciesKey.substring("axolotl:".length()));
            ItemStack item = new ItemStack(Material.AXOLOTL_BUCKET);
            item.editMeta(AxolotlBucketMeta.class, meta -> meta.setVariant(variant));
            return item;
        }
        TropicalFish.Pattern pattern = TropicalFish.Pattern.valueOf(speciesKey.substring("tropical:".length()));
        ItemStack item = new ItemStack(Material.TROPICAL_FISH_BUCKET);
        item.editMeta(TropicalFishBucketMeta.class, meta -> {
            meta.setPattern(pattern);
            meta.setBodyColor(type.dye());
            meta.setPatternColor(org.bukkit.DyeColor.WHITE);
        });
        return item;
    }

    public static java.util.List<String> parseTechniques(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && Technique.byId(id) != null) {
                ids.add(id);
                if (ids.size() >= 3) {
                    break;
                }
            }
        }
        return ids;
    }

    public static String techniqueSummary(Fish fish) {
        java.util.List<Technique> moves = fish.moves();
        if (moves.isEmpty()) {
            return "なし";
        }
        StringBuilder builder = new StringBuilder();
        for (Technique move : moves) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(move.name()).append("(").append(String.format("%.2f", move.bonus())).append("x)");
        }
        return builder.toString();
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
