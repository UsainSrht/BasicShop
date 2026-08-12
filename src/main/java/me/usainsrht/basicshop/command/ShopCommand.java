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
 *   /quicksell                        — open quicksell GUI         [basicshop.quicksell]
 *   /quicksell hand                   — sell held item             [basicshop.quicksell.hand]
 *   /quicksell inventory              — sell all inventory items   [basicshop.quicksell.inventory]
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
                            configManager.getMessagesConfig().send(ctx.getSource().getSender(), "reload-success");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(buildQuickSellNode(cmdCfg))
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
        MainConfig.CommandsConfig cmdCfg = configManager.getMainConfig().getCommandsConfig();
        String prefix = configManager.getMessagesConfig().getPrefix();
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

        configManager.getMessagesConfig().send(sender, "give-success",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("tool", toolType.getId()),
                Placeholder.unparsed("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private void sendMessage(CommandSender sender, String messageKey) {
        configManager.getMessagesConfig().send(sender, messageKey);
    }

    private void sendRaw(CommandSender sender, String message) {
        configManager.getMessagesConfig().send(sender, message);
    }

    private void executeQuickSellHand(Player player) {
        var handStack = player.getInventory().getItemInMainHand().clone();
        var result = shopAPI.quickSellHand(player);
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
