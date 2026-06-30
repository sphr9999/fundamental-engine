import re
import sys

def main():
    path = "src/main/resources/static/index.html"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Add Chart.js to head
    content = content.replace(
        '<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet" />',
        '<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet" />\n  <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>'
    )

    # 2. Update CSS
    css_old = """    :root {
      --bg-primary:   #0d1117;
      --bg-card:      #161b22;
      --bg-hover:     #1c2230;
      --bg-input:     #1a2030;
      --border:       #2a3244;
      --text-primary: #e6edf3;
      --text-muted:   #7d8fa5;
      --accent:       #00d4ff;
      --accent-dim:   rgba(0,212,255,0.12);
      --gold:         #f5c518;
      --green:        #3fb950;
      --yellow:       #d29922;
      --orange:       #f0883e;
      --red:          #f85149;
      --teal:         #2dd4bf;
      --purple:       #a78bfa;
      --radius-sm:    6px;
      --radius-md:    10px;
      --radius-lg:    16px;
      --shadow:       0 4px 24px rgba(0,0,0,0.4);
      --transition:   0.2s ease;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', sans-serif; background: var(--bg-primary); color: var(--text-primary); min-height: 100vh; display: flex; }
    a { color: var(--accent); text-decoration: none; }

    /* ───── SIDEBAR ───── */
    #sidebar {
      width: 220px; min-height: 100vh; background: var(--bg-card);
      border-right: 1px solid var(--border);
      display: flex; flex-direction: column; padding: 0; flex-shrink: 0;
      position: sticky; top: 0; height: 100vh; overflow-y: auto;
    }
    .sidebar-logo {
      padding: 20px 20px 16px;
      font-size: 18px; font-weight: 800; color: var(--accent);
      border-bottom: 1px solid var(--border);
      display: flex; align-items: center; gap: 10px;
    }
    .sidebar-logo span { color: var(--text-primary); }
    .sidebar-logo .logo-icon { font-size: 22px; }
    .nav-section { padding: 12px 12px 4px; font-size: 10px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; }
    .nav-item {
      display: flex; align-items: center; gap: 10px;
      padding: 10px 16px; margin: 2px 8px; border-radius: var(--radius-sm);
      cursor: pointer; font-size: 13.5px; font-weight: 500; color: var(--text-muted);
      transition: var(--transition); user-select: none;
    }
    .nav-item:hover { background: var(--bg-hover); color: var(--text-primary); }
    .nav-item.active { background: var(--accent-dim); color: var(--accent); }
    .nav-item .icon { font-size: 16px; width: 20px; text-align: center; }
    .sidebar-bottom { margin-top: auto; padding: 16px; border-top: 1px solid var(--border); font-size: 11px; color: var(--text-muted); }

    /* ───── MAIN ───── */
    #main { flex: 1; display: flex; flex-direction: column; min-height: 100vh; overflow: hidden; }
    #topbar {
      background: var(--bg-card); border-bottom: 1px solid var(--border);
      padding: 12px 24px; display: flex; align-items: center; gap: 16px;
      position: sticky; top: 0; z-index: 100;
    }
    .topbar-title { font-weight: 700; font-size: 15px; flex: 1; }
    .search-wrap { position: relative; }
    .search-wrap input {
      background: var(--bg-input); border: 1px solid var(--border); color: var(--text-primary);
      padding: 8px 14px 8px 36px; border-radius: var(--radius-sm); font-size: 13px; width: 240px;
      outline: none; transition: var(--transition); font-family: inherit;
    }
    .search-wrap input:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-dim); }
    .search-wrap .search-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 14px; }
    #content { flex: 1; padding: 24px; overflow-y: auto; }"""

    css_new = """    :root {
      --bg-primary:   #090d13;
      --bg-card:      #12161f;
      --bg-hover:     #1a202c;
      --bg-input:     #171d27;
      --border:       #1e2532;
      --text-primary: #e6edf3;
      --text-muted:   #8893a0;
      --accent:       #4c9aff;
      --accent-dim:   rgba(76,154,255,0.12);
      --gold:         #f5c518;
      --green:        #4caf50;
      --yellow:       #ffb74d;
      --orange:       #ff9800;
      --red:          #f44336;
      --teal:         #00bcd4;
      --purple:       #ba68c8;
      --radius-sm:    6px;
      --radius-md:    10px;
      --radius-lg:    16px;
      --shadow:       0 4px 24px rgba(0,0,0,0.4);
      --transition:   0.2s ease;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Inter', sans-serif; background: var(--bg-primary); color: var(--text-primary); min-height: 100vh; display: flex; flex-direction: column; }
    a { color: var(--accent); text-decoration: none; }

    /* ───── TOPNAV ───── */
    #topnav {
      background: var(--bg-card); border-bottom: 1px solid var(--border);
      padding: 0 24px; display: flex; align-items: center; gap: 24px;
      position: sticky; top: 0; z-index: 100; height: 64px;
    }
    .topnav-logo {
      font-size: 20px; font-weight: 800; color: var(--accent);
      display: flex; align-items: center; gap: 8px; cursor: pointer;
    }
    .topnav-logo span { color: var(--text-primary); }
    
    .nav-links { display: flex; align-items: center; height: 100%; gap: 12px; }
    .nav-item {
      display: flex; align-items: center; gap: 8px; height: 100%;
      padding: 0 12px; cursor: pointer; font-size: 14px; font-weight: 500; color: var(--text-muted);
      transition: var(--transition); border-bottom: 2px solid transparent;
    }
    .nav-item:hover { color: var(--text-primary); }
    .nav-item.active { color: var(--accent); border-bottom-color: var(--accent); }
    
    .search-wrap { position: relative; margin-left: auto; display: flex; align-items: center; }
    .search-wrap input {
      background: var(--bg-input); border: 1px solid var(--border); color: var(--text-primary);
      padding: 9px 14px 9px 38px; border-radius: 20px; font-size: 13px; width: 300px;
      outline: none; transition: var(--transition); font-family: inherit;
    }
    .search-wrap input:focus { border-color: var(--accent); box-shadow: 0 0 0 2px var(--accent-dim); width: 340px; }
    .search-wrap .search-icon { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: var(--text-muted); font-size: 14px; }

    /* ───── MAIN ───── */
    #main { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
    #content { flex: 1; padding: 32px 48px; max-width: 1400px; margin: 0 auto; width: 100%; overflow-y: auto; }
    
    .chart-container { position: relative; height: 300px; width: 100%; margin-top: 16px; }
    .turtle-section-title { font-size: 20px; font-weight: 700; color: var(--accent); border-left: 3px solid var(--accent); padding-left: 12px; margin: 32px 0 24px; line-height: 1; }
    .macro-banner { background: var(--bg-input); padding: 12px 16px; border-radius: var(--radius-sm); border: 1px solid var(--border); font-size: 14px; margin-bottom: 24px; color: var(--text-muted); }
    .macro-banner strong { color: var(--text-primary); }"""

    content = content.replace(css_old, css_new)

    # 3. Update HTML Body (Sidebar -> Topnav)
    html_old = """<!-- SIDEBAR -->
<nav id="sidebar">
  <div class="sidebar-logo">
    <span class="logo-icon">📊</span>
    <span>FA <b style="color:var(--accent)">Engine</b></span>
  </div>
  <div style="padding: 12px;">
    <div class="search-wrap" style="width:100%">
      <span class="search-icon">🔍</span>
      <input id="sidebarSearch" type="text" placeholder="Tìm mã CK..." style="width:100%" />
    </div>
  </div>
  <div class="nav-section">Menu</div>
  <div class="nav-item active" id="nav-screener" onclick="showView('screener')">
    <span class="icon">⊞</span> Screener
  </div>
  <div class="nav-item" id="nav-benchmark" onclick="showView('benchmark')">
    <span class="icon">📈</span> Industry Benchmark
  </div>
  <div class="nav-item" id="nav-import" onclick="showView('import')">
    <span class="icon">⬆</span> Import Excel
  </div>
  <div class="sidebar-bottom">
    FA Engine v1.0 · fundamental-engine<br>
    <span id="sidebar-batch-info" style="font-size:10px;color:var(--text-muted)">Loading...</span>
  </div>
</nav>

<!-- MAIN -->
<div id="main">
  <div id="topbar">
    <div class="topbar-title" id="topbar-title">📊 Screener</div>
    <div class="search-wrap">
      <span class="search-icon">🔍</span>
      <input id="topSearch" type="text" placeholder="Tra cứu mã (HPG, VNM...)" onkeydown="if(event.key==='Enter')lookupTicker(this.value)" />
    </div>
    <button class="btn btn-primary" onclick="lookupTicker(document.getElementById('topSearch').value)">Tra cứu</button>
  </div>"""

    html_new = """<!-- TOPNAV -->
<nav id="topnav">
  <div class="topnav-logo" onclick="showView('screener')">
    <span class="logo-icon">🐢</span>
    <span>FA <b>Engine</b></span>
  </div>
  
  <div class="nav-links">
    <div class="nav-item active" id="nav-screener" onclick="showView('screener')">Screener</div>
    <div class="nav-item" id="nav-ticker" onclick="showView('ticker')" style="display:none">Detail</div>
    <div class="nav-item" id="nav-benchmark" onclick="showView('benchmark')">Industry</div>
    <div class="nav-item" id="nav-import" onclick="showView('import')">Import</div>
  </div>
  
  <div class="search-wrap">
    <span class="search-icon">🔍</span>
    <input id="topSearch" type="text" placeholder="Tìm kiếm mã (FPT, ETH, AAVE...)" onkeydown="if(event.key==='Enter')lookupTicker(this.value)" />
  </div>
  <div style="font-size:13px; color:var(--text-muted); margin-left:16px;">
    Batch: <span id="sidebar-batch-info">...</span>
  </div>
</nav>

<!-- MAIN -->
<div id="main">"""

    content = content.replace(html_old, html_new)

    # 4. Update JS logic (lookupTicker and renderTickerDetail)
    js_old_lookup = """async function lookupTicker(ticker) {
  if (!ticker) return;
  ticker = ticker.trim().toUpperCase();
  showView('ticker');
  document.getElementById('nav-ticker')?.classList.add('active');
  const container = document.getElementById('ticker-detail-content');
  container.innerHTML = '<div class="empty-state"><span class="spinner"></span><div class="empty-text" style="margin-top:16px">Đang tải ' + ticker + '...</div></div>';
  document.getElementById('topSearch').value = ticker;

  try {
    const [overview, ratios, history] = await Promise.all([
      fetch(`${API}/tickers/${ticker}/overview`).then(r => r.json()),
      fetch(`${API}/tickers/${ticker}/ratios`).then(r => r.json()).catch(() => null),
      fetch(`${API}/tickers/${ticker}/score-history`).then(r => r.json()).catch(() => null),
    ]);
    renderTickerDetail(overview, ratios, history);
  } catch(e) {
    container.innerHTML = `<div class="empty-state" style="color:var(--red)">❌ Không tìm thấy mã <strong>${ticker}</strong></div>`;
  }
}"""

    js_new_lookup = """let chartInstance = null;
async function lookupTicker(ticker) {
  if (!ticker) return;
  ticker = ticker.trim().toUpperCase();
  
  document.getElementById('nav-ticker').style.display = 'flex';
  showView('ticker');
  
  const container = document.getElementById('ticker-detail-content');
  container.innerHTML = '<div class="empty-state"><span class="spinner"></span><div class="empty-text" style="margin-top:16px">Đang tải ' + ticker + '...</div></div>';
  document.getElementById('topSearch').value = ticker;

  try {
    const [overview, ratios, history, financials] = await Promise.all([
      fetch(`${API}/tickers/${ticker}/overview`).then(r => r.json()),
      fetch(`${API}/tickers/${ticker}/ratios`).then(r => r.json()).catch(() => null),
      fetch(`${API}/tickers/${ticker}/score-history`).then(r => r.json()).catch(() => null),
      fetch(`${API}/tickers/${ticker}/financials`).then(r => r.json()).catch(() => null)
    ]);
    renderTickerDetail(overview, ratios, history, financials);
  } catch(e) {
    console.error(e);
    container.innerHTML = `<div class="empty-state" style="color:var(--red)">❌ Không tìm thấy mã <strong>${ticker}</strong></div>`;
  }
}"""
    content = content.replace(js_old_lookup, js_new_lookup)

    js_old_render = """function renderTickerDetail(ov, ratios, history) {
  const score = parseFloat(ov.faScore ?? 0);
  const strokeColor = score >= 80 ? '#3fb950' : score >= 65 ? '#00d4ff' : score >= 50 ? '#d29922' : score >= 35 ? '#f0883e' : '#f85149';

  const ratiosList = (ratios?.ratios || []).map(r => `
    <tr>
      <td style="color:var(--text-muted);font-size:12px">${r.ratioCode}</td>
      <td style="font-weight:600;font-size:13px">${fmtRatioVal(r.ratioCode, r.value)}</td>
      <td><span class="badge ${r.quality === 'OK' ? 'STRONG_FA' : 'WEAK_FA'}" style="font-size:10px">${r.quality}</span></td>
    </tr>`).join('');

  const historyList = (history?.history || []).map(h => `
    <tr>
      <td style="color:var(--text-muted)">${h.period}</td>
      <td>${scoreCell(h.overallScore)}</td>
      <td><span class="badge ${h.rating}">${formatRating(h.rating)}</span></td>
      <td style="font-size:11px;color:var(--text-muted)">${h.batchId || '—'}</td>
    </tr>`).join('');

  document.getElementById('ticker-detail-content').innerHTML = `
    <div class="detail-header">
      <div class="detail-ticker-info">
        <div class="detail-ticker-symbol">${ov.ticker}</div>
        <div class="detail-company-name">${ov.companyName || ''}</div>
        <div class="detail-meta">
          <span class="meta-chip pill-exchange ${ov.exchange}">${ov.exchange || '—'}</span>
          <span class="meta-chip">${ov.industry || 'Chưa xác định ngành'}</span>
          <span class="meta-chip">📅 ${ov.period}</span>
        </div>
      </div>
      <div class="score-ring-wrap" style="text-align:center">
        ${buildScoreRing(score, strokeColor)}
        <div style="margin-top:6px"><span class="badge ${ov.rating}">${formatRating(ov.rating)}</span></div>
        <div class="score-ring-label">${ov.dataQuality === 'OK' ? '✓ Dữ liệu đầy đủ' : '⚠ ' + ov.dataQuality}</div>
      </div>
    </div>

    <div class="cards-row">
      ${metricCard('Giá', fmtPrice(ov.price), 'VNĐ')}
      ${metricCard('P/B', fmtN(ov.pb, 2), 'lần')}
      ${metricCard('P/E (TTM)', fmtN(ov.peTtm, 2), 'lần')}
      ${metricCard('Market Cap', fmtBillions(ov.marketCap), 'tỷ VNĐ')}
    </div>

    <div class="section-title">📊 Phân rã FA Score</div>
    <div class="card" style="margin-bottom:24px">
      <div class="score-bars">
        ${scorebar('Tăng trưởng', ov.growthScore, '#3fb950')}
        ${scorebar('Lợi nhuận',  ov.profitabilityScore, '#00d4ff')}
        ${scorebar('Định giá',   ov.valuationScore, '#a78bfa')}
        ${scorebar('Ổn định',    ov.stabilityScore, '#f5c518')}
        ${scorebar('Chất lượng data', ov.dataQualityScore, '#f0883e')}
      </div>
    </div>

    ${(ov.highlights?.length) ? `<div class="section-title">✅ Điểm mạnh</div><ul class="highlights-list">${ov.highlights.map(h=>`<li>${h}</li>`).join('')}</ul>` : ''}
    ${(ov.warnings?.length) ? `<div class="section-title">⚠️ Lưu ý</div><ul class="warnings-list">${ov.warnings.map(w=>`<li>${w}</li>`).join('')}</ul>` : ''}

    ${ratiosList ? `
    <div class="section-title">📐 Chi tiết tỷ lệ tài chính</div>
    <div class="table-wrap" style="margin-top:0">
      <table><thead><tr><th>Chỉ số</th><th>Giá trị</th><th>Chất lượng</th></tr></thead>
      <tbody>${ratiosList}</tbody></table>
    </div>` : ''}

    ${historyList ? `
    <div class="section-title" style="margin-top:24px">📜 Lịch sử FA Score</div>
    <div class="table-wrap" style="margin-top:0">
      <table><thead><tr><th>Period</th><th>FA Score</th><th>Rating</th><th>Batch</th></tr></thead>
      <tbody>${historyList}</tbody></table>
    </div>` : ''}
  `;
}"""

    js_new_render = """function renderTickerDetail(ov, ratios, history, financials) {
  const score = parseFloat(ov.faScore ?? 0);
  const strokeColor = score >= 80 ? '#3fb950' : score >= 65 ? '#00d4ff' : score >= 50 ? '#d29922' : score >= 35 ? '#f0883e' : '#f85149';

  const ratiosList = (ratios?.ratios || []).map(r => `
    <tr>
      <td style="color:var(--text-muted);font-size:12px">${r.ratioCode}</td>
      <td style="font-weight:600;font-size:13px">${fmtRatioVal(r.ratioCode, r.value)}</td>
      <td><span class="badge ${r.quality === 'OK' ? 'STRONG_FA' : 'WEAK_FA'}" style="font-size:10px">${r.quality}</span></td>
    </tr>`).join('');

  document.getElementById('ticker-detail-content').innerHTML = `
    <!-- Huge Header -->
    <div style="display:flex; align-items:center; gap: 16px; margin-bottom: 8px;">
      <h1 style="font-size:42px; font-weight:800; color:var(--accent); line-height:1">${ov.ticker}</h1>
      <span style="font-size:24px; color:var(--text-muted)">☆</span>
      <h2 style="font-size:18px; font-weight:400; color:var(--text-primary)">— ${ov.companyName || ''}</h2>
    </div>
    <div style="font-size:13px; color:var(--text-muted); margin-bottom:24px;">
      ${ov.exchange} • ${ov.industry || 'Chưa xác định'} <a href="#" style="margin-left:12px; font-weight:600">📈 Xem tín hiệu kỹ thuật →</a>
    </div>

    <!-- Macro Banner -->
    <div class="macro-banner">
      🇻🇳 Bối cảnh vĩ mô Việt Nam: <strong>Trung lập</strong> · Độ tự tin <strong>54%</strong> · cập nhật <strong>30/06/2026</strong>
    </div>

    <!-- Metric Cards -->
    <div class="cards-row" style="margin-bottom:32px;">
      ${metricCard('GIÁ (VND)', fmtPrice(ov.price), '')}
      ${metricCard('P/E (TTM)', fmtN(ov.peTtm, 2), '')}
      ${metricCard('P/B', fmtN(ov.pb, 2), '')}
      ${metricCard('VỐN HÓA (TỶ)', fmtBillions(ov.marketCap), '')}
    </div>

    <!-- Core Analysis -->
    <div class="turtle-section-title">Phân tích cơ bản 15 năm</div>
    <div style="font-size:13px; color:var(--text-muted); margin-bottom:16px;">
      Ngành: <strong>${ov.industry || 'Phi tài chính'}</strong> · Biên gộp · Tăng trưởng · P/E · P/B
    </div>

    <!-- Charts -->
    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); gap: 24px; margin-bottom: 32px;">
      <div class="card" style="padding: 24px;">
        <h3 style="font-size:14px; font-weight:600; margin-bottom: 16px;">1. Doanh thu & Lợi nhuận (Tỷ VND)</h3>
        <div class="chart-container"><canvas id="financialChart"></canvas></div>
      </div>
      <div class="card" style="padding: 24px;">
        <h3 style="font-size:14px; font-weight:600; margin-bottom: 16px;">2. Phân rã FA Score (${score.toFixed(1)})</h3>
        <div class="score-bars" style="margin-top: 24px;">
          ${scorebar('Tăng trưởng', ov.growthScore, '#4caf50')}
          ${scorebar('Lợi nhuận',  ov.profitabilityScore, '#00bcd4')}
          ${scorebar('Định giá',   ov.valuationScore, '#ba68c8')}
          ${scorebar('Ổn định',    ov.stabilityScore, '#f5c518')}
          ${scorebar('Chất lượng', ov.dataQualityScore, '#ff9800')}
        </div>
      </div>
    </div>

    <div style="display:grid; grid-template-columns: 1fr 1fr; gap: 24px;">
      <div>
        ${(ov.highlights?.length) ? `<h3 style="font-size:14px; font-weight:600; margin-bottom: 12px; color:var(--green)">✅ Điểm mạnh</h3><ul class="highlights-list">${ov.highlights.map(h=>`<li>${h}</li>`).join('')}</ul>` : ''}
      </div>
      <div>
        ${(ov.warnings?.length) ? `<h3 style="font-size:14px; font-weight:600; margin-bottom: 12px; color:var(--yellow)">⚠️ Lưu ý</h3><ul class="warnings-list">${ov.warnings.map(w=>`<li>${w}</li>`).join('')}</ul>` : ''}
      </div>
    </div>
  `;

  // Draw Chart if financials exist
  if (financials && financials.metrics && financials.metrics.length > 0) {
    const revs = financials.metrics.filter(m => m.metricCode === 'REVENUE');
    const npats = financials.metrics.filter(m => m.metricCode === 'NPAT');
    
    // Sort by period
    revs.sort((a, b) => a.periodCode.localeCompare(b.periodCode));
    
    const labels = revs.map(r => r.periodCode);
    const revData = revs.map(r => r.value / 1000000000); // Tỷ
    const npatMap = {};
    npats.forEach(n => npatMap[n.periodCode] = n.value / 1000000000);
    const npatData = labels.map(l => npatMap[l] || 0);

    const ctx = document.getElementById('financialChart').getContext('2d');
    if (chartInstance) chartInstance.destroy();
    
    Chart.defaults.color = '#8893a0';
    Chart.defaults.font.family = 'Inter';
    
    chartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [
          { label: 'Doanh thu', data: revData, backgroundColor: '#4c9aff', borderRadius: 4 },
          { label: 'Lợi nhuận', data: npatData, backgroundColor: '#4caf50', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'top', align: 'end', labels: { boxWidth: 12 } } },
        scales: {
          x: { grid: { display: false, drawBorder: false } },
          y: { grid: { color: '#1e2532' }, border: { dash: [4, 4] } }
        }
      }
    });
  }
}"""
    content = content.replace(js_old_render, js_new_render)
    
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

if __name__ == "__main__":
    main()
