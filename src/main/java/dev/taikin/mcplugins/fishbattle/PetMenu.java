package dev.taikin.mcplugins.fishbattle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PetMenu {

    public static final int SLOT_INFO = 4;
    public static final int SLOT_POWER = 10;
    public static final int SLOT_HP = 11;
    public static final int SLOT_TECHNIQUE = 12;
    public static final int SLOT_TYPE = 14;
    public static final int SLOT_RENAME = 15;
    public static final int SLOT_FOLLOW = 16;
    public static final int SLOT_TECH1 = 21;
    public static final int SLOT_TECH2 = 22;
    public static final int SLOT_TECH3 = 23;
    public static final int SLOT_COLLECT = 25;
    public static final int SLOT_REMOVE = 26;

    private final FishMotion motion;
    private final RenameDialog renameDialog;
    private final Map<UUID, UUID> lastPet = new HashMap<>();
    private final Map<UUID, UUID> openMenus = new HashMap<>();

    public PetMenu(FishMotion motion) {
        this.motion = motion;
        this.renameDialog = new RenameDialog(motion);
    }

    public void open(Player player, Entity entity) {
        if (!FishMotion.hasData(entity)) {
            return;
        }
        lastPet.put(player.getUniqueId(), entity.getUniqueId());
        openMenus.put(player.getUniqueId(), entity.getUniqueId());

        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("魚の設定", NamedTextColor.DARK_AQUA));
        holder.inventory = inventory;
        render(inventory, entity);
        player.openInventory(inventory);
    }

    public void refreshIfOpen(Player player) {
        UUID entityId = openMenus.get(player.getUniqueId());
        if (entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || !entity.isValid()) {
            openMenus.remove(player.getUniqueId());
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof Holder) {
            render(top, entity);
            player.updateInventory();
        }
    }

    private void render(Inventory inventory, Entity entity) {
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return;
        }
        inventory.setItem(SLOT_INFO, infoItem(fish));
        inventory.setItem(SLOT_POWER, statItem(Material.IRON_SWORD, "強さ", fish.power(),
                NamedTextColor.RED, "与えるダメージの大きさ"));
        inventory.setItem(SLOT_HP, statItem(Material.GOLDEN_APPLE, "体力", fish.maxHp(),
                NamedTextColor.GREEN, "HPの高さ（現在 " + fish.hp() + "/" + fish.maxHp() + "）"));
        inventory.setItem(SLOT_TECHNIQUE, statItem(Material.FEATHER, "テクニック", fish.technique(),
                NamedTextColor.AQUA, "気合の溜まりやすさ"));
        inventory.setItem(SLOT_TYPE, typeItem(fish));
        inventory.setItem(SLOT_RENAME, actionItem(Material.NAME_TAG, "名前を変える",
                NamedTextColor.GREEN, List.of("チャットに新しい名前を入力")));
        boolean follow = follows(entity);
        inventory.setItem(SLOT_FOLLOW, actionItem(Material.LEAD,
                follow ? "追従: ON" : "追従: OFF",
                follow ? NamedTextColor.YELLOW : NamedTextColor.GRAY,
                List.of(follow ? "クリックでその場に留める" : "クリックで追従させる")));
        inventory.setItem(SLOT_COLLECT, actionItem(Material.BUCKET, "回収する",
                NamedTextColor.AQUA, List.of("バケツに戻してインベントリへ")));
        inventory.setItem(SLOT_REMOVE, actionItem(Material.BARRIER, "この魚を消す",
                NamedTextColor.RED, List.of("元に戻せません")));

        List<String> ids = fish.techniques();
        for (int i = 0; i < 3; i++) {
            int slot = SLOT_TECH1 + i;
            if (i < ids.size()) {
                Technique technique = Technique.byId(ids.get(i));
                inventory.setItem(slot, techniqueItem(technique, i));
            } else {
                inventory.setItem(slot, emptyTechniqueItem(i));
            }
        }
    }

    private ItemStack infoItem(Fish fish) {
        Material material = switch (fish.type()) {
            case BLUE -> Material.COD_SPAWN_EGG;
            case RED -> Material.SALMON_SPAWN_EGG;
            case YELLOW -> Material.PUFFERFISH_SPAWN_EGG;
        };
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.customName(FishItems.name(fish));
            List<Component> lore = new ArrayList<>();
            lore.add(line("属性: " + fish.type().label(), fish.type().color()));
            lore.add(line("HP " + fish.hp() + " / " + fish.maxHp(), NamedTextColor.RED));
            lore.add(line("Lv" + fish.level() + " / つぎまで "
                    + Math.max(0, fish.nextExp() - fish.exp()) + " EXP", NamedTextColor.AQUA));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack statItem(Material material, String name, int value, NamedTextColor color, String hint) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.customName(Component.text(name + ": " + value, color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(line(hint, NamedTextColor.GRAY)));
        });
        return item;
    }

    private ItemStack typeItem(Fish fish) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        item.editMeta(meta -> {
            meta.customName(Component.text("属性: " + fish.type().label(), fish.type().color())
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(line("捕獲時に決まる属性（変更できません）", NamedTextColor.DARK_GRAY)));
        });
        return item;
    }

    private ItemStack techniqueItem(Technique technique, int index) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            String name = technique != null ? technique.name() : "不明な技";
            NamedTextColor color = technique != null ? technique.color() : NamedTextColor.GRAY;
            meta.customName(Component.text((index + 1) + ". " + name, color)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (technique != null) {
                lore.add(line("威力 x" + String.format("%.2f", technique.bonus()), color));
                lore.add(line(technique.description(), NamedTextColor.GRAY));
            }
            lore.add(line("クリックで外す", NamedTextColor.RED));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack emptyTechniqueItem(int index) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> {
            meta.customName(Component.text("技スロット " + (index + 1) + "（空き）", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(line("技カードを持って魚を右クリックで装備", NamedTextColor.GRAY)));
        });
        return item;
    }

    private ItemStack actionItem(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.customName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>();
            for (String text : lore) {
                lines.add(line(text, NamedTextColor.GRAY));
            }
            meta.lore(lines);
        });
        return item;
    }

    public void handleClick(Player player, int slot) {
        Entity entity = entityOf(player);
        if (entity == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case SLOT_TYPE -> player.sendActionBar(
                    net.kyori.adventure.text.Component.text("属性は捕獲時に決まり変更できません",
                            NamedTextColor.GRAY));
            case SLOT_RENAME -> {
                player.closeInventory();
                renameDialog.open(player, entity);
            }
            case SLOT_FOLLOW -> {
                boolean follow = motion.toggleFollow(entity);
                player.sendMessage(Component.text(follow ? "追従するようにしました。" : "その場に留まるようにしました。",
                        NamedTextColor.YELLOW));
                refreshIfOpen(player);
            }
            case SLOT_COLLECT -> {
                CaptureListener.collect(player, entity, motion);
                close(player);
                player.closeInventory();
            }
            case SLOT_REMOVE -> {
                motion.untrack(entity);
                entity.remove();
                player.sendMessage(Component.text("魚を消しました。", NamedTextColor.GRAY));
                close(player);
                player.closeInventory();
            }
            case SLOT_TECH1, SLOT_TECH2, SLOT_TECH3 -> {
                int index = slot - SLOT_TECH1 + 1;
                untrain(player, entity, index);
                refreshIfOpen(player);
            }
            default -> {
            }
        }
    }

    public void rename(Player player, String name) {
        Entity entity = petOf(player);
        if (entity == null) {
            player.sendMessage(Component.text("先に魚を右クリックして選んでください。", NamedTextColor.RED));
            return;
        }
        renameDialog.apply(player, entity, name);
    }

    public void close(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public void closeMenuOnly(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public boolean ownsMenu(Inventory inventory) {
        return inventory.getHolder() instanceof Holder;
    }

    private void untrain(Player player, Entity entity, int slot) {
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return;
        }
        List<String> ids = new ArrayList<>(fish.techniques());
        if (slot < 1 || slot > ids.size()) {
            return;
        }
        String removed = ids.remove(slot - 1);
        entity.getPersistentDataContainer().set(FishKeys.TECHNIQUES, PersistentDataType.STRING,
                String.join(",", ids));
        entity.customName(FishItems.name(fish.withTechniques(ids)));
        Technique technique = Technique.byId(removed);
        player.sendMessage(Component.text((technique != null ? technique.name() : removed) + " を外しました。",
                NamedTextColor.GRAY));
    }

    public void listTechniques(Player player) {
        Entity entity = petOf(player);
        if (entity == null) {
            player.sendMessage(Component.text("先に魚を右クリックして選んでください。", NamedTextColor.RED));
            return;
        }
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return;
        }
        player.sendMessage(Component.text("── 装備中の技 (" + fish.techniques().size() + "/3) ──",
                NamedTextColor.AQUA));
        if (fish.techniques().isEmpty()) {
            player.sendMessage(Component.text("なし（技カードを持って魚を右クリックで装備）", NamedTextColor.GRAY));
            return;
        }
        for (String id : fish.techniques()) {
            Technique technique = Technique.byId(id);
            if (technique == null) {
                continue;
            }
            player.sendMessage(Component.text()
                    .append(Component.text("・", NamedTextColor.GRAY))
                    .append(Component.text(technique.name(), technique.color()))
                    .append(Component.text(" x" + String.format("%.2f", technique.bonus()),
                            NamedTextColor.DARK_GRAY))
                    .build());
        }
    }

    public boolean equipTechnique(Player player, Entity entity, Technique technique) {
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return false;
        }
        List<String> ids = new ArrayList<>(fish.techniques());
        if (ids.contains(technique.id())) {
            player.sendMessage(Component.text("その技はすでに装備しています。", NamedTextColor.YELLOW));
            return false;
        }
        if (ids.size() >= 3) {
            player.sendMessage(Component.text("技は3つまでです。付け替えるなら先に外してください。", NamedTextColor.RED));
            return false;
        }
        ids.add(technique.id());
        entity.getPersistentDataContainer().set(FishKeys.TECHNIQUES, PersistentDataType.STRING,
                String.join(",", ids));
        entity.customName(FishItems.name(fish.withTechniques(ids)));
        entity.setCustomNameVisible(true);
        player.sendMessage(Component.text("「" + technique.name() + "」を覚えさせました（"
                + ids.size() + "/3）。", technique.color()));
        refreshIfOpen(player);
        return true;
    }

    public void untrain(Player player, int slot) {
        Entity entity = petOf(player);
        if (entity == null) {
            return;
        }
        untrain(player, entity, slot);
    }

    public void toggleFollow(Player player) {
        Entity entity = petOf(player);
        if (entity == null) {
            player.sendMessage(Component.text("先に魚を右クリックして選んでください。", NamedTextColor.RED));
            return;
        }
        boolean follow = motion.toggleFollow(entity);
        player.sendMessage(Component.text(follow ? "追従するようにしました。" : "その場に留まるようにしました。",
                NamedTextColor.YELLOW));
    }

    public void collect(Player player) {
        Entity entity = petOf(player);
        if (entity == null) {
            player.sendMessage(Component.text("先に魚を右クリックして選んでください。", NamedTextColor.RED));
            return;
        }
        CaptureListener.collect(player, entity, motion);
        close(player);
    }

    public void remove(Player player) {
        Entity entity = petOf(player);
        if (entity == null) {
            player.sendMessage(Component.text("先に魚を右クリックして選んでください。", NamedTextColor.RED));
            return;
        }
        motion.untrack(entity);
        entity.remove();
        close(player);
        player.sendMessage(Component.text("魚を消しました。", NamedTextColor.GRAY));
    }

    private Entity entityOf(Player player) {
        UUID entityId = openMenus.get(player.getUniqueId());
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                return entity;
            }
        }
        return petOf(player);
    }

    private Entity petOf(Player player) {
        UUID id = lastPet.get(player.getUniqueId());
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity == null || !entity.isValid()) {
            lastPet.remove(player.getUniqueId());
            return null;
        }
        return entity;
    }

    private static boolean follows(Entity entity) {
        Byte value = entity.getPersistentDataContainer().get(FishKeys.FOLLOW, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Holder implements org.bukkit.inventory.InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
