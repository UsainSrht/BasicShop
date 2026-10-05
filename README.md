# BasicShop

A modern, lightweight, and high-performance shop plugin for Minecraft **Paper** and **Folia** (1.21+). Designed from the ground up for smooth server performance, clean aesthetics with MiniMessage formatting, robust exploit protections, and deep gameplay integration through custom Shop Tools and analytics.

---

## 🌟 Key Features

- **⚡ Folia & Paper Native**: Built using [MorePaperLib](https://github.com/Anon8281/MorePaperLib) for thread-safe region and entity scheduling. Runs flawlessly on single-threaded Paper, Purpur, and multi-threaded Folia.
- **🎨 Modern MiniMessage Formatting**: Full support for [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags, gradients, hover events, and click actions in all messages and GUI items.
- **📁 Modular Category Structure**: Keep your shop organized with individual YAML category files (`categories/*.yml`) or a single `categories.yml`.
- **🛒 Dynamic GUI & Layouts**:
  - Main categories overview with customizable icons and layout.
  - Paginated item catalogues with configurable page sizes, borders, and modern bordered layouts (7 items per row).
  - Configurable click interactions per category (Buy 1, Buy 64, Sell 1, Sell 64, Sell All via drop key).
- **⚡ QuickSell System**:
  - `/quicksell`: Interactive GUI displaying all sellable items in the player's inventory, with per-item selling or a single-click **Sell All** button.
  - `/quicksell hand`: Fast selling of the currently held main-hand item.
  - `/quicksell inventory`: Instant bulk selling of all sellable items in inventory via command.
- **📊 Real-Time Analytics & Top Sellers**:
  - Tracks every transaction asynchronously via an embedded SQLite database or async file logger.
  - In-game Top Sellers ranking (`/shop top`) accessible through chat or an interactive GUI.
  - Cloudflare Worker web analytics dashboard (`/shop admin analytics web`) with sharable, expiring dashboard links (similar to Spark or mclo.gs).
  - In-game audit logs with date and player filtering (`/shop admin logs`).
- **🔧 Special Shop Tools**: Custom PersistentDataContainer (PDC) items (Money Staff, Money Hoe, Sorting Staff, Bazaar Staff) offering unique selling and inventory management mechanics.
- **🧩 Integrations**:
  - Hooks into [Vault](https://www.spigotmc.org/resources/vault.34315/) economy providers.
  - [PlaceholderAPI](https://wiki.placeholderapi.com/) and [MiniPlaceholders](https://miniplaceholders.nova54.dev/) expansions for Top Sellers data.
- **💻 Developer API**: Rich event lifecycle (`ShopPreTransactionEvent`, `ShopPostTransactionEvent`, `MoneyStaffCursorSellEvent`, etc.) and a full `ShopAPI` service.

---

## 🔨 Shop Tools

BasicShop includes specialized utility tools that can be granted to players using `/shop give <target> <tool> [amount]`. Each tool is identified using custom PersistentDataContainer (PDC) tags, meaning custom names or lore changes will never break their functionality.

### 1. Money Staff (`money_staff`)
The **Money Staff** is a versatile selling tool that works both on world container blocks and directly inside inventory screens.

#### World Container Interactions
- **Right-Click Container Block**: Instantly sells all sellable items stored inside the container (chests, barrels, shulker boxes, hoppers, etc.).
- **Left-Click Container Block**: Toggles recursive selling mode for container items (e.g. selling contents of Shulker Boxes stored inside a chest without destroying the Shulker Boxes).
- **Right-Click Air**: Toggles recursive selling mode on the held staff.

#### In-Inventory Cursor Actions
When managing items inside an inventory (player inventory, chests, etc.), the Money Staff provides bidirectional selling interactions using the cursor:

| Cursor Item | Clicked Slot | Click Action | Result & Action Taken |
| :--- | :--- | :--- | :--- |
| **Money Staff** | Any Item Stack | **Left-Click** | **Sells the clicked slot.** If the item is a container (e.g., Shulker Box) and recursive selling is enabled, its internal contents are sold. The Money Staff remains on the cursor. |
| **Money Staff** | Any Item Stack | **Right-Click** | **Sells all matching items in the inventory.** Sells all items in the clicked inventory matching the clicked item's material (including inside container items if recursive). The Money Staff remains on the cursor. |
| Any Item Stack | **Money Staff** | **Left-Click** | **Sells the cursor slot.** Sells the item stack currently held on the player's cursor. If the cursor item is a container and recursive mode is on, items inside are sold and the container is updated in place. |
| Any Item Stack | **Money Staff** | **Right-Click** | **Sells matching inventory items AND the cursor slot.** Sells all matching items of that type located inside the clicked inventory, and then sells the item stack on the player's cursor. Both sales are merged into a single summary payout message. |
| *Any Tool / Item* | *Any Slot* | *Any Click in **Creative Mode*** | **Disabled completely (Early Exit).** Events are uncancelled and untouched by the plugin, ensuring vanilla Creative mode item-cloning and inventory mechanics remain intact. |

#### Architectural Protections & Exploit Prevention
- **1-Tick Deferred Execution**: In Survival mode, the `InventoryClickEvent` is cancelled immediately so vanilla never moves or swaps items. Item removal and economy deposits are scheduled on the next tick via `MorePaperLib`, completely eliminating client/server cursor desyncs, item duplication, or ghost items.
- **Shop Tool Immunity**: Shop tools (Money Staff, Money Hoe, Sorting Staff) can never be sold by accident.
- **Crafting Grid Protection**: Players cannot sell items placed in the 2x2 player crafting grid or crafting result slot.
- **Cooldown & Riding Checks**: Configurable click cooldown (`cursor-cooldown-seconds`) prevents macro spamming, and riding restrictions (`usable-when-riding: false`) prevent exploitation while on mounts or vehicles.
- **World Block Verification**: When interacting with container blocks, the block state is re-verified upon deferred execution to ensure the block wasn't destroyed by explosions or mining within the tick window.

---

### 2. Money Hoe (`money_hoe`)
A farming tool tailored for automated harvesting and selling:
- **Right-Click Air**: Toggles auto-sell mode ON or OFF.
- **Harvesting Mature Crops**: Breaking mature crops (Wheat, Carrots, Potatoes, Beetroots, Nether Wart, Cocoa Beans):
  - Automatically replants the crop to age 0 (deducting 1 seed/crop from the harvest).
  - **Auto-Sell ON**: Sells the crop drops immediately to the shop, deposits the money to the player's account, and drops any unsellable surplus on the ground.
  - **Auto-Sell OFF**: Places the harvested crops directly into the player's inventory without selling (spilling overflow onto the ground).

---

### 3. Sorting Staff (`sorting_staff`)
A container organization wand:
- **Right-Click Container Block**: Instantly sorts the target container. Items of identical type, metadata, and custom data are stacked together to their maximum capacity, and all items are neatly sorted by material.

---

### 4. Bazaar Staff (`bazaar_staff`)
A specialized inventory management wand integrated with [BasicBazaar](https://github.com/UsainSrht/BasicBazaar) (soft dependency):
- **Bazaar Listing Mode (Default)**: Restocks the player's active bazaar listings with matching items from containers or inventory slots.
- **Order Mode**: Fulfills/delivers matching items to active buy orders on the bazaar (highest paying orders first), depositing earnings directly to the player.
- **Air Right-Click**: Toggles between Bazaar Listing Restock Mode and Order Delivery Mode.
- **Air Shift-Right-Click**: Toggles Recursive Mode (inspecting nested containers like Shulker Boxes).
- **World Container Interactions**: Right-clicking a container block processes all contained items (and nested containers if recursive) into the player's listings or active buy orders.
- **In-Inventory Cursor Actions**: Fully mirrors the Money Staff's bidirectional cursor mechanics (Left-Click slot/cursor, Right-Click matching in inventory).
- **Graceful Soft-Dependency**: If BasicBazaar is not installed or enabled, the feature is cleanly ignored without runtime errors.
- **Configurable Messaging**: `use-native-bazaar-messages: true` sends native BasicBazaar transaction notifications.

---

## 📜 Commands & Permissions

| Command | Aliases | Description | Permission | Default |
| :--- | :--- | :--- | :--- | :--- |
| `/shop` | `/bs`, `/basicshop` | Opens the main shop category menu | `basicshop.use` | Everyone |
| `/shop help` | | Displays the help menu | `basicshop.help` | Everyone |
| `/shop top [chat\|gui]` | | Displays top profitable selling items | `basicshop.top` | Everyone |
| `/quicksell` | `/shop quicksell` | Opens the QuickSell inventory GUI | `basicshop.quicksell` | Everyone |
| `/quicksell hand` | `/shop quicksell hand` | Sells the item currently held in hand | `basicshop.quicksell.hand` | Everyone |
| `/quicksell inventory`| `/shop quicksell inventory` | Sells all sellable items in inventory | `basicshop.quicksell.inventory` | Everyone |
| `/shop give <target> <tool> [amt]` | | Gives a shop tool to a player | `basicshop.admin.give` | OP |
| `/shop admin reload` | `/shop reload` | Reloads all configuration and category files | `basicshop.admin.reload` | OP |
| `/shop admin analytics <web\|ingame> [days]` | | Generates in-game summary or web dashboard link | `basicshop.admin.analytics` | OP |
| `/shop admin logs <today\|yesterday\|date> [player]` | | Views transaction history for a date or player | `basicshop.admin.logs` | OP |

### Additional Permissions
- `basicshop.buy`: Allows buying items from the shop (default: `true`).
- `basicshop.sell`: Allows selling items to the shop (default: `true`).
- `basicshop.tools.staff`: Allows using the Money Staff (default: `true`).
- `basicshop.tools.hoe`: Allows using the Money Hoe (default: `true`).
- `basicshop.tools.sorting_staff`: Allows using the Sorting Staff (default: `true`).
- `basicshop.tools.bazaar_staff`: Allows using the Bazaar Staff (default: `true`).
- `basicshop.admin`: Grants all admin permissions (default: `op`).

---

## ⚙️ Configuration Overview

| File | Purpose |
| :--- | :--- |
| [`config.yml`](file:///h:/IdeaProjects/BasicShop/src/main/resources/config.yml) | Root command aliases, tool specifications (materials, cooldowns, riding restrictions), global buy/sell toggles, item display templates, and analytics settings. |
| [`categories.yml`](file:///h:/IdeaProjects/BasicShop/src/main/resources/categories.yml) | Defines the main category selector GUI (rows, title, category slots, icons, and display lores). |
| `categories/*.yml` | Individual category catalogues (e.g. `miner.yml`, `farmer.yml`, `adventurer.yml`). Defines individual item prices, materials, custom model data, commands, or permissions. |
| [`quicksell.yml`](file:///h:/IdeaProjects/BasicShop/src/main/resources/quicksell.yml) | Layout, buttons, filler items, and format of the QuickSell GUI. |
| [`topsellers.yml`](file:///h:/IdeaProjects/BasicShop/src/main/resources/topsellers.yml) | Configuration for the Top Sellers GUI and ranking item display. |
| [`messages.yml`](file:///h:/IdeaProjects/BasicShop/src/main/resources/messages.yml) | All user-facing chat messages, action bar messages, sound effects, and MiniMessage templates. |

---

## 📈 Web Analytics Dashboard

BasicShop includes a plug-and-play web analytics dashboard powered by a lightweight Cloudflare Worker (located in the [`web/`](file:///h:/IdeaProjects/BasicShop/web/) directory).

- Run `/shop admin analytics web [days]` (default: 7 days).
- The server compiles transaction logs, sends a payload to the Cloudflare Worker, and generates an expiring, clickable URL in chat.
- Administrators can visually inspect sales volume, net economy delta, top buyers, top sellers, and revenue charts directly in their web browser.
- Reports automatically expire and are purged from KV storage according to `expiration-hours` in `config.yml`.

---

## 🧩 Placeholders

BasicShop supports both **PlaceholderAPI** and **MiniPlaceholders v3**:

| Placeholder | Description | Example |
| :--- | :--- | :--- |
| `%basicshop_top_<1-10>_item%` | Name of the top seller item at specified rank | `Diamond` |
| `%basicshop_top_<1-10>_profit%` | Total profit generated by that item | `$12,450.00` |
| `%basicshop_top_<1-10>_amount%` | Total count of items sold | `1,245` |
| `%basicshop_top_<1-10>_material%` | Bukkit material name of the item | `DIAMOND` |
| `%basicshop_top_<1-10>_category%`| Shop category ID the item belongs to | `miner` |

*(MiniPlaceholders tags use identical identifiers without percent symbols, e.g. `<basicshop_top_1_item>`)*.

---

## 💻 Developer API

BasicShop registers its API in the Bukkit service manager:

```java
RegisteredServiceProvider<ShopAPI> provider = Bukkit.getServicesManager().getRegistration(ShopAPI.class);
if (provider != null) {
    ShopAPI shopAPI = provider.getProvider();
    
    // Quick-sell player's held cursor stack
    QuickSellResult result = shopAPI.sellCursor(player, true);
    
    // Check item buy and sell prices
    Optional<ShopItem> diamond = shopAPI.getItemByMaterial(Material.DIAMOND);
    diamond.ifPresent(item -> {
        double buyPrice = item.getBuyPrice().orElse(-1);
        double sellPrice = item.getSellPrice().orElse(-1);
    });
}
```

### Event Lifecycle
- `ShopOpenEvent`: Fired when a player opens any shop GUI.
- `ShopPreTransactionEvent`: Cancellable. Fired before buying or selling an individual item stack.
- `ShopPostTransactionEvent`: Fired after a transaction succeeds, containing the recorded transaction details.
- `ShopPreBulkSellEvent`: Cancellable. Fired before a bulk sell operation begins (QuickSell, Money Staff, Auto-sell).
- `ShopBulkSellEvent`: Fired after bulk selling completes, containing total items sold and net payout.
- `MoneyStaffCursorSellEvent`: Cancellable. Fired when a player triggers a Money Staff cursor action in an inventory.
- `MoneyStaffUseEvent`: Cancellable. Fired when a Money Staff is used on a world container block.
- `MoneyHoeHarvestEvent`: Cancellable. Fired when a Money Hoe harvests a crop.
- `SortingStaffUseEvent`: Cancellable. Fired when a Sorting Staff sorts a container.
- `ShopReloadEvent`: Fired when the plugin configurations are reloaded.

---

## 📥 Requirements & Installation

1. **Server Platform**: Paper, Purpur, or Folia **1.21+**.
2. **Java Version**: **Java 21** or higher.
3. **Dependencies**:
   - [Vault](https://www.spigotmc.org/resources/vault.34315/) + any Vault-compatible economy plugin (e.g. EssentialsX, EconomyShopGUI).
   - *(Optional)* [PlaceholderAPI](https://wiki.placeholderapi.com/) or [MiniPlaceholders](https://miniplaceholders.nova54.dev/).
4. Drop `BasicShop.jar` into your server's `plugins/` directory and restart the server.
