package me.usainsrht.basicshop.analytics;

import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import space.arim.morepaperlib.MorePaperLib;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TopSellersEngineTest {

    @TempDir
    Path tempDir;

    private Plugin plugin;
    private ConfigManager configManager;
    private MorePaperLib morePaperLib;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        configManager = mock(ConfigManager.class);
        morePaperLib = mock(MorePaperLib.class);

        YamlConfiguration configYml = new YamlConfiguration();
        configYml.set("analytics.top-sellers.amount", 5);
        configYml.set("analytics.top-sellers.days", 7);
        configYml.set("analytics.top-sellers.update-interval-minutes", 60);
        configYml.set("analytics.top-sellers.default-view", "chat");
        MainConfig mainConfig = new MainConfig(configYml);

        when(configManager.getMainConfig()).thenReturn(mainConfig);
    }

    @Test
    void testRecalculateCalculatesOnlySellsAndSortsByProfit() throws Exception {
        File analyticsDir = new File(tempDir.toFile(), "analytics");
        analyticsDir.mkdirs();

        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        File logFile = new File(analyticsDir, "transactions-" + dateStr + ".csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile))) {
            writer.println("timestamp,player_id,player_name,category_id,item_id,type,amount,total_price");
            // BUY transactions (should be ignored by top sellers)
            writer.println(Instant.now() + ",00000000-0000-0000-0000-000000000001,Steve,miner,miner:diamond,BUY,10,1000.00");
            // SELL transactions:
            // Diamond: 5 sold for 500.00
            writer.println(Instant.now() + ",00000000-0000-0000-0000-000000000001,Steve,miner,miner:diamond,SELL,5,500.00");
            // Wheat: 64 sold for 640.00 (higher profit!)
            writer.println(Instant.now() + ",00000000-0000-0000-0000-000000000002,Alex,farmer,farmer:wheat,SELL,64,640.00");
            // Iron: 20 sold for 100.00
            writer.println(Instant.now() + ",00000000-0000-0000-0000-000000000001,Steve,miner,miner:iron_ingot,SELL,20,100.00");
        }

        TopSellersEngine engine = new TopSellersEngine(plugin, configManager, null, morePaperLib);
        List<TopSellerItem> topSellers = engine.recalculateSync();

        assertEquals(3, topSellers.size());

        // #1 should be wheat ($640)
        TopSellerItem rank1 = topSellers.get(0);
        assertEquals(1, rank1.rank());
        assertEquals("farmer:wheat", rank1.itemId());
        assertEquals(64, rank1.unitsSold());
        assertEquals(640.00, rank1.totalProfit(), 0.001);
        assertEquals(Material.WHEAT, rank1.material());

        // #2 should be diamond ($500)
        TopSellerItem rank2 = topSellers.get(1);
        assertEquals(2, rank2.rank());
        assertEquals("miner:diamond", rank2.itemId());
        assertEquals(5, rank2.unitsSold());
        assertEquals(500.00, rank2.totalProfit(), 0.001);
        assertEquals(Material.DIAMOND, rank2.material());

        // #3 should be iron ($100)
        TopSellerItem rank3 = topSellers.get(2);
        assertEquals(3, rank3.rank());
        assertEquals("miner:iron_ingot", rank3.itemId());
        assertEquals(20, rank3.unitsSold());
        assertEquals(100.00, rank3.totalProfit(), 0.001);
    }

    @Test
    void testEmptyLogsReturnsEmptyList() {
        TopSellersEngine engine = new TopSellersEngine(plugin, configManager, null, morePaperLib);
        List<TopSellerItem> topSellers = engine.recalculateSync();
        assertNotNull(topSellers);
        assertTrue(topSellers.isEmpty());
        assertTrue(engine.getByRank(1).isEmpty());
    }
}
