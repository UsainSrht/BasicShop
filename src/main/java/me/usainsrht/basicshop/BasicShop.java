package me.usainsrht.basicshop;

import me.usainsrht.basicshop.analytics.AnalyticsManager;
import me.usainsrht.basicshop.analytics.TransactionLogger;
import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.ShopAPIImpl;
import me.usainsrht.basicshop.api.economy.VaultEconomyProvider;
import me.usainsrht.basicshop.command.ShopCommand;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.item.ShopToolFactory;
import me.usainsrht.basicshop.listener.GuiListener;
import me.usainsrht.basicshop.listener.ToolListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.morepaperlib.MorePaperLib;
import java.util.logging.Level;

/**
 * BasicShop — main plugin class.
 *
 * <p>Boot order:
 * <ol>
 *   <li>Initialize {@link MorePaperLib} (Folia-compatible scheduling)</li>
 *   <li>Load configuration via {@link ConfigManager}</li>
 *   <li>Hook Vault economy; abort if unavailable</li>
 *   <li>Initialize analytics ({@link AnalyticsManager} + {@link TransactionLogger})</li>
 *   <li>Build {@link ShopAPIImpl}</li>
 *   <li>Register listeners</li>
 *   <li>Register Brigadier commands (via lifecycle event)</li>
 *   <li>Start bStats metrics</li>
 * </ol>
 */
public final class BasicShop extends JavaPlugin {

    /** bStats plugin ID — register at https://bstats.org to obtain a real ID. */
    private static final int BSTATS_PLUGIN_ID = 31510;

    private MorePaperLib morePaperLib;
    private ConfigManager configManager;
    private VaultEconomyProvider economyProvider;
    private AnalyticsManager analyticsManager;
    private TransactionLogger transactionLogger;
    private ShopAPIImpl shopAPI;
    private ShopToolFactory toolFactory;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onEnable() {
        // 1. MorePaperLib — must be first (used by other subsystems)
        morePaperLib = new MorePaperLib(this);

        // 2. Configuration
        configManager = new ConfigManager(this);
        try {
            configManager.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configurations! The plugin may not function correctly.", e);
        }

        // 3. Economy (Vault)
        economyProvider = new VaultEconomyProvider();
        if (!economyProvider.hook(getLogger())) {
            getLogger().severe("BasicShop could not hook into an economy plugin. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Analytics
        analyticsManager  = new AnalyticsManager();
        transactionLogger = new TransactionLogger(this, analyticsManager, morePaperLib);
        transactionLogger.start();

        // 5. API
        shopAPI = new ShopAPIImpl(configManager, economyProvider, analyticsManager, transactionLogger);
        toolFactory = new ShopToolFactory(this, configManager.getToolsConfig());

        // 6. Listeners
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(
                new ToolListener(configManager, shopAPI, toolFactory, morePaperLib),
                this
        );

        // 7. Commands (lifecycle — must be called before onEnable returns for COMMANDS event)
        new ShopCommand(this, configManager, shopAPI, toolFactory, morePaperLib).register();

        // 8. bStats
        initMetrics();

        int categoriesCount = configManager.getCategories() != null ? configManager.getCategories().size() : 0;
        getLogger().info("BasicShop enabled successfully. Loaded " + categoriesCount + " categories.");
    }

    @Override
    public void onDisable() {
        // Flush analytics and stop scheduled tasks
        if (transactionLogger != null) {
            transactionLogger.stop();
        }
        if (morePaperLib != null) {
            morePaperLib.scheduling().cancelGlobalTasks();
        }
        getLogger().info("BasicShop disabled.");
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    private void initMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        // Chart: buying enabled?
        metrics.addCustomChart(new SimplePie("buying_enabled",
                () -> configManager.getMainConfig().isBuyingEnabled() ? "Enabled" : "Disabled"));

        // Chart: selling enabled?
        metrics.addCustomChart(new SimplePie("selling_enabled",
                () -> configManager.getMainConfig().isSellingEnabled() ? "Enabled" : "Disabled"));

        // Chart: category count
        metrics.addCustomChart(new SimplePie("category_count",
                () -> String.valueOf(configManager.getCategories().size())));
    }

    // -------------------------------------------------------------------------
    // Accessors (for integration / testing)
    // -------------------------------------------------------------------------

    public ShopAPI getShopAPI() {
        return shopAPI;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MorePaperLib getMorePaperLib() {
        return morePaperLib;
    }
}
