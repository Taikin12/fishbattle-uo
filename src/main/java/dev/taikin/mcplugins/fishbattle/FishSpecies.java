package dev.taikin.mcplugins.fishbattle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.TropicalFish;

public record FishSpecies(String key, String name, int hp, int power, int technique, String special, String superSpecial) {

    private static final Map<String, FishSpecies> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("cod", new FishSpecies("cod", "タラ", 60, 45, 40, "大津波", "海神の怒り"));
        REGISTRY.put("salmon", new FishSpecies("salmon", "サケ", 55, 60, 55, "遡上の一撃", "火の粉乱舞"));
        REGISTRY.put("pufferfish", new FishSpecies("pufferfish", "フグ", 65, 55, 30, "トゲトゲバースト", "猛毒大噴射"));

        pattern(TropicalFish.Pattern.KOB, "コブダイ", 55, 50, 40, "コブアタック", "岩塊プレス");
        pattern(TropicalFish.Pattern.SUNSTREAK, "スジモヨウ", 45, 55, 60, "スジビーム", "光条乱射");
        pattern(TropicalFish.Pattern.SNOOPER, "ハナビレ", 50, 45, 45, "ハナビレソード", "突貫フィン");
        pattern(TropicalFish.Pattern.DASHER, "セイブン", 40, 60, 70, "セイブンダッシュ", "音速突進");
        pattern(TropicalFish.Pattern.BRINELY, "ブリナ", 60, 45, 40, "ブリナウェーブ", "大海嘯");
        pattern(TropicalFish.Pattern.SPOTTY, "スポッティ", 50, 50, 50, "スポットショット", "斑点乱舞");
        pattern(TropicalFish.Pattern.FLOPPER, "フロッパー", 65, 40, 35, "フロップタックル", "大跳躍プレス");
        pattern(TropicalFish.Pattern.STRIPEY, "シマシマ", 45, 55, 55, "シマシマアタック", "縞模様ラッシュ");
        pattern(TropicalFish.Pattern.GLITTER, "キラキラ", 40, 50, 75, "キラキラフラッシュ", "眩惑の輝き");
        pattern(TropicalFish.Pattern.BLOCKFISH, "ブロック", 70, 55, 25, "ブロックプレス", "鉄壁の巨体");
        pattern(TropicalFish.Pattern.BETTY, "ベティ", 50, 65, 50, "ベティクロー", "華麗なる連撃");
        pattern(TropicalFish.Pattern.CLAYFISH, "クレイ", 60, 60, 30, "クレイバスター", "大地砕き");

        axolotl(Axolotl.Variant.LUCY, "ルーシー", 55, 50, 50, "ルーシービーム", "ピンクの輝き");
        axolotl(Axolotl.Variant.WILD, "ワイルド", 50, 55, 45, "ワイルドバイト", "野生の咆哮");
        axolotl(Axolotl.Variant.GOLD, "ゴールド", 60, 60, 45, "ゴールドラッシュ", "黄金の一撃");
        axolotl(Axolotl.Variant.CYAN, "シアン", 45, 55, 60, "シアンソニック", "超音波攻撃");
        axolotl(Axolotl.Variant.BLUE, "ブルー", 50, 45, 70, "ブルーストリーム", "蒼海の渦");
    }

    private static void pattern(TropicalFish.Pattern pattern, String name, int hp, int power, int technique,
            String special, String superSpecial) {
        REGISTRY.put("tropical:" + pattern.name(),
                new FishSpecies("tropical:" + pattern.name(), name, hp, power, technique, special, superSpecial));
    }

    private static void axolotl(Axolotl.Variant variant, String name, int hp, int power, int technique,
            String special, String superSpecial) {
        REGISTRY.put("axolotl:" + variant.name(),
                new FishSpecies("axolotl:" + variant.name(), name, hp, power, technique, special, superSpecial));
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
