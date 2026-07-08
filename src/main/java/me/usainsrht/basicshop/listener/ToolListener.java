package me.usainsrht.basicshop.listener;

import me.usainsrht.basicshop.api.ShopAPI;
import me.usainsrht.basicshop.api.model.ShopItem;
import me.usainsrht.basicshop.api.model.ShopToolType;
import me.usainsrht.basicshop.config.ConfigManager;
import me.usainsrht.basicshop.item.ShopToolFactory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import space.arim.morepaperlib.MorePaperLib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Handles Money Staff and Money Hoe interactions.
 *
 * <p>Restrictions run at HIGH priority; sell/replant logic runs at MONITOR and
 * intentionally observes cancelled hoe breaks.
 */
public final class ToolListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Drop types that cost one item to replant when auto-selling (replaces legacy name substring checks). */
    private static final Set<Material> REPLANT_COST_DROPS = Set.of(
            Material.WHEAT_SEEDS,
            Material.BEETROOT_SEEDS,
            Material.MELON_SEEDS,
            Material.PUMPKIN_SEEDS,
            Material.NETHER_WART,
            Material.POTATO,
            Material.CARROT
    );

    private final ConfigManager configManager;
    private final ShopAPI shopAPI;
    private final ShopToolFactory toolFactory;
    private final MorePaperLib morePaperLib;

    public ToolListener(
            ConfigManager configManager,
            ShopAPI shopAPI,
            ShopToolFactory toolFactory,
            MorePaperLib morePaperLib
    ) {
        this.configManager = configManager;
        this.shopAPI = shopAPI;
        this.toolFactory = toolFactory;
        this.morePaperLib = morePaperLib;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ShopToolType type = toolFactory.getToolType(event.getItemInHand());
        //hoe tilling also fires blockplaceevent 
        if (type == ShopToolType.MONEY_STAFF) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        ShopToolType type = toolFactory.getToolType(tool);
        if (type == null) return;

        if (type == ShopToolType.MONEY_STAFF) {
            event.setCancelled(true);
            return;
        }

        if (type == ShopToolType.MONEY_HOE && event.getBlock().getBlockData() instanceof Ageable) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToolInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        ShopToolType type = toolFactory.getToolType(item);
        if (type == null) return;

        Action action = event.getAction();
        if (type == ShopToolType.MONEY_STAFF) {
            if (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK
                    || action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR
                    || action == Action.PHYSICAL) {
                event.setCancelled(true);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onStaffUse(PlayerInteractEvent event) {
        if (!event.hasBlock() || !event.hasItem()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (toolFactory.getToolType(item) != ShopToolType.MONEY_STAFF) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("basicshop.tools.staff")) return;

        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null) return;

        BlockState state = block.getState();
        if (!(state instanceof Container)) return;

        Location location = block.getLocation();
        morePaperLib.scheduling().regionSpecificScheduler(location).runDelayed(
                () -> sellItemsInBlock(player, block),
                1L
        );
    }

    private void sellItemsInBlock(Player player, Block block) {
        if (!player.isOnline()) return;

        BlockState state = block.getState();
        if (!(state instanceof Container container)) return;

        ShopAPI.QuickSellResult result = shopAPI.sellFromInventory(player, container.getInventory());
        if (!result.anySuccess()) return;

        String prefix = configManager.getMainConfig().getPrefix();
        String template = configManager.getMainConfig().getMessage("tool-staff-line");
        for (ShopAPI.SoldMaterialLine line : result.lines()) {
            String msg = template
                    .replace("<amount>", String.valueOf(line.amount()))
                    .replace("<item>", "<lang:" + line.material().translationKey() + ">")
                    .replace("<price>", configManager.getMainConfig().formatPrice(line.earned()));
            player.sendMessage(MM.deserialize(prefix + msg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHoeToggle(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_AIR) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (toolFactory.getToolType(item) != ShopToolType.MONEY_HOE) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("basicshop.tools.hoe")) return;

        boolean autoSellEnabled = toolFactory.toggleAutoSell(item);
        player.getInventory().setItemInMainHand(item);

        String prefix = configManager.getMainConfig().getPrefix();
        String key = autoSellEnabled ? "tool-hoe-autosell-on" : "tool-hoe-autosell-off";
        player.sendActionBar(MM.deserialize(prefix + configManager.getMainConfig().getMessage(key)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHoeBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (toolFactory.getToolType(tool) != ShopToolType.MONEY_HOE) return;
        if (!player.hasPermission("basicshop.tools.hoe")) return;

        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable)) return;
        if (!isFullyGrown(block)) return;

        event.setCancelled(true);
        event.setDropItems(false);

        Collection<ItemStack> drops = block.getDrops(tool);
        boolean autoSellEnabled = toolFactory.isAutoSellEnabled(tool);

        if (autoSellEnabled) {
            handleAutoSell(player, block, drops);
        } else {
            giveDrops(player, block, drops);
        }

        scheduleReplant(block);
    }

    private void handleAutoSell(Player player, Block block, Collection<ItemStack> drops) {
        List<ItemStack> sellable = new ArrayList<>();
        List<ItemStack> unsellable = new ArrayList<>();
        double totalEarned = 0;
        int totalSold = 0;

        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;

            ItemStack adjusted = drop.clone();
            applyReplantCost(adjusted);
            if (adjusted.getAmount() <= 0) continue;

            Optional<ShopItem> shopItemOpt = shopAPI.getItemByMaterial(adjusted.getType());
            if (shopItemOpt.isEmpty()) {
                unsellable.add(adjusted);
                continue;
            }

            OptionalDouble priceOpt = shopItemOpt.get().getSellPrice();
            if (priceOpt.isEmpty()) {
                unsellable.add(adjusted);
                continue;
            }

            totalEarned += priceOpt.getAsDouble() * adjusted.getAmount();
            totalSold += adjusted.getAmount();
            sellable.add(adjusted);
        }

        if (totalSold > 0) {
            shopAPI.sellItemStacks(player, sellable);
            String msg = configManager.getMainConfig().getMessage("tool-hoe-success")
                    .replace("<price>", configManager.getMainConfig().formatPrice(totalEarned));
            player.sendActionBar(MM.deserialize(msg));
        }

        for (ItemStack leftover : unsellable) {
            block.getWorld().dropItemNaturally(block.getLocation(), leftover);
        }
    }

    private static void giveDrops(Player player, Block block, Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop.clone());
            for (ItemStack overflow : leftover.values()) {
                block.getWorld().dropItemNaturally(block.getLocation(), overflow);
            }
        }
    }

    private static void applyReplantCost(ItemStack drop) {
        if (!REPLANT_COST_DROPS.contains(drop.getType())) return;
        drop.setAmount(drop.getAmount() - 1);
    }

    private static boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }

    private void scheduleReplant(Block block) {
        Location location = block.getLocation();
        Material cropType = block.getType();
        BlockData replantData = block.getBlockData();

        if (replantData instanceof Ageable ageable) {
            ageable.setAge(0);
            replantData = ageable;
        }

        BlockData finalData = replantData;
        morePaperLib.scheduling().regionSpecificScheduler(location).run(() -> {
            Block replantBlock = location.getBlock();
            if (replantBlock.getType() != Material.AIR && replantBlock.getType() != cropType) return;
            replantBlock.setType(cropType, false);
            replantBlock.setBlockData(finalData, false);
        });
    }
}
