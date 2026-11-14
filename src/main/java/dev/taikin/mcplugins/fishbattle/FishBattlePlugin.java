package dev.taikin.mcplugins.fishbattle;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class FishBattlePlugin extends JavaPlugin {

    private BattleManager battles;
    private FishMotion motion;
    private NamespacedKey captureRecipeKey;

    @Override
    public void onEnable() {
        FishKeys.init(this);
        this.motion = new FishMotion(this);
        this.battles = new BattleManager(this, motion);
        motion.start();
        motion.trackLoaded();
        PetMenu menu = new PetMenu(motion);

        CaptureListener capture = new CaptureListener(this, motion, menu);
        capture.setBattles(battles);
        getServer().getPluginManager().registerEvents(capture, this);
        getServer().getPluginManager().registerEvents(new BattleListener(battles, motion), this);
        getServer().getPluginManager().registerEvents(new PetMenuListener(this, menu), this);

        FishCommand command = new FishCommand(battles, motion, menu);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.node(), "Bucket fish and battle", List.of()));

        registerCaptureRecipe();
        getLogger().info("FishBattle enabled.");
    }

    @Override
    public void onDisable() {
        if (motion != null) {
            motion.stop();
        }
        if (captureRecipeKey != null) {
            Bukkit.removeRecipe(captureRecipeKey);
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (battles != null) {
                battles.endQuietly(player);
            }
        }
        getLogger().info("FishBattle disabled.");
    }

    private void registerCaptureRecipe() {
        this.captureRecipeKey = new NamespacedKey(this, "capture_bucket");
        Bukkit.removeRecipe(captureRecipeKey);
        ShapelessRecipe recipe = new ShapelessRecipe(captureRecipeKey, FishItems.captureBucket());
        recipe.addIngredient(Material.BUCKET);
        recipe.addIngredient(Material.KELP);
        Bukkit.addRecipe(recipe);
        getLogger().info("Capture bucket recipe: bucket + kelp.");
    }
}
