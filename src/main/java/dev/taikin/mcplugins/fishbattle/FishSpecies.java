package dev.taikin.mcplugins.fishbattle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.TropicalFish;

public record FishSpecies(String key, String name, int hp, int power, int technique) {

    private static final Map<String, FishSpecies> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("cod", new FishSpecies("cod", "タラ", 60, 45, 40));
        REGISTRY.put("salmon", new FishSpecies("salmon", "サケ", 55, 60, 55));
        REGISTRY.put("pufferfish", new FishSpecies("pufferfish", "フグ", 65, 55, 30));

        pattern(TropicalFish.Pattern.KOB, "コブダイ", 55, 50, 40);
        pattern(TropicalFish.Pattern.SUNSTREAK, "スジモヨウ", 45, 55, 60);
        pattern(TropicalFish.Pattern.SNOOPER, "ハナビレ", 50, 45, 45);
        pattern(TropicalFish.Pattern.DASHER, "セイブン", 40, 60, 70);
        pattern(TropicalFish.Pattern.BRINELY, "ブリナ", 60, 45, 40);
        pattern(TropicalFish.Pattern.SPOTTY, "スポッティ", 50, 50, 50);
        pattern(TropicalFish.Pattern.FLOPPER, "フロッパー", 65, 40, 35);
        pattern(TropicalFish.Pattern.STRIPEY, "シマシマ", 45, 55, 55);
        pattern(TropicalFish.Pattern.GLITTER, "キラキラ", 40, 50, 75);
        pattern(TropicalFish.Pattern.BLOCKFISH, "ブロック", 70, 55, 25);
        pattern(TropicalFish.Pattern.BETTY, "ベティ", 50, 65, 50);
        pattern(TropicalFish.Pattern.CLAYFISH, "クレイ", 60, 60, 30);

        axolotl(Axolotl.Variant.LUCY, "ルーシー", 55, 50, 50);
        axolotl(Axolotl.Variant.WILD, "ワイルド", 50, 55, 45);
        axolotl(Axolotl.Variant.GOLD, "ゴールド", 60, 60, 45);
        axolotl(Axolotl.Variant.CYAN, "シアン", 45, 55, 60);
        axolotl(Axolotl.Variant.BLUE, "ブルー", 50, 45, 70);
    }

    private static void pattern(TropicalFish.Pattern pattern, String name, int hp, int power, int technique) {
        REGISTRY.put("tropical:" + pattern.name(),
                new FishSpecies("tropical:" + pattern.name(), name, hp, power, technique));
    }

    private static void axolotl(Axolotl.Variant variant, String name, int hp, int power, int technique) {
        REGISTRY.put("axolotl:" + variant.name(),
                new FishSpecies("axolotl:" + variant.name(), name, hp, power, technique));
    }

    public static FishSpecies byKey(String key) {
        FishSpecies species = REGISTRY.get(key);
        return species != null ? species : REGISTRY.get("tropical:KOB");
    }

    public static String keyOf(TropicalFish.Pattern pattern) {
        return "tropical:" + pattern.name();
    }

    public static String keyOf(Axolotl.Variant variant) {
        return "axolotl:" + variant.name();
    }

    public static FishType typeOf(Axolotl.Variant variant) {
        return switch (variant) {
            case LUCY, WILD -> FishType.RED;
            case GOLD -> FishType.YELLOW;
            case CYAN, BLUE -> FishType.BLUE;
        };
    }

    public static FishType typeOfKey(String key) {
        return switch (key) {
            case "cod" -> FishType.BLUE;
            case "salmon" -> FishType.RED;
            case "pufferfish" -> FishType.YELLOW;
            default -> FishType.BLUE;
        };
    }
}
