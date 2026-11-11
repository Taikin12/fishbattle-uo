package dev.taikin.mcplugins.fishbattle;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Rps {
    GU("グー", "✊", FishType.BLUE),
    CHOKI("チョキ", "✌", FishType.RED),
    PA("パー", "✋", FishType.YELLOW);

    private final String label;
    private final String emoji;
    private final FishType type;

    Rps(String label, String emoji, FishType type) {
        this.label = label;
        this.emoji = emoji;
        this.type = type;
    }

    public FishType type() {
        return type;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }

    public NamedTextColor color() {
        return type.color();
    }

    public boolean beats(Rps other) {
        return (this == GU && other == CHOKI)
                || (this == CHOKI && other == PA)
                || (this == PA && other == GU);
    }
}
