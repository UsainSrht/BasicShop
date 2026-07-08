package me.usainsrht.basicshop.command;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.config.MainConfig;
import me.usainsrht.basicshop.gui.CategoriesGui;
import me.usainsrht.basicshop.gui.QuickSellGui;
import me.usainsrht.basicshop.item.ShopToolFactory;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import space.arim.morepaperlib.MorePaperLib;

import java.util.Map;

/**
 * Registers all BasicShop commands via the Paper Brigadier lifecycle API.
 *
 * <p>Commands:
 * <pre>
 *   /shop                             — open shop GUI              [basicshop.use]
 *   /shop help                        — show help                  [basicshop.use]
 *   /shop reload                      — reload configuration       [basicshop.admin.reload]
 *   /shop quicksell hand              — sell held item             [basicshop.quicksell.hand]
 *   /shop quicksell inventory         — sell all inventory items   [basicshop.quicksell.inventory]
 *   /shop give &lt;player&gt; &lt;tool&gt; &lt;amount&gt; — give a shop tool        [basicshop.admin.give]
 * </pre>
 *
 * <p>Every sub-command with restricted access uses {@code .requires()} to guard execution.
 */
public final class ShopCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final ShopToolFactory toolFactory;
    private final MorePaperLib morePaperLib;

    public ShopCommand(
            Plugin plugin,
            ConfigManager configManager,
            ShopAPI shopAPI,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib
    ) {
        this.plugin        = plugin;
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.toolFactory   = toolFactory;
        this.morePaperLib  = morePaperLib;
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
                            sendHelp(player, cmdCfg);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal(cmdCfg.sub("reload"))
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.reload"))
                        .executes(ctx -> {
                            configManager.load();
                            String msg = configManager.getMainConfig().getPrefix()
                                    + configManager.getMainConfig().getMessage("reload-success");
                            ctx.getSource().getSender().sendMessage(MM.deserialize(msg));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal(cmdCfg.sub("quicksell"))
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
                                })))
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

    // -------------------------------------------------------------------------
    // Executors
    // -------------------------------------------------------------------------

    private void openShop(Player player) {
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            CategoriesGui gui = new CategoriesGui(configManager, shopAPI, morePaperLib, player);
            player.openInventory(gui.getInventory());
        }, null);
    }

    private void openQuickSell(Player player) {
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> {
            QuickSellGui gui = new QuickSellGui(configManager, shopAPI, morePaperLib, player);
            player.openInventory(gui.getInventory());
        }, null);
    }

    private void sendHelp(Player player, MainConfig.CommandsConfig cmdCfg) {
        String prefix = configManager.getMainConfig().getPrefix();
        String root   = cmdCfg.root();
        player.sendMessage(MM.deserialize(prefix + "<yellow>BasicShop Commands:"));
        player.sendMessage(MM.deserialize("<gold>/" + root + "</gold> <gray>— Open the shop"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("help") + "</gold> <gray>— Show this message"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("quicksell") + " " + cmdCfg.sub("quicksell-hand") + "</gold> <gray>— Sell the item in your hand"));
        player.sendMessage(MM.deserialize("<gold>/" + root + " " + cmdCfg.sub("quicksell") + " " + cmdCfg.sub("quicksell-inventory") + "</gold> <gray>— Sell all sellable items"));
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

        String msg = configManager.getMainConfig().getMessage("give-success")
                .replace("<amount>", String.valueOf(amount))
                .replace("<tool>", toolType.getId())
                .replace("<player>", target.getName());
        sendRaw(sender, msg);
        return Command.SINGLE_SUCCESS;
    }

    private void sendMessage(CommandSender sender, String messageKey) {
        sendRaw(sender, configManager.getMainConfig().getMessage(messageKey));
    }

    private void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(MM.deserialize(configManager.getMainConfig().getPrefix() + message));
    }

    private void executeQuickSellHand(Player player) {
        var result = shopAPI.quickSellHand(player);
        String prefix = configManager.getMainConfig().getPrefix();
        String key = switch (result) {
            case SUCCESS               -> null;
            case NOT_ENOUGH_ITEMS      -> "quicksell-hand-empty";
            case SELL_DISABLED         -> "item-sell-disabled";
            case GLOBAL_SELL_DISABLED  -> "shop-sell-disabled";
            case ECONOMY_UNAVAILABLE   -> "vault-unavailable";
            default                    -> "vault-unavailable";
        };
        if (key != null) {
            player.sendMessage(MM.deserialize(prefix + configManager.getMainConfig().getMessage(key)));
        } else {
            // Success message with item/price info
            var hand = player.getInventory().getItemInMainHand();
            String msg = configManager.getMainConfig().getMessage("sell-success")
                    .replace("<amount>", "?")
                    .replace("<item>", "<lang:" + hand.getType().translationKey() + ">")
                    .replace("<price>", "?");
            player.sendMessage(MM.deserialize(prefix + msg));
        }
    }

    private void executeQuickSellInventory(Player player) {
        ShopAPI.QuickSellResult result = shopAPI.quickSellInventory(player);
        String prefix = configManager.getMainConfig().getPrefix();
        if (result.anySuccess()) {
            String msg = configManager.getMainConfig().getMessage("quicksell-inventory-success")
                    .replace("<price>", String.format("%.2f", result.totalEarned()));
            player.sendMessage(MM.deserialize(prefix + msg));
        } else {
            player.sendMessage(MM.deserialize(prefix + configManager.getMainConfig().getMessage("no-sellable-items")));
        }
    }
}
