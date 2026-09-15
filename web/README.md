# BasicShop Web Analytics Service

A serverless analytics service and interactive web dashboard for **BasicShop**, running entirely on **Cloudflare's Free Tier** (Workers + KV Storage).

Like **Spark** (`spark.lucko.me`) or **mclo.gs**, BasicShop uploads compressed shop transactions directly to a centralized web service, returning an instant, temporary, interactive dashboard link in Minecraft chat. Server owners don't need to configure Cloudflare or touch any code — it works out of the box!

---

## Features

- **Zero-Config for Server Owners**: Works immediately out-of-the-box upon installing BasicShop.
- **100% Free Tier**: Runs within Cloudflare's free limits (100,000 requests/day, 1,000 KV writes/day).
- **Auto-Expiring Reports**: Cloudflare KV `expirationTtl` automatically purges reports after 1 hour (default; 1–168 hours configurable). Zero maintenance required.
- **Built-in Abuse Safeguards**:
  - Max 2MB compressed / 10MB uncompressed payload size cap (prevents memory exhaustion & zip bombs).
  - Schema signature validation (rejects non-BasicShop data).
  - Hard TTL limits (capped between 1 and 168 hours / 7 days).
- **Rich Dark-Mode Dashboard**:
  - **KPI Cards**: Gross Turnover, Buy Revenue, Sell Payouts, Net Economy Delta, Units Traded, Unique Traders.
  - **Interactive Charts (Chart.js)**: Economy Flow Timeline, Category Market Share, Top 10 Profited Items, Hourly Activity.
  - **Official Minecraft Item Textures**: High-res pixel-art textures powered by global CDNs (mcasset.cloud & jsDelivr) with multi-level fallbacks (item → block → jsDelivr → SVG).
  - **Player Head Avatars**: Fast skin rendering via CraftHead & mc-heads.
  - **Lazy Loading**: All images use `loading="lazy"` and `decoding="async"` for lightning-fast page loads.
  - **Interactive Filters**: Filter top items by player, or filter top traders by item.
  - **Dedicated Drill-Down Analytics**: Click any item or player to open deep-dive modals complete with dedicated timelines, traded partners, category charts, and filtered logs.
  - **One-Click Share & Export**: Direct report links and JSON export.

---

## 🛠️ Maintainer Setup Guide (Deploy Once)

As the plugin author/maintainer, you only need to deploy this worker **once** to your Cloudflare account. All BasicShop servers worldwide will then use it automatically.

### Step 1: Install Dependencies
```bash
cd web
npm install
```

### Step 2: Login to Cloudflare
```bash
npx wrangler login
```

### Step 3: Create the KV Storage
```bash
npx wrangler kv namespace create ANALYTICS_KV
```
Copy the `id` from the terminal output and paste it into `web/wrangler.toml`:
```toml
[[kv_namespaces]]
binding = "ANALYTICS_KV"
id = "your_actual_kv_id_here"
```

### Step 4: Deploy the Worker
```bash
npx wrangler deploy
```
Wrangler will output your public worker URL, for example:
`https://basicshop-analytics.<your-subdomain>.workers.dev`

### Step 5: Update Default URL in BasicShop
Set this URL as the default in `src/main/resources/config.yml`:
```yaml
analytics:
  web:
    upload-url: "https://basicshop-analytics.<your-subdomain>.workers.dev"
```
That's it! Every user who installs BasicShop can now use `/shop admin analytics web` with zero setup.

---

## API Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/upload` | Receives GZIP-compressed BasicShop JSON payload and returns `{ id, url, expirationHours, expirationDays, expiresAt }`. |
| `GET` | `/api/data/:id` | Returns the stored JSON analytics payload for report `:id`. |
| `GET` | `/view/:id` | Serves the interactive dark-mode dashboard SPA. |

---

## Self-Hosting (Optional for Private Networks)

Server networks that prefer private internal analytics can deploy their own instance of this worker:
1. Follow the deployment steps above on their own Cloudflare account.
2. (Optional) Set a secret password: `npx wrangler secret put UPLOAD_TOKEN`.
3. In their server's `config.yml`, set their custom `upload-url` and `secret-token`.
