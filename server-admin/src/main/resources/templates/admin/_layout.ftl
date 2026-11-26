<#-- page: the HTML page. With `user` (logged-in pages) a header with navigation and logout; with htmx=true HTMX 2.0.11 (ADR 0012: CDN + integrity hash). -->
<#macro page title user="" role="" htmx=false>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} · compose-cast admin</title>
  <style>
    :root { color-scheme: light dark; --fg: #1d1d1f; --bg: #f6f6f7; --card: #fff; --muted: #6b6b70;
            --accent: #3b5bdb; --error: #c92a2a; --line: #dcdce0; }
    @media (prefers-color-scheme: dark) {
      :root { --fg: #ececf0; --bg: #161618; --card: #232326; --muted: #9a9aa2; --accent: #748ffc;
              --error: #ff8787; --line: #3a3a3f; }
    }
    * { box-sizing: border-box; }
    body { margin: 0; font: 16px/1.5 system-ui, sans-serif; color: var(--fg); background: var(--bg); }
    header { display: flex; justify-content: space-between; align-items: center; gap: 16px;
             padding: 12px 16px; border-bottom: 1px solid var(--line); background: var(--card); }
    main { max-width: 720px; margin: 32px auto; padding: 0 16px; }
    .card { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 24px; }
    label { display: block; margin: 12px 0 4px; font-weight: 600; }
    input { width: 100%; padding: 8px 10px; font: inherit; color: inherit; background: var(--bg);
            border: 1px solid var(--line); border-radius: 6px; }
    button { margin-top: 16px; padding: 8px 16px; font: inherit; color: #fff; background: var(--accent);
             border: 0; border-radius: 6px; cursor: pointer; }
    header button { margin: 0; background: transparent; color: var(--accent); border: 1px solid var(--line); }
    .error { color: var(--error); font-weight: 600; }
    .muted { color: var(--muted); }
    nav a { margin-right: 16px; color: var(--accent); text-decoration: none; font-weight: 600; }
    /* Side menu (owner 2026-11-26, like Canva): icon with its label underneath. */
    .shell { display: flex; align-items: flex-start; }
    .shell-main { flex: 1; min-width: 0; overflow-x: auto; } /* wide tables scroll here, the menu stays put */
    .rail { position: sticky; top: 0; flex: 0 0 80px; min-height: calc(100vh - 58px); display: flex; flex-direction: column;
      gap: 4px; padding: 12px 6px; background: var(--card); border-right: 1px solid var(--line); box-sizing: border-box; }
    .rail a { margin: 0; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 2px 8px;
      border-radius: 12px; color: var(--muted); font-size: 11px; font-weight: 600; text-decoration: none; text-align: center; }
    .rail svg { width: 26px; height: 26px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
    .rail a:hover { background: var(--bg); color: var(--fg); }
    .rail a[aria-current="page"] { color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }
    @media (max-width: 760px) {
      .shell { flex-direction: column; align-items: stretch; }
      .rail { position: static; flex-direction: row; justify-content: space-around; min-height: 0; border-right: 0; border-bottom: 1px solid var(--line); }
      .rail a { flex: 1; }
    }
    .wide { max-width: 1100px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 8px; border-bottom: 1px solid var(--line); }
    td a { color: var(--accent); }
    .status { display: inline-block; padding: 0 8px; border-radius: 999px; font-size: 14px; border: 1px solid var(--line); }
    .status-published, .status-live { color: #2b8a3e; } .status-draft { color: #e67700; } .status-unpublished { color: var(--muted); }
    .live-badge { display: inline-flex; align-items: center; gap: 5px; padding: 0 8px; border-radius: 999px;
                  font-size: 13px; font-weight: 600; border: 1px solid var(--line); color: #e03131; vertical-align: middle; }
    .live-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #e03131;
                animation: live-pulse 1000ms ease-in-out infinite; }
    @keyframes live-pulse { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: .25; transform: scale(.75); } }
    textarea { width: 100%; min-height: 60vh; padding: 12px; font: 14px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace;
               color: inherit; background: var(--bg); border: 1px solid var(--line); border-radius: 6px; tab-size: 2; }
    .problems { margin: 12px 0 0; padding-left: 20px; }
    .ok { color: #2b8a3e; font-weight: 600; } .warning { color: #e67700; }
    .row { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
    .row > div { flex: 1 1 200px; }
    code { font: 14px ui-monospace, SFMono-Regular, Menlo, monospace; }
    .split { display: grid; grid-template-columns: minmax(240px, 1fr) 2fr; gap: 16px; align-items: start; }
    @media (max-width: 760px) { .split { grid-template-columns: 1fr; } }
    /* T-048: phone preview beside the editor; below it on narrower screens. */
    .wide.with-phone { max-width: 1440px; }
    .split.with-phone { grid-template-columns: minmax(220px, 1fr) 2fr minmax(220px, 300px); }
    .phone { position: sticky; top: 16px; }
    /* Screens list thumbnails (the phone preview scaled down by the server). */
    td.thumb { width: 90px; padding: 8px 12px 8px 8px; }
    td.thumb img { display: block; width: 90px; height: auto; aspect-ratio: 1078 / 2399; border: 4px solid #1d1d1f;
      border-radius: 14px; background: #fff; object-fit: cover; }
    td.thumb + td, td.thumb ~ td { vertical-align: middle; }
    /* Screens grid (owner 2026-11-26): rectangular cards, thumbnail left, texts stacked right. */
    .screen-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; margin-top: 16px; }
    .screen-card { position: relative; display: flex; gap: 16px; align-items: stretch; padding: 14px; background: var(--card);
      border: 1px solid var(--line); border-radius: 14px; transition: box-shadow .15s ease, border-color .15s ease; }
    .screen-card:hover { border-color: var(--accent); box-shadow: 0 4px 16px rgba(0, 0, 0, .08); }
    .screen-card.no-thumb { min-height: 0; }
    .screen-thumb img { display: block; width: 90px; height: auto; aspect-ratio: 1078 / 2399; border: 4px solid #1d1d1f;
      border-radius: 14px; background: #fff; object-fit: cover; }
    .screen-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; padding-right: 28px; }
    .screen-title { font-size: 17px; font-weight: 700; color: var(--fg); text-decoration: none; overflow-wrap: anywhere; }
    .screen-title::after { content: ""; position: absolute; inset: 0; border-radius: 14px; } /* the whole card is the link */
    .screen-id { color: var(--muted); font-size: 13px; overflow-wrap: anywhere; }
    .screen-badges { display: flex; flex-wrap: wrap; gap: 6px; }
    .screen-meta { margin: auto 0 0; color: var(--muted); font-size: 13px; line-height: 1.4; }
    details.menu.screen-menu { position: absolute; top: 10px; right: 10px; z-index: 1; }
    /* Components: the component alone, landscape, no phone frame. */
    td.thumb-wide { width: 180px; }
    td.thumb-wide img { width: 180px; aspect-ratio: auto; border: 1px solid var(--line); border-radius: 10px; background: var(--bg); }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    .phone img { display: block; width: 100%; height: auto; box-sizing: border-box; border: 10px solid #1d1d1f; border-radius: 32px; background: #fff; }
    @media (max-width: 1100px) { .split.with-phone { grid-template-columns: minmax(240px, 1fr) 2fr; } .split.with-phone .phone { grid-column: 1 / -1; position: static; max-width: 320px; } }
    @media (max-width: 760px) { .split.with-phone { grid-template-columns: 1fr; } }
    .tree a, .tree-list { display: block; padding: 4px 8px; border-radius: 6px; text-decoration: none; color: inherit;
                          white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .tree a.selected { background: var(--bg); outline: 2px solid var(--accent); }
    .tree a.dragging { opacity: .45; }
    .tree a.drop-before { box-shadow: inset 0 2px 0 var(--accent); }
    .tree a.drop-after { box-shadow: inset 0 -2px 0 var(--accent); }
    .tree a.drop-inside { background: var(--bg); outline: 2px dashed var(--accent); }
    .tree-list { font-size: 13px; text-transform: uppercase; letter-spacing: .04em; }
    details.group { border: 1px solid var(--line); border-radius: 8px; margin: 12px 0; padding: 8px 12px; }
    details.group > summary { cursor: pointer; font-weight: 600; }
    details.group .fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 0 12px; margin-top: 8px; }
    details.group .fields > details, details.group .fields > .field:has(textarea), details.group .fields > .variant-type { grid-column: 1 / -1; }
    .required { color: var(--error); }
    .panel > .card { margin-bottom: 16px; }
    .toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 8px; }
    .toolbar button { margin: 0; padding: 6px 12px; }
    .toolbar button:disabled { opacity: .4; cursor: default; }
    button.danger { background: var(--error); }
    label.inline { display: inline-flex; gap: 8px; align-items: center; margin: 0; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    button.chip { margin: 0; padding: 4px 8px; background: var(--bg); color: var(--fg); border: 1px solid var(--line);
                  max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; text-align: left; }
    button.chip[aria-selected="true"] { background: var(--accent); color: #fff; border-color: var(--accent); }
    .fields-title { font-size: 15px; margin: 12px 0 6px; }
    fieldset.plain { border: 0; margin: 0; padding: 0; }
    details.menu { position: relative; display: inline-block; }
    details.menu > summary { list-style: none; cursor: pointer; padding: 2px 10px; border-radius: 6px;
                             border: 1px solid var(--line); background: var(--bg); color: var(--fg); font-weight: 600; }
    details.menu > summary::-webkit-details-marker { display: none; }
    details.menu[open] > .menu-list { position: absolute; right: 0; top: calc(100% + 4px); z-index: 10; min-width: 150px;
                                       background: var(--card); border: 1px solid var(--line); border-radius: 8px;
                                       padding: 4px; box-shadow: 0 4px 12px rgba(0,0,0,.15); }
    details.menu .menu-list button { display: block; width: 100%; margin: 0; padding: 6px 10px; text-align: left;
                                     background: transparent; color: var(--fg); border-radius: 6px; }
    details.menu .menu-list button:hover { background: var(--bg); }
    details.menu .menu-list button.danger { color: var(--error); }
    .field { margin-bottom: 8px; }
    .help { font-size: 13px; }
    select { padding: 8px; font: inherit; color: inherit; background: var(--bg); border: 1px solid var(--line); border-radius: 6px; }
    textarea.json { min-height: 0; }
    label.toggle { font-weight: normal; }
    label.toggle input { width: auto; }
    .card-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 8px; }
    .card-head h2 { margin: 0; }
    .btn-link { display: inline-block; padding: 6px 14px; font-size: 14px; font-weight: 600; color: #fff;
                background: var(--accent); border-radius: 6px; text-decoration: none; white-space: nowrap; }
    .btn-link:hover { opacity: .9; }
    .item-list { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
    .item-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 10px 14px;
                border: 1px solid var(--line); border-radius: 8px; background: var(--bg); text-decoration: none;
                color: inherit; transition: border-color .15s ease; }
    .item-row:hover { border-color: var(--accent); }
    .item-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
    .item-name { font-weight: 600; color: var(--fg); }
    .item-meta { font-size: 13px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .item-badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600;
                  background: var(--card); border: 1px solid var(--line); color: var(--accent); white-space: nowrap; }
    .item-chevron { color: var(--muted); font-size: 18px; line-height: 1; flex-shrink: 0; }
    .action-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; margin: 16px 0 20px; }
    .action-card { display: flex; align-items: flex-start; gap: 14px; padding: 16px 18px; background: var(--card);
                   border: 1px solid var(--line); border-radius: 10px; text-decoration: none; color: inherit;
                   transition: border-color .15s ease, transform .15s ease, box-shadow .15s ease; }
    .action-card:hover { border-color: var(--accent); transform: translateY(-1px); box-shadow: 0 4px 14px rgba(0,0,0,.06); }
    .action-icon { flex: 0 0 38px; height: 38px; border-radius: 8px; background: var(--bg); border: 1px solid var(--line);
                   display: flex; align-items: center; justify-content: center; font-size: 18px; }
    .action-body { flex: 1; min-width: 0; }
    .action-title { font-weight: 600; color: var(--fg); display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .action-desc { font-size: 13px; color: var(--muted); margin-top: 3px; line-height: 1.4; }
    .action-arrow { color: var(--accent); font-size: 16px; transition: transform .15s ease; }
    .action-card:hover .action-arrow { transform: translateX(3px); }
  </style>
  <#if htmx><script src="https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js" integrity="sha384-2OatzQy1H+Zd/IIrjr1TcuDGqLXeHhbooAyJY1KdQMKnr4LZ22k31GBLdYKHmVjg" crossorigin="anonymous"></script></#if>
</head>
<body>
<#if user?has_content>
<header>
  <strong>compose-cast admin</strong>
  <form method="post" action="/admin/logout">
    <span class="muted">${user.email} (${role})</span>
    <button type="submit">Log out</button>
  </form>
</header>
<div class="shell">
  <#assign current = section!"">
  <nav class="rail" aria-label="Main">
    <a href="/admin/screens"<#if current == "screens"> aria-current="page"</#if>>
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="2.5" width="12" height="19" rx="2.5"/><path d="M10.5 18.5h3"/></svg>
      <span>Screens</span>
    </a>
    <#if hasComponents!false>
    <a href="/admin/components"<#if current == "components"> aria-current="page"</#if>>
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="8" height="8" rx="1.5"/><circle cx="17" cy="7" r="4"/><path d="M7 13.5 L11 20.5 H3 Z"/><rect x="13" y="13" width="8" height="8" rx="4" ry="1.5"/></svg>
      <span>Components</span>
    </a>
    </#if>
    <a href="/admin/templates"<#if current == "templates"> aria-current="page"</#if>>
      <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2.5"/><path d="M3 9h18M9 9v12"/></svg>
      <span>Templates</span>
    </a>
    <a href="/admin/settings"<#if current == "settings"> aria-current="page"</#if>>
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.2"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/></svg>
      <span>Settings</span>
    </a>
  </nav>
  <div class="shell-main">
<#nested>
  </div>
</div>
<#else>
<#nested>
</#if>
</body>
</html>
</#macro>
