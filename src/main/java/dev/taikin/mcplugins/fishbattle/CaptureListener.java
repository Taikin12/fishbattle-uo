package dev.taikin.mcplugins.fishbattle;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cod;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PufferFish;
import org.bukkit.entity.Salmon;
import org.bukkit.entity.Tadpole;
import org.bukkit.entity.TropicalFish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class CaptureListener implements Listener {

    private final FishMotion motion;
    private final PetMenu menu;
    private BattleManager battles;

    public CaptureListener(FishMotion motion, PetMenu menu) {
        this.motion = motion;
        this.menu = menu;
    }

    public void setBattles(BattleManager battles) {
        this.battles = battles;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPunch(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!FishMotion.hasData(entity)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        Entity entity = event.getAttacked();
        if (!FishMotion.hasData(entity)) {
            return;
        }
        event.setCancelled(true);
        collectFromPunch(event.getPlayer(), entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(org.bukkit.event.entity.EntityCombustEvent event) {
        if (FishMotion.hasData(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (FishMotion.hasData(event.getEntity())
                || (event.getTarget() != null && FishMotion.hasData(event.getTarget()))) {
            event.setCancelled(true);
        }
    }

    private void collectFromPunch(Player player, Entity entity) {
        if (battles != null && battles.inBattle(player.getUniqueId())) {
            return;
        }
        String ownerId = entity.getPersistentDataContainer().get(FishKeys.OWNER, PersistentDataType.STRING);
        if (ownerId != null && !ownerId.equals(player.getUniqueId().toString())) {
            player.sendMessage(Component.text("飼い主だけが回収できます。飼い主は "
                    + ownerName(ownerId) + " です。", NamedTextColor.YELLOW));
            return;
        }
        collect(player, entity, motion);
    }

    static String ownerName(String ownerId) {
        try {
            var offline = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(ownerId));
            String name = offline.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return "不明";
    }

    static void collect(Player player, Entity entity, FishMotion motion) {
        Fish fish = FishData.read(entity);
        if (fish == null) {
            return;
        }
        ItemStack bucket = FishItems.bucketFor(fish);
        var leftovers = player.getInventory().addItem(bucket);
        if (!leftovers.isEmpty()) {
            player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
            return;
        }
        motion.untrack(entity);
        entity.remove();
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        player.sendMessage(Component.text()
                .append(Component.text("回収した: ", NamedTextColor.GREEN))
                .append(FishItems.name(fish))
                .build());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isBucketableFish(entity)) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = player.getInventory().getItem(hand);
        Technique card = TechniqueItem.read(item);
        if (card != null && FishMotion.hasData(entity)) {
            String ownerId = entity.getPersistentDataContainer().get(FishKeys.OWNER, PersistentDataType.STRING);
            if (ownerId == null || ownerId.equals(player.getUniqueId().toString())) {
                event.setCancelled(true);
                if (menu.equipTechnique(player, entity, card)) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItem(hand, new ItemStack(Material.AIR));
                    }
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
                }
                return;
            }
        }
        if (FishMotion.hasData(entity)) {
            String ownerId = entity.getPersistentDataContainer().get(FishKeys.OWNER, PersistentDataType.STRING);
            if (ownerId == null || ownerId.equals(player.getUniqueId().toString())) {
                event.setCancelled(true);
                menu.open(player, entity);
                return;
            }
        }
        if (!FishItems.isCaptureBucket(item)) {
            return;
        }
        event.setCancelled(true);
        catchAndGive(player, hand, entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        Entity entity = event.getEntity();
        if (!isBucketableFish(entity)) {
            return;
        }
        Player player = event.getPlayer();
        if (!FishItems.isCaptureBucket(event.getOriginalBucket())) {
            return;
        }
        event.setCancelled(true);
        catchAndGive(player, event.getHand(), entity);
    }

    private void catchAndGive(Player player, EquipmentSlot hand, Entity entity) {
        Fish caught = catchFish(entity);
        ItemStack bucket = FishItems.bucketFor(caught);
        EquipmentSlot slot = hand == null ? EquipmentSlot.HAND : hand;
        ItemStack held = player.getInventory().getItem(slot);
        if (held != null && held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            var leftovers = player.getInventory().addItem(bucket);
            if (!leftovers.isEmpty()) {
                player.sendMessage(Component.text("インベントリに空きがありません。", NamedTextColor.RED));
                return;
            }
        } else {
            player.getInventory().setItem(slot, bucket);
        }
        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 1.0f, 1.0f);
        player.sendMessage(Component.text()
                .append(Component.text("捕まえた！ ", NamedTextColor.GREEN))
                .append(FishItems.name(caught))
                .build());
        motion.untrack(entity);
        entity.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (FishItems.isCaptureBucket(event.getItemStack())
                || FishItems.isFish(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (FishItems.isCaptureBucket(event.getItemStack())
                || FishItems.read(event.getItemStack()) != null) {
            event.setCancelled(true);
            if (FishItems.isCaptureBucket(event.getItemStack())) {
                event.getPlayer().sendMessage(Component.text("捕獲バケツは水をまけません。魚に使いましょう。",
                        NamedTextColor.YELLOW));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() == null) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getState() instanceof Container) {
            return;
        }
        Fish fish = FishItems.read(event.getItem());
        if (fish == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        release(player, hand, fish);
    }

    private void release(Player player, EquipmentSlot hand, Fish fish) {
        if (motion.petOf(player) != null) {
            player.sendMessage(Component.text("同時に出せる魚は1匹までです。先に回収してください。",
                    NamedTextColor.YELLOW));
            return;
        }
        var eye = player.getEyeLocation();
        Vector direction = eye.getDirection().setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(1.6);
        var location = eye.clone().add(direction).add(0, 0.1, 0);

        Entity entity = spawnFor(fish, location);
        if (entity == null) {
            player.sendMessage(Component.text("魚を出せませんでした。", NamedTextColor.RED));
            return;
        }
        applyData(entity, fish);
        entity.getPersistentDataContainer().set(FishKeys.OWNER, PersistentDataType.STRING,
                player.getUniqueId().toString());
        entity.setPersistent(true);
        motion.track(entity);

        ItemStack inHand = player.getInventory().getItem(hand);
        if (inHand != null) {
            if (inHand.getAmount() > 1) {
                inHand.setAmount(inHand.getAmount() - 1);
            } else {
                player.getInventory().setItem(hand, new ItemStack(Material.AIR));
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
        player.sendMessage(Component.text()
                .append(Component.text("出した: ", NamedTextColor.GRAY))
                .append(FishItems.name(fish))
                .build());
    }

    public static Entity spawnFor(Fish fish, org.bukkit.Location location) {
        var world = location.getWorld();
        if (world == null) {
            return null;
        }
        String key = fish.speciesKey();
        if (key.equals("cod")) {
            return world.spawn(location, Cod.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        if (key.equals("salmon")) {
            return world.spawn(location, Salmon.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        if (key.equals("pufferfish")) {
            return world.spawn(location, PufferFish.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        if (key.equals("tadpole")) {
            return world.spawn(location, Tadpole.class, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
        if (key.startsWith("axolotl:")) {
            Axolotl.Variant variant = Axolotl.Variant.valueOf(key.substring("axolotl:".length()));
            return world.spawn(location, Axolotl.class, CreatureSpawnEvent.SpawnReason.CUSTOM, axolotl -> {
                axolotl.setVariant(variant);
                axolotl.setPlayingDead(false);
            });
        }
        TropicalFish.Pattern pattern = TropicalFish.Pattern.valueOf(key.substring("tropical:".length()));
        return world.spawn(location, TropicalFish.class, CreatureSpawnEvent.SpawnReason.CUSTOM, tropical -> {
            tropical.setPattern(pattern);
            tropical.setBodyColor(fish.type().dye());
            tropical.setPatternColor(org.bukkit.DyeColor.WHITE);
        });
    }

    public static void applyData(Entity entity, Fish fish) {
        var pdc = entity.getPersistentDataContainer();
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
        pdc.set(FishKeys.FOLLOW, PersistentDataType.BYTE, (byte) 1);
        if (!fish.techniques().isEmpty()) {
            pdc.set(FishKeys.TECHNIQUES, PersistentDataType.STRING, String.join(",", fish.techniques()));
        }
        entity.customName(FishItems.name(fish));
        entity.setCustomNameVisible(true);
        var loc = entity.getLocation();
        pdc.set(FishKeys.ANCHOR_X, PersistentDataType.DOUBLE, loc.getX());
        pdc.set(FishKeys.ANCHOR_Y, PersistentDataType.DOUBLE, loc.getY());
        pdc.set(FishKeys.ANCHOR_Z, PersistentDataType.DOUBLE, loc.getZ());
    }

    private static boolean isBucketableFish(Entity entity) {
        return entity instanceof TropicalFish
                || entity instanceof Axolotl
                || entity instanceof Cod
                || entity instanceof Salmon
                || entity instanceof PufferFish
                || entity instanceof Tadpole;
    }

    private Fish catchFish(Entity entity) {
        Fish existing = FishData.read(entity);
        if (existing != null) {
            return existing;
        }
        String speciesKey;
        if (entity instanceof TropicalFish tropical) {
            speciesKey = FishSpecies.keyOf(tropical.getPattern());
        } else if (entity instanceof Axolotl axolotl) {
            speciesKey = FishSpecies.keyOf(axolotl.getVariant());
        } else if (entity instanceof Cod) {
            speciesKey = "cod";
        } else if (entity instanceof Salmon) {
            speciesKey = "salmon";
        } else if (entity instanceof Tadpole) {
            speciesKey = "tadpole";
        } else {
            speciesKey = "pufferfish";
        }
        FishType type = FishType.random();
        int level = ThreadLocalRandom.current().nextInt(2, 6);
        return new Fish(UUID.randomUUID().toString(), type, speciesKey, level, 0,
                Fish.maxHp(FishSpecies.byKey(speciesKey), level), "", List.of());
    }
}
