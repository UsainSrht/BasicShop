package me.usainsrht.basicshop.api.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

/**
 * Vault-backed economy provider.
 * Call {@link #hook(Logger)} during plugin enable to register the Vault hook.
 */
public final class VaultEconomyProvider implements EconomyProvider {

    private Economy economy;

    /**
     * Attempts to hook into Vault's Economy service.
     *
     * @return {@code true} if Vault was found and the hook succeeded
     */
    public boolean hook(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.severe("Vault not found! BasicShop requires Vault to function.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            logger.severe("No economy plugin found! Please install an economy plugin (e.g. EssentialsX).");
            return false;
        }

        economy = rsp.getProvider();
        logger.info("Successfully hooked into Vault economy: " + economy.getName());
        return true;
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
