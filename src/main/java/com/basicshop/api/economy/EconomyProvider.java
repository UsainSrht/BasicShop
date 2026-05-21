package com.basicshop.api.economy;

import org.bukkit.entity.Player;

/**
 * Abstraction over the economy system.
 * The default implementation bridges to Vault.
 */
public interface EconomyProvider {

    /**
     * Returns {@code true} if the economy provider is loaded and functional.
     */
    boolean isAvailable();

    /**
     * Returns the current balance of the player.
     */
    double getBalance(Player player);

    /**
     * Withdraws {@code amount} from the player's account.
     * Callers must verify sufficient balance before calling.
     *
     * @return {@code true} if the withdrawal succeeded
     */
    boolean withdraw(Player player, double amount);

    /**
     * Deposits {@code amount} into the player's account.
     *
     * @return {@code true} if the deposit succeeded
     */
    boolean deposit(Player player, double amount);
}
