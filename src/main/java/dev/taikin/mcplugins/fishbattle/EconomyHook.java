package dev.taikin.mcplugins.fishbattle;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyHook {

    private final Economy economy;

    private EconomyHook(Economy economy) {
        this.economy = economy;
    }

    public static EconomyHook load() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return new EconomyHook(null);
        }
        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null || provider.getProvider() == null) {
            return new EconomyHook(null);
        }
        return new EconomyHook(provider.getProvider());
    }

    public boolean available() {
        return economy != null;
    }

    public double balance(OfflinePlayer player) {
        return economy == null ? 0 : economy.getBalance(player);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) {
            return false;
        }
        if (economy.getBalance(player) < amount) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
