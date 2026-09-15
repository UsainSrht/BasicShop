package me.usainsrht.basicshop.analytics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes shop logs, compresses the payload, and uploads it asynchronously to the Cloudflare Worker web service.
 */
public final class AnalyticsWebUploader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final TopSellersEngine topSellersEngine;

    public record UploadResult(boolean success, String url, String id, int expirationHours, String error) {
        public int expirationDays() {
            return (int) Math.ceil(expirationHours / 24.0);
        }
    }

    public AnalyticsWebUploader(Plugin plugin, ConfigManager configManager, TopSellersEngine topSellersEngine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.topSellersEngine = topSellersEngine;
    }

    /**
     * Packages analytics for the given number of days and uploads them to the Cloudflare Worker.
     */
    public CompletableFuture<UploadResult> uploadAsync(int days) {
        MainConfig.WebAnalyticsSettings web = configManager.getMainConfig().getAnalyticsSettings().web();
        String url = web.uploadUrl();
        String token = web.secretToken();

        if (url == null || url.isBlank()) {
            return CompletableFuture.completedFuture(new UploadResult(false, null, null, 0, "upload-url not configured in config.yml"));
        }

        // Normalize URL
        String targetUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String uploadEndpoint = targetUrl + "/api/upload";

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Build Payload
                JsonObject payload = buildPayload(days);
                String json = GSON.toJson(payload);

                // 2. Compress with GZIP
                byte[] gzippedBytes = gzipCompress(json);

                // 3. Prepare HTTP POST
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(uploadEndpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .header("Content-Encoding", "gzip")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(gzippedBytes));

                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token);
                }

                // 4. Send Request
                HttpResponse<String> response = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonObject respObj = GSON.fromJson(response.body(), JsonObject.class);
                    String id = respObj.has("id") ? respObj.get("id").getAsString() : "";
                    String viewUrl = respObj.has("url") ? respObj.get("url").getAsString() : (targetUrl + "/view/" + id);
                    int exp = respObj.has("expirationHours") ? respObj.get("expirationHours").getAsInt() :
                              (respObj.has("expirationDays") ? respObj.get("expirationDays").getAsInt() * 24 : web.expirationHours());
                    return new UploadResult(true, viewUrl, id, exp, null);
                } else {
                    String err = "HTTP " + response.statusCode() + ": " + response.body();
                    return new UploadResult(false, null, null, 0, err);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during analytics upload to Cloudflare", e);
                return new UploadResult(false, null, null, 0, e.getMessage());
            }
        });
    }

    private JsonObject buildPayload(int days) {
        List<TransactionRecord> records = topSellersEngine.readRecordsForDays(days);

        JsonObject root = new JsonObject();
        root.addProperty("version", "1");
        root.addProperty("serverName", plugin.getServer().getName());
        root.addProperty("generatedAt", Instant.now().toString());
        root.addProperty("daysAnalyzed", days);
        root.addProperty("startDate", LocalDate.now().minusDays(days - 1).toString());
        root.addProperty("endDate", LocalDate.now().toString());
        root.addProperty("expirationHours", configManager.getMainConfig().getAnalyticsSettings().web().expirationHours());
        root.addProperty("expirationDays", configManager.getMainConfig().getAnalyticsSettings().web().expirationDays());

        // KPIs
        double totalBought = 0;
        double totalSold = 0;
        long unitsBought = 0;
        long unitsSold = 0;
        int totalTransactions = records.size();

        Map<String, ItemStats> itemMap = new HashMap<>();
        Map<String, PlayerStats> playerMap = new HashMap<>();
        Map<String, DayStats> dayMap = new HashMap<>();
        int[] hourlyDistribution = new int[24];

        for (TransactionRecord r : records) {
            String dateStr = r.getTimestamp().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE);

            if (r.getType() == TransactionType.BUY) {
                totalBought += r.getTotalPrice();
                unitsBought += r.getAmount();
            } else {
                totalSold += r.getTotalPrice();
                unitsSold += r.getAmount();
            }

            // Item stats
            ItemStats is = itemMap.computeIfAbsent(r.getItemId(), k -> new ItemStats(r.getItemId(), r.getCategoryId()));
            ItemPlayerStats ips = is.traders.computeIfAbsent(r.getPlayerId().toString(), k -> new ItemPlayerStats(r.getPlayerId().toString(), r.getPlayerName()));
            DayStats itemDay = is.timeline.computeIfAbsent(dateStr, DayStats::new);

            if (r.getType() == TransactionType.BUY) {
                is.boughtUnits += r.getAmount();
                is.boughtMoney += r.getTotalPrice();
                ips.boughtUnits += r.getAmount();
                ips.spent += r.getTotalPrice();
                itemDay.buyUnits += r.getAmount();
                itemDay.buyMoney += r.getTotalPrice();
            } else {
                is.soldUnits += r.getAmount();
                is.soldMoney += r.getTotalPrice();
                ips.soldUnits += r.getAmount();
                ips.earned += r.getTotalPrice();
                itemDay.sellUnits += r.getAmount();
                itemDay.sellMoney += r.getTotalPrice();
            }
            is.txCount++;
            ips.txCount++;
            itemDay.transactions++;

            // Player stats
            PlayerStats ps = playerMap.computeIfAbsent(r.getPlayerId().toString(), k -> new PlayerStats(r.getPlayerId().toString(), r.getPlayerName()));
            PlayerItemStats pis = ps.items.computeIfAbsent(r.getItemId(), k -> new PlayerItemStats(r.getItemId(), r.getCategoryId()));
            CategoryStats cs = ps.categories.computeIfAbsent(r.getCategoryId(), CategoryStats::new);
            DayStats playerDay = ps.timeline.computeIfAbsent(dateStr, DayStats::new);

            if (r.getType() == TransactionType.BUY) {
                ps.spent += r.getTotalPrice();
                ps.boughtUnits += r.getAmount();
                pis.boughtUnits += r.getAmount();
                pis.spent += r.getTotalPrice();
                cs.spent += r.getTotalPrice();
                cs.units += r.getAmount();
                playerDay.buyMoney += r.getTotalPrice();
                playerDay.buyUnits += r.getAmount();
            } else {
                ps.earned += r.getTotalPrice();
                ps.soldUnits += r.getAmount();
                pis.soldUnits += r.getAmount();
                pis.earned += r.getTotalPrice();
                cs.earned += r.getTotalPrice();
                cs.units += r.getAmount();
                playerDay.sellMoney += r.getTotalPrice();
                playerDay.sellUnits += r.getAmount();
            }
            ps.txCount++;
            pis.txCount++;
            cs.txCount++;
            playerDay.transactions++;

            // Hourly distribution (in server local time)
            int hour = r.getTimestamp().atZone(ZoneId.systemDefault()).getHour();
            if (hour >= 0 && hour < 24) {
                hourlyDistribution[hour]++;
            }

            // Overall Day timeline
            DayStats ds = dayMap.computeIfAbsent(dateStr, DayStats::new);
            if (r.getType() == TransactionType.BUY) {
                ds.buyMoney += r.getTotalPrice();
                ds.buyUnits += r.getAmount();
            } else {
                ds.sellMoney += r.getTotalPrice();
                ds.sellUnits += r.getAmount();
            }
            ds.transactions++;
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("totalTransactions", totalTransactions);
        summary.addProperty("totalBoughtMoney", totalBought);
        summary.addProperty("totalSoldMoney", totalSold);
        summary.addProperty("netCashDelta", totalBought - totalSold);
        summary.addProperty("totalUnitsBought", unitsBought);
        summary.addProperty("totalUnitsSold", unitsSold);
        summary.addProperty("totalUnitsTraded", unitsBought + unitsSold);
        summary.addProperty("uniqueTraders", playerMap.size());
        root.add("summary", summary);

        // Hourly Array
        JsonArray hourlyArr = new JsonArray();
        for (int h : hourlyDistribution) {
            hourlyArr.add(h);
        }
        root.add("hourlyDistribution", hourlyArr);

        // Timeline Array
        JsonArray timelineArr = new JsonArray();
        List<String> sortedDates = new ArrayList<>(dayMap.keySet());
        Collections.sort(sortedDates);
        for (String d : sortedDates) {
            DayStats ds = dayMap.get(d);
            JsonObject dObj = new JsonObject();
            dObj.addProperty("date", ds.date);
            dObj.addProperty("buyMoney", ds.buyMoney);
            dObj.addProperty("sellMoney", ds.sellMoney);
            dObj.addProperty("buyUnits", ds.buyUnits);
            dObj.addProperty("sellUnits", ds.sellUnits);
            dObj.addProperty("transactions", ds.transactions);
            timelineArr.add(dObj);
        }
        root.add("timeline", timelineArr);

        // Top Items Array (sorted by total volume / transactions)
        JsonArray itemsArr = new JsonArray();
        List<ItemStats> sortedItems = new ArrayList<>(itemMap.values());
        sortedItems.sort((a, b) -> Double.compare(b.soldMoney + b.boughtMoney, a.soldMoney + a.boughtMoney));
        for (ItemStats is : sortedItems) {
            JsonObject io = new JsonObject();
            io.addProperty("itemId", is.itemId);
            io.addProperty("categoryId", is.categoryId);
            io.addProperty("soldUnits", is.soldUnits);
            io.addProperty("soldMoney", is.soldMoney);
            io.addProperty("boughtUnits", is.boughtUnits);
            io.addProperty("boughtMoney", is.boughtMoney);
            io.addProperty("transactions", is.txCount);

            // Item Top Traders
            JsonArray itemTradersArr = new JsonArray();
            List<ItemPlayerStats> sortedItemTraders = new ArrayList<>(is.traders.values());
            sortedItemTraders.sort((a, b) -> Double.compare(b.spent + b.earned, a.spent + a.earned));
            for (ItemPlayerStats ips : sortedItemTraders) {
                JsonObject ipsObj = new JsonObject();
                ipsObj.addProperty("uuid", ips.uuid);
                ipsObj.addProperty("name", ips.name);
                ipsObj.addProperty("boughtUnits", ips.boughtUnits);
                ipsObj.addProperty("soldUnits", ips.soldUnits);
                ipsObj.addProperty("spent", ips.spent);
                ipsObj.addProperty("earned", ips.earned);
                ipsObj.addProperty("transactions", ips.txCount);
                itemTradersArr.add(ipsObj);
            }
            io.add("topTraders", itemTradersArr);

            // Item Timeline
            JsonArray itemTimelineArr = new JsonArray();
            List<String> sortedItemDates = new ArrayList<>(is.timeline.keySet());
            Collections.sort(sortedItemDates);
            for (String d : sortedItemDates) {
                DayStats ids = is.timeline.get(d);
                JsonObject idObj = new JsonObject();
                idObj.addProperty("date", ids.date);
                idObj.addProperty("buyMoney", ids.buyMoney);
                idObj.addProperty("sellMoney", ids.sellMoney);
                idObj.addProperty("buyUnits", ids.buyUnits);
                idObj.addProperty("sellUnits", ids.sellUnits);
                idObj.addProperty("transactions", ids.transactions);
                itemTimelineArr.add(idObj);
            }
            io.add("timeline", itemTimelineArr);

            itemsArr.add(io);
        }
        root.add("items", itemsArr);
        root.add("topItems", itemsArr); // alias for worker schema validation

        // Top Players Array
        JsonArray playersArr = new JsonArray();
        List<PlayerStats> sortedPlayers = new ArrayList<>(playerMap.values());
        sortedPlayers.sort((a, b) -> Integer.compare(b.txCount, a.txCount));
        for (PlayerStats ps : sortedPlayers) {
            JsonObject po = new JsonObject();
            po.addProperty("uuid", ps.uuid);
            po.addProperty("name", ps.name);
            po.addProperty("transactions", ps.txCount);
            po.addProperty("spent", ps.spent);
            po.addProperty("earned", ps.earned);
            po.addProperty("net", ps.earned - ps.spent);

            // Player Categories
            JsonArray catArr = new JsonArray();
            for (CategoryStats cs : ps.categories.values()) {
                JsonObject cObj = new JsonObject();
                cObj.addProperty("categoryId", cs.categoryId);
                cObj.addProperty("spent", cs.spent);
                cObj.addProperty("earned", cs.earned);
                cObj.addProperty("units", cs.units);
                cObj.addProperty("transactions", cs.txCount);
                catArr.add(cObj);
            }
            po.add("categories", catArr);

            // Player Items
            JsonArray playerItemsArr = new JsonArray();
            List<PlayerItemStats> sortedPlayerItems = new ArrayList<>(ps.items.values());
            sortedPlayerItems.sort((a, b) -> Double.compare(b.spent + b.earned, a.spent + a.earned));
            for (PlayerItemStats pis : sortedPlayerItems) {
                JsonObject piObj = new JsonObject();
                piObj.addProperty("itemId", pis.itemId);
                piObj.addProperty("categoryId", pis.categoryId);
                piObj.addProperty("boughtUnits", pis.boughtUnits);
                piObj.addProperty("soldUnits", pis.soldUnits);
                piObj.addProperty("spent", pis.spent);
                piObj.addProperty("earned", pis.earned);
                piObj.addProperty("transactions", pis.txCount);
                playerItemsArr.add(piObj);
            }
            po.add("topItems", playerItemsArr);

            // Player Timeline
            JsonArray playerTimelineArr = new JsonArray();
            List<String> sortedPlayerDates = new ArrayList<>(ps.timeline.keySet());
            Collections.sort(sortedPlayerDates);
            for (String d : sortedPlayerDates) {
                DayStats pds = ps.timeline.get(d);
                JsonObject pdObj = new JsonObject();
                pdObj.addProperty("date", pds.date);
                pdObj.addProperty("buyMoney", pds.buyMoney);
                pdObj.addProperty("sellMoney", pds.sellMoney);
                pdObj.addProperty("buyUnits", pds.buyUnits);
                pdObj.addProperty("sellUnits", pds.sellUnits);
                pdObj.addProperty("transactions", pds.transactions);
                playerTimelineArr.add(pdObj);
            }
            po.add("timeline", playerTimelineArr);

            playersArr.add(po);
        }
        root.add("players", playersArr);

        // Recent Transactions (up to 1000 most recent for detailed inspection)
        JsonArray txArr = new JsonArray();
        int maxRecords = Math.min(records.size(), 1000);
        for (int i = records.size() - 1; i >= records.size() - maxRecords; i--) {
            TransactionRecord r = records.get(i);
            JsonObject to = new JsonObject();
            to.addProperty("timestamp", r.getTimestamp().toString());
            to.addProperty("playerName", r.getPlayerName());
            to.addProperty("playerId", r.getPlayerId().toString());
            to.addProperty("itemId", r.getItemId());
            to.addProperty("categoryId", r.getCategoryId());
            to.addProperty("type", r.getType().name());
            to.addProperty("amount", r.getAmount());
            to.addProperty("totalPrice", r.getTotalPrice());
            txArr.add(to);
        }
        root.add("recentTransactions", txArr);

        return root;
    }

    private static byte[] gzipCompress(String data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private static class ItemStats {
        final String itemId;
        final String categoryId;
        long soldUnits;
        double soldMoney;
        long boughtUnits;
        double boughtMoney;
        int txCount;
        final Map<String, ItemPlayerStats> traders = new HashMap<>();
        final Map<String, DayStats> timeline = new HashMap<>();

        ItemStats(String itemId, String categoryId) {
            this.itemId = itemId;
            this.categoryId = categoryId;
        }
    }

    private static class ItemPlayerStats {
        final String uuid;
        final String name;
        long boughtUnits;
        long soldUnits;
        double spent;
        double earned;
        int txCount;

        ItemPlayerStats(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static class PlayerStats {
        final String uuid;
        final String name;
        double spent;
        double earned;
        long boughtUnits;
        long soldUnits;
        int txCount;
        final Map<String, PlayerItemStats> items = new HashMap<>();
        final Map<String, CategoryStats> categories = new HashMap<>();
        final Map<String, DayStats> timeline = new HashMap<>();

        PlayerStats(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static class PlayerItemStats {
        final String itemId;
        final String categoryId;
        long boughtUnits;
        long soldUnits;
        double spent;
        double earned;
        int txCount;

        PlayerItemStats(String itemId, String categoryId) {
            this.itemId = itemId;
            this.categoryId = categoryId;
        }
    }

    private static class CategoryStats {
        final String categoryId;
        double spent;
        double earned;
        long units;
        int txCount;

        CategoryStats(String categoryId) {
            this.categoryId = categoryId;
        }
    }

    private static class DayStats {
        final String date;
        double buyMoney;
        double sellMoney;
        long buyUnits;
        long sellUnits;
        int transactions;

        DayStats(String date) {
            this.date = date;
        }
    }
}
