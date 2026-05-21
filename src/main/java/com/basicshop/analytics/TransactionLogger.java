package com.basicshop.analytics;

import com.basicshop.api.model.TransactionRecord;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Appends transaction records to a daily-rotated CSV log file asynchronously.
 * Also persists an analytics summary on shutdown.
 */
public final class TransactionLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Plugin plugin;
    private final AnalyticsManager analyticsManager;
    private final Queue<TransactionRecord> pending = new ConcurrentLinkedQueue<>();

    private LocalDate currentDate;
    private File currentLogFile;
    private ScheduledTask flushTask;

    // Reference to MorePaperLib scheduling - injected at start
    private space.arim.morepaperlib.MorePaperLib morePaperLib;

    public TransactionLogger(Plugin plugin, AnalyticsManager analyticsManager, space.arim.morepaperlib.MorePaperLib morePaperLib) {
        this.plugin           = plugin;
        this.analyticsManager = analyticsManager;
        this.morePaperLib     = morePaperLib;

        File analyticsDir = new File(plugin.getDataFolder(), "analytics");
        if (!analyticsDir.exists()) {
            analyticsDir.mkdirs();
        }
    }

    /**
     * Starts the periodic flush task (every 30 seconds).
     */
    public void start() {
        currentDate    = LocalDate.now();
        currentLogFile = resolveLogFile(currentDate);
        ensureHeader(currentLogFile);

        // Flush every 30 seconds on an async thread
        flushTask = morePaperLib.scheduling().asyncScheduler().runAtFixedRate(
                (Runnable) this::flush, java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(30));
    }

    /**
     * Stops the flush task and performs a final synchronous flush.
     */
    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flush(); // final flush on calling thread (disable thread)
        saveSummary();
    }

    /**
     * Queues a transaction record for async logging.
     * This method is non-blocking and safe to call from any thread.
     */
    public void log(TransactionRecord record) {
        pending.offer(record);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void flush() {
        if (pending.isEmpty()) return;

        // Rotate file if the date changed
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            currentDate    = today;
            currentLogFile = resolveLogFile(currentDate);
            ensureHeader(currentLogFile);
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(currentLogFile, true))) {
            TransactionRecord record;
            while ((record = pending.poll()) != null) {
                writer.println(record.toCsvRow());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to write analytics log", e);
        }
    }

    private File resolveLogFile(LocalDate date) {
        String name = "transactions-" + date.format(DATE_FORMAT) + ".csv";
        return new File(plugin.getDataFolder(), "analytics/" + name);
    }

    private void ensureHeader(File file) {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
                try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
                    writer.println(TransactionRecord.csvHeader());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create analytics log file", e);
            }
        }
    }

    private void saveSummary() {
        File summaryFile = new File(plugin.getDataFolder(), "analytics/summary.yml");
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            Map<String, Object> data = analyticsManager.exportSummary();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                cfg.set(entry.getKey(), entry.getValue());
            }
            cfg.save(summaryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save analytics summary", e);
        }
    }
}
