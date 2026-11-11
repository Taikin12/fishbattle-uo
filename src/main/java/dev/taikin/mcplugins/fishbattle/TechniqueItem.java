package dev.taikin.mcplugins.fishbattle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class TechniqueItem {

    private TechniqueItem() {
    }

    public static ItemStack create(Technique technique) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(FishKeys.TECHNIQUES, PersistentDataType.STRING, technique.id());
            meta.customName(Component.text("技カード: " + technique.name(), technique.color())
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    line("威力 x" + String.format("%.2f", technique.bonus()), technique.color()),
                    line(technique.description(), NamedTextColor.GRAY),
                    line("魚を持って右クリックで装備", NamedTextColor.DARK_GRAY)));
        });
        return item;
    }

    public static Technique read(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(FishKeys.TECHNIQUES, PersistentDataType.STRING);
        return id == null ? null : Technique.byId(id);
    }

    public static ItemStack random() {
        List<Technique> all = new ArrayList<>(Technique.all());
        return create(all.get(ThreadLocalRandom.current().nextInt(all.size())));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
