package dev.taikin.mcplugins.fishbattle;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FishInventory {

    private FishInventory() {
    }

    public static int findSlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (FishItems.isFish(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    public static int findSlot(Player player, String fishId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            Fish fish = FishItems.read(contents[i]);
            if (fish != null && fish.id().equals(fishId)) {
                return i;
            }
        }
        return -1;
    }

    public static Fish find(Player player) {
        int slot = findSlot(player);
        return slot < 0 ? null : FishItems.read(player.getInventory().getItem(slot));
    }

    public static void update(Player player, Fish fish) {
        int slot = findSlot(player, fish.id());
        if (slot < 0) {
            slot = findSlot(player);
        }
        if (slot < 0) {
            return;
        }
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing == null) {
            return;
        }
        ItemStack copy = existing.clone();
        FishItems.write(copy, fish);
        player.getInventory().setItem(slot, copy);
    }
}
