package dev.taikin.mcplugins.fishbattle;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;

public record Technique(String id, String name, double bonus, String description) {

    private static final List<Technique> REGISTRY = new ArrayList<>();

    static {
        add("tackle", "たいあたり", 1.10, "基本の体当たり");
        add("bite", "かみつき", 1.25, "鋭い牙で噛みつく");
        add("tail", "しっぽビンタ", 1.20, "しっぽで張り倒す");
        add("splash", "はねあげ", 1.15, "水しぶきを上げる");
        add("dash", "とっしん", 1.35, "勢いよく突進する");
        add("spin", "スピンアタック", 1.30, "回転しながら体当たり");
        add("bubble", "バブルビーム", 1.45, "泡を撃ち出す");
        add("wave", "ビッグウェーブ", 1.60, "大波で押し流す");
        add("thunder", "かみなり", 1.70, "雷を落とす");
        add("blaze", "ごうかブレイズ", 1.85, "炎をまとって突撃");
        add("frost", "こおりづめ", 1.55, "冷気で凍らせる");
        add("poison", "もうどくばり", 1.50, "毒針を突き刺す");
        add("guard", "てっぺき", 1.00, "防御を固める");
        add("heal", "いやしのしずく", 1.05, "体力を少し回復する");
        add("lucky", "ラッキーヒット", 1.40, "運任せの一撃");
        add("finish", "とどめの一撃", 2.00, "勝負を決める大技");
    }

    private static void add(String id, String name, double bonus, String description) {
        REGISTRY.add(new Technique(id, name, bonus, description));
    }

    public static List<Technique> all() {
        return REGISTRY;
    }

    public static Technique byId(String id) {
        for (Technique technique : REGISTRY) {
            if (technique.id().equalsIgnoreCase(id)) {
                return technique;
            }
        }
        return null;
    }

    public NamedTextColor color() {
        if (bonus >= 1.8) {
            return NamedTextColor.DARK_PURPLE;
        }
        if (bonus >= 1.45) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        if (bonus >= 1.25) {
            return NamedTextColor.AQUA;
        }
        return NamedTextColor.GRAY;
    }
}
