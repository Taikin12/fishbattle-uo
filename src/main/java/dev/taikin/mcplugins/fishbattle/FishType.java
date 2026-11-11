package dev.taikin.mcplugins.fishbattle;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;

public enum FishType {
    BLUE("✊グー", "✊", NamedTextColor.BLUE, DyeColor.BLUE),
    RED("✌チョキ", "✌", NamedTextColor.RED, DyeColor.RED),
    YELLOW("✋パー", "✋", NamedTextColor.YELLOW, DyeColor.YELLOW);

    private final String label;
    private final String emoji;
    private final NamedTextColor color;
    private final DyeColor dye;

    FishType(String label, String emoji, NamedTextColor color, DyeColor dye) {
        this.label = label;
        this.emoji = emoji;
        this.color = color;
        this.dye = dye;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return color;
    }

    public DyeColor dye() {
        return dye;
    }

    public Rps hand() {
        return switch (this) {
            case BLUE -> Rps.GU;
            case RED -> Rps.CHOKI;
            case YELLOW -> Rps.PA;
        };
    }

    public FishType beats() {
        return switch (this) {
            case BLUE -> RED;
            case RED -> YELLOW;
            case YELLOW -> BLUE;
        };
    }

    public double multiplier(FishType defender) {
        if (beats() == defender) {
            return 1.3;
        }
        if (defender.beats() == this) {
            return 0.75;
        }
        return 1.0;
    }

    private static final FishType[] VALUES = values();

    public static FishType random() {
        return VALUES[java.util.concurrent.ThreadLocalRandom.current().nextInt(VALUES.length)];
    }

    public static FishType fromDyeColor(DyeColor color) {
        return switch (color) {
            case RED, ORANGE, PINK, MAGENTA, BROWN -> RED;
            case YELLOW, LIME, WHITE, LIGHT_GRAY -> YELLOW;
            case BLUE, CYAN, LIGHT_BLUE, GREEN, PURPLE, BLACK, GRAY -> BLUE;
        };
    }
}
