package dev.taikin.mcplugins.fishbattle;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class FishKeys {

    public static NamespacedKey CAPTURE;
    public static NamespacedKey ID;
    public static NamespacedKey SPECIES;
    public static NamespacedKey TYPE;
    public static NamespacedKey LEVEL;
    public static NamespacedKey EXP;
    public static NamespacedKey HP;
    public static NamespacedKey ANCHOR_X;
    public static NamespacedKey ANCHOR_Y;
    public static NamespacedKey ANCHOR_Z;
    public static NamespacedKey OWNER;
    public static NamespacedKey NICK;
    public static NamespacedKey FOLLOW;
    public static NamespacedKey TECHNIQUES;
    public static NamespacedKey DEMO;
    public static NamespacedKey CAUGHT;

    private FishKeys() {
    }

    public static void init(Plugin plugin) {
        CAPTURE = new NamespacedKey(plugin, "capture_bucket");
        ID = new NamespacedKey(plugin, "fish_id");
        SPECIES = new NamespacedKey(plugin, "species");
        TYPE = new NamespacedKey(plugin, "type");
        LEVEL = new NamespacedKey(plugin, "level");
        EXP = new NamespacedKey(plugin, "exp");
        HP = new NamespacedKey(plugin, "hp");
        ANCHOR_X = new NamespacedKey(plugin, "anchor_x");
        ANCHOR_Y = new NamespacedKey(plugin, "anchor_y");
        ANCHOR_Z = new NamespacedKey(plugin, "anchor_z");
        OWNER = new NamespacedKey(plugin, "owner");
        NICK = new NamespacedKey(plugin, "nick");
        FOLLOW = new NamespacedKey(plugin, "follow");
        TECHNIQUES = new NamespacedKey(plugin, "techniques");
        DEMO = new NamespacedKey(plugin, "demo");
        CAUGHT = new NamespacedKey(plugin, "caught");
    }
}
