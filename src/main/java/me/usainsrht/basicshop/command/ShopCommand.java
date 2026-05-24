package me.usainsrht.basicshop.command;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.gui.CategoriesGui;
import me.usainsrht.basicshop.gui.QuickSellGui;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import space.arim.morepaperlib.MorePaperLib;

import java.util.List;

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
 * </pre>
 *
 * <p>Every sub-command with restricted access uses {@code .requires()} to guard execution.
 */
public final class ShopCommand {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final MorePaperLib morePaperLib;

    public ShopCommand(Plugin plugin, ConfigManager configManager, ShopAPI shopAPI, MorePaperLib morePaperLib) {
        this.plugin        = plugin;
        this.configManager = configManager;
        this.shopAPI       = shopAPI;
        this.morePaperLib  = morePaperLib;
    }

    /**
     * Registers the command tree with the server's lifecycle event manager.
     * Call this from {@code onEnable()}.
     */
    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    buildCommandTree(),
                    "BasicShop main command.",
                    List.of("bs", "basicshop")
            );
        });
    }

    // -------------------------------------------------------------------------
    // Command tree
    // -------------------------------------------------------------------------

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> buildCommandTree() {
        return Commands.literal("shop")
                .requires(src -> src.getSender() instanceof Player
                        && src.getSender().hasPermission("basicshop.use"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    openShop(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("basicshop.use"))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            sendHelp(player);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission("basicshop.admin.reload"))
                        .executes(ctx -> {
                            configManager.load();
                            String msg = configManager.getMainConfig().getPrefix()
                                    + configManager.getMainConfig().getMessage("reload-success");
                            ctx.getSource().getSender().sendMessage(MM.deserialize(msg));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("quicksell")
                        .requires(src -> src.getSender() instanceof Player
                                && src.getSender().hasPermission("basicshop.quicksell"))
                        .executes(ctx -> {
                            Player player = (Player) ctx.getSource().getSender();
                            openQuickSell(player);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("hand")
                                .requires(src -> src.getSender() instanceof Player
                                        && src.getSender().hasPermission("basicshop.quicksell.hand"))
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    executeQuickSellHand(player);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("inventory")
                                .requires(src -> src.getSender() instanceof Player
                                        && src.getSender().hasPermission("basicshop.quicksell.inventory"))
                                .executes(ctx -> {
                                    Player player = (Player) ctx.getSource().getSender();
                                    executeQuickSellInventory(player);
                                    return Command.SINGLE_SUCCESS;
                                })))
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

    private void sendHelp(Player player) {
        String prefix = configManager.getMainConfig().getPrefix();
        player.sendMessage(MM.deserialize(prefix + "<yellow>BasicShop Commands:"));
        player.sendMessage(MM.deserialize("<gold>/shop</gold> <gray>— Open the shop"));
        player.sendMessage(MM.deserialize("<gold>/shop help</gold> <gray>— Show this message"));
        player.sendMessage(MM.deserialize("<gold>/shop quicksell hand</gold> <gray>— Sell the item in your hand"));
        player.sendMessage(MM.deserialize("<gold>/shop quicksell inventory</gold> <gray>— Sell all sellable items"));
        if (player.hasPermission("basicshop.admin.reload")) {
            player.sendMessage(MM.deserialize("<gold>/shop reload</gold> <gray>— Reload configuration"));
        }
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
