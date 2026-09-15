/**
 * BasicShop Cloudflare Worker & Web Analytics Dashboard
 * 100% Free Tier: Cloudflare Workers + KV Storage
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization, Content-Encoding",
        },
      });
    }

    // 1. POST /api/upload — BasicShop plugin uploads compressed analytics
    if (request.method === "POST" && path === "/api/upload") {
      return handleUpload(request, env, url);
    }

    // 2. GET /api/data/:id — Dashboard fetches JSON payload
    if (request.method === "GET" && path.startsWith("/api/data/")) {
      const id = path.replace("/api/data/", "").trim();
      return handleGetData(id, env);
    }

    // 3. GET /view/:id or / — Serves interactive Web Dashboard SPA
    if (request.method === "GET") {
      const reportId = path.startsWith("/view/") ? path.replace("/view/", "").trim() : "";
      return new Response(renderDashboardHtml(reportId, url.origin), {
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "public, max-age=3600",
        },
      });
    }

    return new Response("Not Found", { status: 404 });
  },
};

/**
 * Handles uploading analytics reports from BasicShop plugin.
 */
async function handleUpload(request, env, url) {
  const jsonHeaders = {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
  };

  // Check Authorization token if configured (allows self-hosters to require a token)
  const authHeader = request.headers.get("Authorization") || "";
  const expectedToken = env.UPLOAD_TOKEN || "";
  if (expectedToken && expectedToken !== "CHANGE_ME") {
    const token = authHeader.replace(/^Bearer\s+/i, "").trim();
    if (token !== expectedToken) {
      return new Response(JSON.stringify({ error: "Unauthorized: Invalid upload token" }), {
        status: 401,
        headers: jsonHeaders,
      });
    }
  }

  // Check Content-Length to reject oversized uploads immediately (Max 2MB compressed)
  const contentLength = parseInt(request.headers.get("Content-Length") || "0", 10);
  if (contentLength > 2 * 1024 * 1024) {
    return new Response(JSON.stringify({ error: "Payload Too Large: Max compressed payload size is 2MB" }), {
      status: 413,
      headers: jsonHeaders,
    });
  }

  // Decompress body if GZIP encoded
  let jsonString;
  try {
    const contentEncoding = request.headers.get("Content-Encoding") || "";
    if (contentEncoding.includes("gzip")) {
      const ds = new DecompressionStream("gzip");
      const decompressedStream = request.body.pipeThrough(ds);
      jsonString = await new Response(decompressedStream).text();
    } else {
      jsonString = await request.text();
    }
  } catch (err) {
    return new Response(JSON.stringify({ error: "Failed to decompress body: " + err.message }), {
      status: 400,
      headers: jsonHeaders,
    });
  }

  // Uncompressed size limit check (Max 10MB) to prevent zip-bomb memory exhaustion
  if (jsonString.length > 10 * 1024 * 1024) {
    return new Response(JSON.stringify({ error: "Payload Too Large: Uncompressed payload exceeds 10MB" }), {
      status: 413,
      headers: jsonHeaders,
    });
  }

  // Parse and validate JSON
  let payload;
  try {
    payload = JSON.parse(jsonString);
  } catch (err) {
    return new Response(JSON.stringify({ error: "Invalid JSON payload" }), {
      status: 400,
      headers: jsonHeaders,
    });
  }

  // BasicShop Schema Validation: verify report signature to prevent generic abuse
  if (
    !payload ||
    typeof payload !== "object" ||
    !payload.version ||
    !payload.generatedAt ||
    (!Array.isArray(payload.transactions) && !Array.isArray(payload.topItems))
  ) {
    return new Response(JSON.stringify({ error: "Invalid payload: Not a recognized BasicShop analytics report" }), {
      status: 400,
      headers: jsonHeaders,
    });
  }

  // Generate short unique identifier (8 characters)
  const id = crypto.randomUUID().split("-")[0];

  // Enforce TTL: minimum 1 day, maximum 7 days (default 3 days)
  const rawExp = parseInt(payload.expirationDays, 10);
  const expirationDays = Math.min(7, Math.max(1, isNaN(rawExp) ? 3 : rawExp));
  const ttlSeconds = expirationDays * 86400;

  // Store in Cloudflare KV with TTL
  if (!env.ANALYTICS_KV) {
    return new Response(JSON.stringify({ error: "ANALYTICS_KV namespace binding missing in Cloudflare Worker" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }

  await env.ANALYTICS_KV.put(id, jsonString, {
    expirationTtl: ttlSeconds,
  });

  const viewUrl = `${url.origin}/view/${id}`;

  return new Response(
    JSON.stringify({
      id: id,
      url: viewUrl,
      expirationDays: expirationDays,
      expiresAt: new Date(Date.now() + ttlSeconds * 1000).toISOString(),
    }),
    {
      status: 200,
      headers: jsonHeaders,
    }
  );
}

/**
 * Returns JSON payload for a report ID.
 */
async function handleGetData(id, env) {
  if (!id || !env.ANALYTICS_KV) {
    return new Response(JSON.stringify({ error: "Invalid ID or KV not configured" }), {
      status: 400,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  const data = await env.ANALYTICS_KV.get(id);
  if (!data) {
    return new Response(JSON.stringify({ error: "Report not found or has expired" }), {
      status: 404,
      headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" },
    });
  }

  return new Response(data, {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "public, max-age=60",
    },
  });
}

/**
 * Renders the Single Page Application Dashboard HTML.
 */
function renderDashboardHtml(initialId, origin) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>BasicShop Analytics</title>
  <meta name="description" content="Modern, real-time Minecraft shop analytics powered by Cloudflare Free Tier" />
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@600;700;800&display=swap" rel="stylesheet">
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
  <style>
    :root {
      --bg-base: #0a0e17;
      --bg-surface: #111827;
      --bg-surface-elevated: #1e293b;
      --border-subtle: rgba(255, 255, 255, 0.08);
      --border-accent: rgba(99, 102, 241, 0.3);
      --primary: #6366f1;
      --primary-hover: #4f46e5;
      --accent-emerald: #10b981;
      --accent-amber: #f59e0b;
      --accent-rose: #f43f5e;
      --accent-violet: #8b5cf6;
      --text-main: #f8fafc;
      --text-muted: #94a3b8;
      --text-dim: #64748b;
      --shadow-glow: 0 0 30px rgba(99, 102, 241, 0.15);
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      background-color: var(--bg-base);
      color: var(--text-main);
      line-height: 1.5;
      min-height: 100vh;
      overflow-x: hidden;
    }

    /* Ambient Background Glow */
    .ambient-glow {
      position: fixed;
      top: -100px;
      left: 50%;
      transform: translateX(-50%);
      width: 800px;
      height: 400px;
      background: radial-gradient(ellipse at center, rgba(99, 102, 241, 0.12), transparent 70%);
      pointer-events: none;
      z-index: 0;
    }

    /* Navbar */
    header {
      position: sticky;
      top: 0;
      background: rgba(10, 14, 23, 0.85);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border-subtle);
      padding: 0.85rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      z-index: 50;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      text-decoration: none;
      color: inherit;
    }
    .brand-logo {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: linear-gradient(135deg, var(--primary), var(--accent-violet));
      display: flex;
      align-items: center;
      justify-content: center;
      font-family: 'Outfit', sans-serif;
      font-weight: 800;
      font-size: 1.1rem;
      color: #fff;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
    }
    .brand-title {
      font-family: 'Outfit', sans-serif;
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .brand-tag {
      font-size: 0.7rem;
      font-weight: 600;
      padding: 0.15rem 0.5rem;
      background: rgba(99, 102, 241, 0.15);
      color: #818cf8;
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: 9999px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.45rem 0.9rem;
      font-size: 0.85rem;
      font-weight: 500;
      border-radius: 8px;
      cursor: pointer;
      border: 1px solid var(--border-subtle);
      background: var(--bg-surface-elevated);
      color: var(--text-main);
      transition: all 0.2s ease;
    }
    .btn:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
    }
    .btn-primary {
      background: var(--primary);
      border-color: var(--primary);
      color: #fff;
    }
    .btn-primary:hover {
      background: var(--primary-hover);
    }

    /* Container */
    main {
      max-width: 1300px;
      margin: 0 auto;
      padding: 2rem 1.5rem 4rem;
      position: relative;
      z-index: 10;
    }

    /* Meta Banner */
    .meta-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      padding: 1.25rem 1.75rem;
      margin-bottom: 2rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 1rem;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
    }
    .meta-info h1 {
      font-family: 'Outfit', sans-serif;
      font-size: 1.45rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .meta-subtitle {
      color: var(--text-muted);
      font-size: 0.85rem;
      margin-top: 0.2rem;
    }
    .badge-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.3rem 0.75rem;
      border-radius: 9999px;
      font-size: 0.78rem;
      font-weight: 600;
      background: rgba(16, 185, 129, 0.12);
      color: var(--accent-emerald);
      border: 1px solid rgba(16, 185, 129, 0.25);
    }
    .badge-pill.warning {
      background: rgba(245, 158, 11, 0.12);
      color: var(--accent-amber);
      border-color: rgba(245, 158, 11, 0.25);
    }

    /* KPI Grid */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
      margin-bottom: 2rem;
    }
    .kpi-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: 12px;
      padding: 1.25rem;
      transition: transform 0.2s ease, border-color 0.2s ease;
    }
    .kpi-card:hover {
      transform: translateY(-2px);
      border-color: var(--border-accent);
    }
    .kpi-title {
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .kpi-value {
      font-family: 'Outfit', sans-serif;
      font-size: 1.7rem;
      font-weight: 700;
      margin-top: 0.35rem;
      color: #fff;
    }
    .kpi-value.green { color: var(--accent-emerald); }
    .kpi-value.gold  { color: var(--accent-amber); }
    .kpi-value.purple { color: var(--accent-violet); }
    .kpi-subtext {
      font-size: 0.75rem;
      color: var(--text-dim);
      margin-top: 0.25rem;
    }

    /* Charts Grid */
    .charts-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 1.5rem;
      margin-bottom: 2rem;
    }
    @media (max-width: 960px) {
      .charts-grid { grid-template-columns: 1fr; }
    }
    .chart-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
    }
    .chart-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }
    .chart-title {
      font-family: 'Outfit', sans-serif;
      font-size: 1.05rem;
      font-weight: 600;
    }
    .chart-container {
      position: relative;
      flex: 1;
      min-height: 260px;
    }

    /* Secondary charts */
    .secondary-charts {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.5rem;
      margin-bottom: 2rem;
    }
    @media (max-width: 960px) {
      .secondary-charts { grid-template-columns: 1fr; }
    }

    /* Tables */
    .section-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: 14px;
      padding: 1.5rem;
      margin-bottom: 2rem;
    }
    .tabs-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--border-subtle);
      padding-bottom: 0.75rem;
      margin-bottom: 1rem;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .nav-tabs {
      display: flex;
      gap: 0.5rem;
    }
    .tab-btn {
      padding: 0.45rem 1rem;
      border-radius: 8px;
      background: transparent;
      border: 1px solid transparent;
      color: var(--text-muted);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
    }
    .tab-btn.active {
      background: var(--bg-surface-elevated);
      color: #fff;
      border-color: var(--border-accent);
    }
    .search-box {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-subtle);
      border-radius: 8px;
      padding: 0.4rem 0.8rem;
      color: #fff;
      font-size: 0.85rem;
      outline: none;
      width: 220px;
    }
    .search-box:focus {
      border-color: var(--primary);
    }

    .table-responsive {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.87rem;
    }
    th {
      padding: 0.75rem 1rem;
      color: var(--text-muted);
      font-weight: 600;
      border-bottom: 1px solid var(--border-subtle);
      text-transform: uppercase;
      font-size: 0.72rem;
      letter-spacing: 0.05em;
    }
    td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
      vertical-align: middle;
    }
    tr:hover td {
      background: rgba(255, 255, 255, 0.02);
    }
    .rank-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 0.75rem;
      background: var(--bg-surface-elevated);
      color: var(--text-muted);
    }
    .rank-1 { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.4); }
    .rank-2 { background: rgba(148, 163, 184, 0.2); color: #e2e8f0; border: 1px solid rgba(148, 163, 184, 0.4); }
    .rank-3 { background: rgba(180, 83, 9, 0.2); color: #d97706; border: 1px solid rgba(180, 83, 9, 0.4); }

    .player-cell {
      display: flex;
      align-items: center;
      gap: 0.7rem;
    }
    .player-avatar {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      image-rendering: pixelated;
    }
    .tag-buy {
      color: var(--accent-amber);
      background: rgba(245, 158, 11, 0.12);
      padding: 0.15rem 0.45rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .tag-sell {
      color: var(--accent-emerald);
      background: rgba(16, 185, 129, 0.12);
      padding: 0.15rem 0.45rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    /* Toast Notification */
    #toast {
      position: fixed;
      bottom: 24px;
      right: 24px;
      background: var(--primary);
      color: #fff;
      padding: 0.6rem 1.2rem;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 500;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
      opacity: 0;
      transform: translateY(10px);
      transition: all 0.3s ease;
      pointer-events: none;
      z-index: 100;
    }
    #toast.show {
      opacity: 1;
      transform: translateY(0);
    }

    /* Loading Spinner */
    #loadingOverlay {
      position: fixed;
      inset: 0;
      background: var(--bg-base);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      z-index: 999;
      gap: 1rem;
    }
    .spinner {
      width: 44px;
      height: 44px;
      border: 3px solid rgba(99, 102, 241, 0.2);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  </style>
</head>
<body>
  <div class="ambient-glow"></div>

  <!-- Loading State -->
  <div id="loadingOverlay">
    <div class="spinner"></div>
    <p style="color: var(--text-muted); font-size: 0.9rem;">Loading Shop Analytics...</p>
  </div>

  <!-- Toast -->
  <div id="toast">Link copied to clipboard!</div>

  <!-- Navbar -->
  <header>
    <a href="#" class="brand">
      <div class="brand-logo">B</div>
      <div>
        <span class="brand-title">BasicShop</span>
        <span class="brand-tag">Analytics</span>
      </div>
    </a>
    <div class="header-actions">
      <button class="btn" id="btnExportJson">Export JSON</button>
      <button class="btn btn-primary" id="btnShare">Share Link</button>
    </div>
  </header>

  <main>
    <!-- Meta Card -->
    <div class="meta-card">
      <div class="meta-info">
        <h1 id="serverTitle">Minecraft Server</h1>
        <p class="meta-subtitle" id="dateRangeText">Analyzing past 7 days</p>
      </div>
      <div style="display: flex; gap: 0.5rem; align-items: center;">
        <div class="badge-pill" id="expiryBadge">Active Report</div>
      </div>
    </div>

    <!-- KPI Cards -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-title">Gross Turnover</div>
        <div class="kpi-value green" id="kpiGrossTurnover">$0.00</div>
        <div class="kpi-subtext" id="kpiTotalUnits">0 units traded</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-title">Player Sales (Payouts)</div>
        <div class="kpi-value" id="kpiTotalSold">$0.00</div>
        <div class="kpi-subtext" id="kpiSoldUnits">0 items sold to shop</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-title">Player Purchases</div>
        <div class="kpi-value gold" id="kpiTotalBought">$0.00</div>
        <div class="kpi-subtext" id="kpiBoughtUnits">0 items bought from shop</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-title">Net Economy Delta</div>
        <div class="kpi-value" id="kpiNetDelta">$0.00</div>
        <div class="kpi-subtext" id="kpiNetSubtext">Purchases minus payouts</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-title">Transactions & Traders</div>
        <div class="kpi-value purple" id="kpiTransactions">0</div>
        <div class="kpi-subtext" id="kpiUniqueTraders">0 unique players</div>
      </div>
    </div>

    <!-- Charts -->
    <div class="charts-grid">
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-title">Economy Flow Timeline</div>
          <span class="badge-pill" style="font-size: 0.72rem;">Daily Volume ($)</span>
        </div>
        <div class="chart-container">
          <canvas id="timelineChart"></canvas>
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-title">Category Distribution</div>
        </div>
        <div class="chart-container">
          <canvas id="categoryChart"></canvas>
        </div>
      </div>
    </div>

    <div class="secondary-charts">
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-title">Top 10 Most Profited Items</div>
        </div>
        <div class="chart-container">
          <canvas id="topItemsChart"></canvas>
        </div>
      </div>
      <div class="chart-card">
        <div class="chart-header">
          <div class="chart-title">Hourly Activity Distribution</div>
        </div>
        <div class="chart-container">
          <canvas id="hourlyChart"></canvas>
        </div>
      </div>
    </div>

    <!-- Data Tables -->
    <div class="section-card">
      <div class="tabs-header">
        <div class="nav-tabs">
          <button class="tab-btn active" data-tab="tabItems">Top Items</button>
          <button class="tab-btn" data-tab="tabPlayers">Top Traders</button>
          <button class="tab-btn" data-tab="tabLogs">Recent Transactions</button>
        </div>
        <input type="text" class="search-box" id="tableSearch" placeholder="Search table..." />
      </div>

      <!-- Tab: Top Items -->
      <div id="tabItems" class="tab-content">
        <div class="table-responsive">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Item</th>
                <th>Category</th>
                <th>Units Sold</th>
                <th>Payout Total</th>
                <th>Units Bought</th>
                <th>Purchase Cost</th>
                <th>Total Volume</th>
              </tr>
            </thead>
            <tbody id="itemsTableBody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab: Top Players -->
      <div id="tabPlayers" class="tab-content" style="display: none;">
        <div class="table-responsive">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Player</th>
                <th>Transactions</th>
                <th>Total Spent</th>
                <th>Total Earned</th>
                <th>Net Balance Delta</th>
              </tr>
            </thead>
            <tbody id="playersTableBody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab: Recent Logs -->
      <div id="tabLogs" class="tab-content" style="display: none;">
        <div class="table-responsive">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Player</th>
                <th>Item</th>
                <th>Amount</th>
                <th>Total Price</th>
              </tr>
            </thead>
            <tbody id="logsTableBody"></tbody>
          </table>
        </div>
      </div>
    </div>
  </main>

  <script>
    let reportData = null;
    const initialReportId = "${initialId}";

    function formatMoney(n) {
      return "$" + Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    async function loadData() {
      const id = initialReportId || new URLSearchParams(window.location.search).get("id");
      if (!id) {
        document.getElementById("loadingOverlay").innerHTML =
          '<h3 style="color:#f43f5e;">No Report ID Specified</h3><p style="color:#94a3b8;margin-top:0.5rem;">Upload analytics from Minecraft using: <code style="color:#818cf8;">/shop admin analytics web</code></p>';
        return;
      }

      try {
        const resp = await fetch("/api/data/" + id);
        if (!resp.ok) throw new Error("Report not found or expired");
        reportData = await resp.json();
        renderDashboard(reportData);
        document.getElementById("loadingOverlay").style.display = "none";
      } catch (err) {
        document.getElementById("loadingOverlay").innerHTML =
          '<h3 style="color:#f43f5e;">Report Unavailable</h3><p style="color:#94a3b8;margin-top:0.5rem;">' + err.message + '</p>';
      }
    }

    function renderDashboard(data) {
      // Header
      document.getElementById("serverTitle").textContent = data.serverName || "Minecraft Server";
      document.getElementById("dateRangeText").textContent =
        "Analyzed " + (data.daysAnalyzed || 7) + " days (" + (data.startDate || "") + " to " + (data.endDate || "") + ")";

      if (data.expirationDays) {
        document.getElementById("expiryBadge").textContent = "Expires in " + data.expirationDays + " days";
      }

      // KPIs
      const sum = data.summary || {};
      const gross = (sum.totalBoughtMoney || 0) + (sum.totalSoldMoney || 0);
      document.getElementById("kpiGrossTurnover").textContent = formatMoney(gross);
      document.getElementById("kpiTotalUnits").textContent = Number(sum.totalUnitsTraded || 0).toLocaleString() + " items traded";

      document.getElementById("kpiTotalSold").textContent = formatMoney(sum.totalSoldMoney || 0);
      document.getElementById("kpiSoldUnits").textContent = Number(sum.totalUnitsSold || 0).toLocaleString() + " units sold to shop";

      document.getElementById("kpiTotalBought").textContent = formatMoney(sum.totalBoughtMoney || 0);
      document.getElementById("kpiBoughtUnits").textContent = Number(sum.totalUnitsBought || 0).toLocaleString() + " units bought by players";

      const netDelta = sum.netCashDelta || 0;
      const netElem = document.getElementById("kpiNetDelta");
      netElem.textContent = (netDelta >= 0 ? "+" : "") + formatMoney(netDelta);
      netElem.style.color = netDelta >= 0 ? "var(--accent-emerald)" : "var(--accent-rose)";

      document.getElementById("kpiTransactions").textContent = Number(sum.totalTransactions || 0).toLocaleString();
      document.getElementById("kpiUniqueTraders").textContent = (sum.uniqueTraders || 0) + " unique players";

      // Render Charts
      renderTimelineChart(data.timeline || []);
      renderCategoryChart(data.items || []);
      renderTopItemsChart(data.items || []);
      renderHourlyChart(data.hourlyDistribution || []);

      // Render Tables
      renderItemsTable(data.items || []);
      renderPlayersTable(data.players || []);
      renderLogsTable(data.recentTransactions || []);
    }

    function renderTimelineChart(timeline) {
      const ctx = document.getElementById("timelineChart").getContext("2d");
      const labels = timeline.map(t => t.date.slice(5));
      const buyData = timeline.map(t => t.buyMoney);
      const sellData = timeline.map(t => t.sellMoney);

      new Chart(ctx, {
        type: 'line',
        data: {
          labels: labels,
          datasets: [
            {
              label: 'Player Purchases ($)',
              data: buyData,
              borderColor: '#f59e0b',
              backgroundColor: 'rgba(245, 158, 11, 0.1)',
              fill: true,
              tension: 0.35,
            },
            {
              label: 'Player Sell Payouts ($)',
              data: sellData,
              borderColor: '#10b981',
              backgroundColor: 'rgba(16, 185, 129, 0.1)',
              fill: true,
              tension: 0.35,
            }
          ]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { labels: { color: '#94a3b8' } }
          },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b' } },
            y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b', callback: v => '$' + v } }
          }
        }
      });
    }

    function renderCategoryChart(items) {
      const catMap = {};
      items.forEach(it => {
        const cat = it.categoryId || "general";
        catMap[cat] = (catMap[cat] || 0) + (it.soldMoney || 0) + (it.boughtMoney || 0);
      });

      const labels = Object.keys(catMap);
      const values = Object.values(catMap);
      const ctx = document.getElementById("categoryChart").getContext("2d");

      new Chart(ctx, {
        type: 'doughnut',
        data: {
          labels: labels,
          datasets: [{
            data: values,
            backgroundColor: ['#6366f1', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6', '#06b6d4', '#14b8a6'],
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 11 } } }
          }
        }
      });
    }

    function renderTopItemsChart(items) {
      const sorted = [...items].sort((a,b) => (b.soldMoney || 0) - (a.soldMoney || 0)).slice(0, 10);
      const labels = sorted.map(it => it.itemId.split(':').pop());
      const data = sorted.map(it => it.soldMoney || 0);
      const ctx = document.getElementById("topItemsChart").getContext("2d");

      new Chart(ctx, {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [{
            label: 'Total Profit Sold ($)',
            data: data,
            backgroundColor: '#10b981',
            borderRadius: 6
          }]
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b', callback: v => '$' + v } },
            y: { grid: { display: false }, ticks: { color: '#f8fafc' } }
          }
        }
      });
    }

    function renderHourlyChart(hourly) {
      const labels = Array.from({ length: 24 }, (_, i) => String(i).padStart(2, '0') + ':00');
      const ctx = document.getElementById("hourlyChart").getContext("2d");

      new Chart(ctx, {
        type: 'bar',
        data: {
          labels: labels,
          datasets: [{
            label: 'Transactions',
            data: hourly,
            backgroundColor: '#6366f1',
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { grid: { display: false }, ticks: { color: '#64748b' } },
            y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b' } }
          }
        }
      });
    }

    function renderItemsTable(items) {
      const tbody = document.getElementById("itemsTableBody");
      tbody.innerHTML = items.map((it, idx) => {
        const rank = idx + 1;
        const rankClass = rank <= 3 ? 'rank-' + rank : '';
        const total = (it.soldMoney || 0) + (it.boughtMoney || 0);
        return '<tr>' +
          '<td><span class="rank-badge ' + rankClass + '">' + rank + '</span></td>' +
          '<td style="font-weight:600;">' + it.itemId + '</td>' +
          '<td><span style="color:var(--text-dim);">' + (it.categoryId || "-") + '</span></td>' +
          '<td>' + Number(it.soldUnits || 0).toLocaleString() + '</td>' +
          '<td style="color:var(--accent-emerald); font-weight:600;">' + formatMoney(it.soldMoney) + '</td>' +
          '<td>' + Number(it.boughtUnits || 0).toLocaleString() + '</td>' +
          '<td style="color:var(--accent-amber);">' + formatMoney(it.boughtMoney) + '</td>' +
          '<td style="font-weight:600;">' + formatMoney(total) + '</td>' +
          '</tr>';
      }).join('');
    }

    function renderPlayersTable(players) {
      const tbody = document.getElementById("playersTableBody");
      tbody.innerHTML = players.map((p, idx) => {
        const rank = idx + 1;
        const rankClass = rank <= 3 ? 'rank-' + rank : '';
        const net = (p.earned || 0) - (p.spent || 0);
        const netColor = net >= 0 ? 'var(--accent-emerald)' : 'var(--accent-rose)';
        const avatarUrl = "https://crafthead.net/avatar/" + (p.uuid || p.name);

        return '<tr>' +
          '<td><span class="rank-badge ' + rankClass + '">' + rank + '</span></td>' +
          '<td><div class="player-cell"><img class="player-avatar" src="' + avatarUrl + '" alt="" /><span>' + p.name + '</span></div></td>' +
          '<td>' + Number(p.transactions || 0).toLocaleString() + '</td>' +
          '<td style="color:var(--accent-amber);">' + formatMoney(p.spent) + '</td>' +
          '<td style="color:var(--accent-emerald);">' + formatMoney(p.earned) + '</td>' +
          '<td style="font-weight:600; color:' + netColor + ';">' + (net >= 0 ? '+' : '') + formatMoney(net) + '</td>' +
          '</tr>';
      }).join('');
    }

    function renderLogsTable(logs) {
      const tbody = document.getElementById("logsTableBody");
      tbody.innerHTML = logs.map(l => {
        const time = new Date(l.timestamp).toLocaleTimeString();
        const tag = l.type === 'BUY' ? '<span class="tag-buy">BUY</span>' : '<span class="tag-sell">SELL</span>';
        return '<tr>' +
          '<td style="color:var(--text-dim);">' + time + '</td>' +
          '<td>' + tag + '</td>' +
          '<td>' + l.playerName + '</td>' +
          '<td style="font-weight:500;">' + l.itemId + '</td>' +
          '<td>' + l.amount + '</td>' +
          '<td style="font-weight:600;">' + formatMoney(l.totalPrice) + '</td>' +
          '</tr>';
      }).join('');
    }

    // Tabs switching
    document.querySelectorAll(".tab-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        document.querySelectorAll(".tab-btn").forEach(b => b.classList.remove("active"));
        document.querySelectorAll(".tab-content").forEach(c => c.style.display = "none");
        btn.classList.add("active");
        document.getElementById(btn.dataset.tab).style.display = "block";
      });
    });

    // Table Search filter
    document.getElementById("tableSearch").addEventListener("input", (e) => {
      const q = e.target.value.toLowerCase();
      document.querySelectorAll(".tab-content tbody tr").forEach(row => {
        row.style.display = row.textContent.toLowerCase().includes(q) ? "" : "none";
      });
    });

    // Share / Copy Link
    document.getElementById("btnShare").addEventListener("click", () => {
      navigator.clipboard.writeText(window.location.href).then(() => {
        const toast = document.getElementById("toast");
        toast.classList.add("show");
        setTimeout(() => toast.classList.remove("show"), 2500);
      });
    });

    // Export JSON
    document.getElementById("btnExportJson").addEventListener("click", () => {
      if (!reportData) return;
      const blob = new Blob([JSON.stringify(reportData, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "basicshop-analytics.json";
      a.click();
    });

    window.addEventListener("DOMContentLoaded", loadData);
  </script>
</body>
</html>`;
}
