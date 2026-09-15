package me.usainsrht.basicshop.command;

import me.usainsrht.basicshop.analytics.AnalyticsWebUploader;
import me.usainsrht.basicshop.analytics.TopSellersEngine;
import me.usainsrht.basicshop.analytics.model.TopSellerItem;
import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.event.ShopOpenEvent;
import me.usainsrht.basicshop.api.event.ShopReloadEvent;
import me.usainsrht.basicshop.api.event.ShopViewType;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.api.model.TransactionRecord;
import me.usainsrht.basicshop.api.model.TransactionResult;
import me.usainsrht.basicshop.api.model.TransactionType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import me.usainsrht.basicshop.gui.CategoriesGui;
import me.usainsrht.basicshop.gui.QuickSellGui;
import me.usainsrht.basicshop.gui.TopSellersGui;
import me.usainsrht.basicshop.item.ShopToolFactory;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.itemapi.itemtext.ItemText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import space.arim.morepaperlib.MorePaperLib;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers all BasicShop commands via the Paper Brigadier lifecycle API.
 */
public final class ShopCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final ShopToolFactory toolFactory;
    private final MorePaperLib morePaperLib;
    private final TopSellersEngine topSellersEngine;
    private final AnalyticsWebUploader webUploader;

    public ShopCommand(
            Plugin plugin,
            ConfigManager configManager,
            ShopAPI shopAPI,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib
    ) {
        this(plugin, configManager, shopAPI, toolFactory, morePaperLib, null, null);
    }

    public ShopCommand(
            Plugin plugin,
            ConfigManager configManager,
            ShopAPI shopAPI,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib,
            TopSellersEngine topSellersEngine,
            AnalyticsWebUploader webUploader
    ) {
        this.plugin           = plugin;
        this.configManager    = configManager;
        this.shopAPI          = shopAPI;
        this.toolFactory      = toolFactory;
        this.morePaperLib     = morePaperLib;
        this.topSellersEngine = topSellersEngine;
        this.webUploader      = webUploader;
    }

    /**
     * Registers the command tree with the server's lifecycle event manager.
     * Call this from {@code onEnable()}.
     */
    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            MainConfig.CommandsConfig cmdCfg = configManager.getMainConfig().getCommandsConfig();
            commands.register(
                    buildCommandTree(cmdCfg),
                    "BasicShop main command.",
                    cmdCfg.aliases()
            );
            commands.register(
                    buildQuickSellNode(cmdCfg).build(),
                    "BasicShop quicksell command.",
                    cmdCfg.quicksellAliases()
            );
        });
    }

    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildCommandTree(MainConfig.CommandsConfig cmdCfg) {
        return Commands.literal(cmdCfg.root())
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        sendMessage(ctx.getSource().getSender(), "player-only");
                        return 0;
                    }
                    if (!player.hasPermission("basicshop.use")) {
                        sendMessage(player, "no-permission");
                        return 0;
                    }
                    openShop(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal(cmdCfg.sub("help"))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                sendMessage(ctx.getSource().getSender(), "player-only");
                                return 0;
                            }
                            if (!player.hasPermission("basicshop.use")) {
                                sendMessage(player, "no-permission");
                                return 0;
                            }
                            sendHelp(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal(cmdCfg.sub("reload"))
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.reload"))
                        .executes(ctx -> {
                            configManager.load();
                            if (topSellersEngine != null) {
                                topSellersEngine.recalculateAsync();
                            }
                            Bukkit.getPluginManager().callEvent(new ShopReloadEvent(ctx.getSource().getSender()));
                            configManager.getMessagesConfig().send(ctx.getSource().getSender(), "reload-success");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(buildQuickSellNode(cmdCfg))
                .then(buildTopNode(cmdCfg))
                .then(buildAdminNode(cmdCfg))
                .then(Commands.literal(cmdCfg.sub("give"))
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.give"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("tool", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (ShopToolType type : ShopToolType.values()) {
                                                builder.suggest(type.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(this::executeGive)))))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTopNode(MainConfig.CommandsConfig cmdCfg) {
        return Commands.literal(cmdCfg.sub("top"))
                .requires(src -> src.getSender().hasPermission("basicshop.top") || src.getSender().hasPermission("basicshop.use"))
                .executes(ctx -> {
                    String defView = configManager.getMainConfig().getAnalyticsSettings().topSellers().defaultView();
                    if ("gui".equalsIgnoreCase(defView) && ctx.getSource().getSender() instanceof Player player) {
                        openTopSellersGui(player);
                    } else {
                        executeTopChat(ctx.getSource().getSender());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("chat")
                        .executes(ctx -> {
                            executeTopChat(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("gui")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                sendMessage(ctx.getSource().getSender(), "player-only");
                                return 0;
                            }
                            openTopSellersGui(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildAdminNode(MainConfig.CommandsConfig cmdCfg) {
        return Commands.literal(cmdCfg.sub("admin"))
                .requires(src -> src.getSender().hasPermission("basicshop.admin"))
                .then(Commands.literal("analytics")
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.analytics") || src.getSender().hasPermission("basicshop.admin"))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("web");
                                    builder.suggest("ingame");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeAdminAnalytics(ctx, 7))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 365))
                                        .executes(ctx -> executeAdminAnalytics(ctx, IntegerArgumentType.getInteger(ctx, "days"))))))
                .then(Commands.literal("logs")
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.logs") || src.getSender().hasPermission("basicshop.admin"))
                        .then(Commands.argument("date", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("today");
                                    builder.suggest("yesterday");
                                    builder.suggest(LocalDate.now().toString());
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> executeAdminLogs(ctx, null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> executeAdminLogs(ctx, StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal(cmdCfg.sub("reload"))
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.reload") || src.getSender().hasPermission("basicshop.admin"))
                        .executes(ctx -> {
                            configManager.load();
                            if (topSellersEngine != null) {
                                topSellersEngine.recalculateAsync();
                            }
                            Bukkit.getPluginManager().callEvent(new ShopReloadEvent(ctx.getSource().getSender()));
                            configManager.getMessagesConfig().send(ctx.getSource().getSender(), "reload-success");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal(cmdCfg.sub("give"))
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.give") || src.getSender().hasPermission("basicshop.admin"))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("tool", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (ShopToolType type : ShopToolType.values()) {
                                                builder.suggest(type.getId());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(this::executeGive)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildQuickSellNode(MainConfig.CommandsConfig cmdCfg) {
        return Commands.literal(cmdCfg.sub("quicksell"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player player)) {
                        sendMessage(ctx.getSource().getSender(), "player-only");
                        return 0;
                    }
                    if (!player.hasPermission("basicshop.quicksell")) {
                        sendMessage(player, "no-permission");
                        return 0;
                    }
                    openQuickSell(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal(cmdCfg.sub("quicksell-hand"))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                sendMessage(ctx.getSource().getSender(), "player-only");
                                return 0;
                            }
                            if (!player.hasPermission("basicshop.quicksell.hand")) {
                                sendMessage(player, "no-permission");
                                return 0;
                            }
                            executeQuickSellHand(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal(cmdCfg.sub("quicksell-inventory"))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player player)) {
                                sendMessage(ctx.getSource().getSender(), "player-only");
                                return 0;
                            }
                            if (!player.hasPermission("basicshop.quicksell.inventory")) {
                                sendMessage(player, "no-permission");
                                return 0;
                            }
                            executeQuickSellInventory(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // Executors
    // -------------------------------------------------------------------------

    private void openShop(Player player) {
        ShopOpenEvent openEvent = new ShopOpenEvent(player, ShopViewType.CATEGORIES, null);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) return;

        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            CategoriesGui gui = new CategoriesGui(configManager, shopAPI, morePaperLib, topSellersEngine, player);
            player.openInventory(gui.getInventory());
        }, null);
    }

    private void openQuickSell(Player player) {
        ShopOpenEvent openEvent = new ShopOpenEvent(player, ShopViewType.QUICK_SELL, null);
        Bukkit.getPluginManager().callEvent(openEvent);
        if (openEvent.isCancelled()) return;

        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            QuickSellGui gui = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
            player.openInventory(gui.getInventory());
        }, null);
    }

    private void openTopSellersGui(Player player) {
        if (topSellersEngine == null) return;
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            TopSellersGui gui = new TopSellersGui(configManager, shopAPI, morePaperLib, topSellersEngine, player);
            player.openInventory(gui.getInventory());
        }, null);
    }

    private void executeTopChat(CommandSender sender) {
        if (topSellersEngine == null) return;
        int days = configManager.getMainConfig().getAnalyticsSettings().topSellers().days();
        List<TopSellerItem> topList = topSellersEngine.getTopSellers();

        if (topList.isEmpty()) {
            configManager.getMessagesConfig().send(sender, "top-sellers-empty",
                    Placeholder.unparsed("days", String.valueOf(days)));
            return;
        }

        configManager.getMessagesConfig().send(sender, "top-sellers-header",
                Placeholder.unparsed("days", String.valueOf(days)));

        for (TopSellerItem item : topList) {
            ItemStack stack = new ItemStack(item.material());
            Component itemComp = ItemText.format(stack, b -> b.amount(1));
            String profitStr = configManager.getMainConfig().formatPrice(item.totalProfit());

            configManager.getMessagesConfig().send(sender, "top-sellers-format",
                    Placeholder.unparsed("rank", String.valueOf(item.rank())),
                    Placeholder.component("item", itemComp),
                    Placeholder.unparsed("amount", String.valueOf(item.unitsSold())),
                    Placeholder.unparsed("total_profit", profitStr));
        }

        configManager.getMessagesConfig().send(sender, "top-sellers-footer");
    }

    private int executeAdminAnalytics(CommandContext<CommandSourceStack> ctx, int days) {
        CommandSender sender = ctx.getSource().getSender();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();

        if ("web".equals(mode)) {
            if (webUploader == null) return 0;
            configManager.getMessagesConfig().send(sender, "analytics-uploading",
                    Placeholder.unparsed("days", String.valueOf(days)));

            webUploader.uploadAsync(days).thenAccept(result -> {
                if (result.success()) {
                    configManager.getMessagesConfig().send(sender, "analytics-upload-success",
                            Placeholder.parsed("url", result.url()),
                            Placeholder.unparsed("expiration", String.valueOf(result.expirationHours())));
                } else {
                    configManager.getMessagesConfig().send(sender, "analytics-upload-failed",
                            Placeholder.unparsed("reason", result.error() != null ? result.error() : "Unknown error"));
                }
            });
            return Command.SINGLE_SUCCESS;
        } else if ("ingame".equals(mode)) {
            if (topSellersEngine == null) return 0;
            morePaperLib.scheduling().asyncScheduler().run(() -> {
                List<TransactionRecord> records = topSellersEngine.readRecordsForDays(days);
                double totalBought = 0;
                double totalSold = 0;
                long totalUnits = 0;

                for (TransactionRecord r : records) {
                    if (r.getType() == TransactionType.BUY) {
                        totalBought += r.getTotalPrice();
                    } else {
                        totalSold += r.getTotalPrice();
                    }
                    totalUnits += r.getAmount();
                }

                double net = totalBought - totalSold;
                String coloredNet = (net >= 0 ? "<green>+" : "<red>") + configManager.getMainConfig().formatPrice(net);

                configManager.getMessagesConfig().send(sender, "analytics-ingame-header",
                        Placeholder.unparsed("days", String.valueOf(days)));
                configManager.getMessagesConfig().send(sender, "analytics-ingame-summary",
                        Placeholder.unparsed("transactions", String.valueOf(records.size())),
                        Placeholder.unparsed("units", String.valueOf(totalUnits)),
                        Placeholder.unparsed("total_sold", configManager.getMainConfig().formatPrice(totalSold)),
                        Placeholder.unparsed("total_bought", configManager.getMainConfig().formatPrice(totalBought)),
                        Placeholder.parsed("net_delta", coloredNet));
                configManager.getMessagesConfig().send(sender, "analytics-ingame-footer");
            });
            return Command.SINGLE_SUCCESS;
        } else {
            sender.sendMessage(MM.deserialize("<red>Invalid mode '<white>" + mode + "</white>'. Use <white>web</white> or <white>ingame</white>."));
            return 0;
        }
    }

    private int executeAdminLogs(CommandContext<CommandSourceStack> ctx, String playerFilter) {
        CommandSender sender = ctx.getSource().getSender();
        String rawDate = StringArgumentType.getString(ctx, "date").trim().toLowerCase();

        LocalDate targetDate;
        if ("today".equals(rawDate)) {
            targetDate = LocalDate.now();
        } else if ("yesterday".equals(rawDate)) {
            targetDate = LocalDate.now().minusDays(1);
        } else {
            try {
                targetDate = LocalDate.parse(rawDate);
            } catch (Exception e) {
                configManager.getMessagesConfig().send(sender, "logs-invalid-date",
                        Placeholder.unparsed("date", rawDate));
                return 0;
            }
        }

        if (topSellersEngine == null) return 0;

        morePaperLib.scheduling().asyncScheduler().run(() -> {
            List<TransactionRecord> list = topSellersEngine.readRecordsForDate(targetDate);
            if (playerFilter != null && !playerFilter.isBlank()) {
                String filterLower = playerFilter.toLowerCase();
                list = list.stream()
                        .filter(r -> r.getPlayerName().toLowerCase().equals(filterLower) || r.getPlayerId().toString().equalsIgnoreCase(filterLower))
                        .toList();
            }

            if (list.isEmpty()) {
                configManager.getMessagesConfig().send(sender, "logs-empty",
                        Placeholder.unparsed("date", targetDate.toString()));
                return;
            }

            configManager.getMessagesConfig().send(sender, "logs-header",
                    Placeholder.unparsed("date", targetDate.toString()));

            double totalSold = 0;
            double totalBought = 0;

            int start = Math.max(0, list.size() - 10);
            for (int i = start; i < list.size(); i++) {
                TransactionRecord r = list.get(i);
                if (r.getType() == TransactionType.BUY) {
                    totalBought += r.getTotalPrice();
                } else {
                    totalSold += r.getTotalPrice();
                }

                String timeStr = r.getTimestamp().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String actionColor = r.getType() == TransactionType.BUY ? "<gold>" : "<green>";
                ItemStack stack = new ItemStack(resolveMat(r.getItemId()));
                Component itemComp = ItemText.format(stack, b -> b.amount(r.getAmount()));
                String coloredAction = actionColor + r.getType().name();

                configManager.getMessagesConfig().send(sender, "logs-line",
                        Placeholder.unparsed("time", timeStr),
                        Placeholder.parsed("action", coloredAction),
                        Placeholder.unparsed("amount", String.valueOf(r.getAmount())),
                        Placeholder.component("item", itemComp),
                        Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(r.getTotalPrice())),
                        Placeholder.unparsed("player", r.getPlayerName()));
            }

            for (int i = 0; i < start; i++) {
                TransactionRecord r = list.get(i);
                if (r.getType() == TransactionType.BUY) {
                    totalBought += r.getTotalPrice();
                } else {
                    totalSold += r.getTotalPrice();
                }
            }

            configManager.getMessagesConfig().send(sender, "logs-summary",
                    Placeholder.unparsed("count", String.valueOf(list.size())),
                    Placeholder.unparsed("total_sold", configManager.getMainConfig().formatPrice(totalSold)),
                    Placeholder.unparsed("total_bought", configManager.getMainConfig().formatPrice(totalBought)));
        });

        return Command.SINGLE_SUCCESS;
    }

    private Material resolveMat(String itemId) {
        if (shopAPI != null) {
            Optional<ShopItem> item = shopAPI.getItem(itemId);
            if (item.isPresent()) {
                return item.get().getMaterial();
            }
        }
        int colon = itemId.indexOf(':');
        String name = colon != -1 ? itemId.substring(colon + 1) : itemId;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : Material.CHEST;
    }

    private void sendHelp(Player player) {
        MainConfig.CommandsConfig cmdCfg = configManager.getMainConfig().getCommandsConfig();
        String prefix = configManager.getMessagesConfig().getPrefix();
        String root   = cmdCfg.root();
        player.sendMessage(MM.deserialize(prefix + "<yellow>BasicShop Commands:"));
        player.sendMessage(MM.deserialize("<gold>/" + root + "</gold> <gray>— Open the shop"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("help") + "</gold> <gray>— Show this message"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("top") + "</gold> <gray>— View top selling shop items"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("quicksell") + " " + cmdCfg.sub("quicksell-hand") + "</gold> <gray>— Sell the item in your hand"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("quicksell") + " " + cmdCfg.sub("quicksell-inventory") + "</gold> <gray>— Sell all sellable items"));
        if (player.hasPermission("basicshop.admin.analytics") || player.hasPermission("basicshop.admin")) {
            player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("admin") + " analytics <web|ingame> [days]</gold> <gray>— View/upload shop analytics"));
        }
        if (player.hasPermission("basicshop.admin.logs") || player.hasPermission("basicshop.admin")) {
            player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("admin") + " logs <date> [<player>]</gold> <gray>— View transaction logs"));
        }
        if (player.hasPermission("basicshop.admin.reload")) {
            player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("reload") + "</gold> <gray>— Reload configuration"));
        }
        if (player.hasPermission("basicshop.admin.give")) {
            player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("give") + " <player> <tool> <amount></gold> <gray>— Give a shop tool"));
        }
    }

    private int executeGive(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String targetName = StringArgumentType.getString(ctx, "target");
        String toolId = StringArgumentType.getString(ctx, "tool");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sendMessage(sender, "give-player-not-found");
            return 0;
        }

        ShopToolType toolType = ShopToolType.fromId(toolId).orElse(null);
        if (toolType == null) {
            sendMessage(sender, "give-invalid-tool");
            return 0;
        }

        ItemStack stack = toolFactory.create(toolType, amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
        overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));

        configManager.getMessagesConfig().send(sender, "give-success",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("tool", toolType.getId()),
                Placeholder.unparsed("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private void sendMessage(CommandSender sender, String messageKey) {
        configManager.getMessagesConfig().send(sender, messageKey);
    }

    private void executeQuickSellHand(Player player) {
        var handStack = player.getInventory().getItemInMainHand().clone();
        var result = shopAPI.quickSellHand(player);
        if (result == TransactionResult.CANCELLED) {
            return;
        }
        String key = switch (result) {
            case SUCCESS               -> null;
            case NOT_ENOUGH_ITEMS      -> "quicksell-hand-empty";
            case SELL_DISABLED         -> "item-sell-disabled";
            case GLOBAL_SELL_DISABLED  -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE   -> "vault-unavailable";
            default                    -> "vault-unavailable";
        };
        if (key != null) {
            configManager.getMessagesConfig().send(player, key);
        } else {
            int amount = handStack.getAmount();
            double earned = shopAPI.getItemByMaterial(handStack.getType())
                    .flatMap(si -> si.getSellPrice().isPresent() ? java.util.Optional.of(si.getSellPrice().getAsDouble() * amount) : java.util.Optional.empty())
                    .orElse(0.0);

            ItemStack itemStack = handStack.clone();
            itemStack.setAmount(1);

            Component itemTextComp = ItemText.format(itemStack, b -> b.amount(amount));
            configManager.getMessagesConfig().send(player, "sell-success",
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.component("item", itemTextComp),
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(earned)));
        }
    }

    private void executeQuickSellInventory(Player player) {
        ShopAPI.QuickSellResult result = shopAPI.quickSellInventory(player);
        if (result.anySuccess()) {
            configManager.getMessagesConfig().send(player, "quicksell-inventory-success",
                    Placeholder.unparsed("price", configManager.getMainConfig().formatPrice(result.totalEarned())));
        } else {
            configManager.getMessagesConfig().send(player, "no-sellable-items");
        }
    }
}
