<#-- uid: copyable AI reference ID icon button next to headings when debug mode is enabled in Settings. -->
<#macro uid id><#if debugMode!false><button type="button" class="ui-id-btn" data-ui-id="${id}" title="Copy AI reference ID: ${id}" aria-label="Copy ID ${id}"><svg class="icon-link" viewBox="0 0 16 16" aria-hidden="true"><path d="M6.5 9.5l3-3M5 7.5L3.6 8.9a2.5 2.5 0 0 0 3.5 3.5L8.5 11M11 8.5l1.4-1.4a2.5 2.5 0 0 0-3.5-3.5L7.5 5"/></svg><svg class="icon-ok" viewBox="0 0 16 16" aria-hidden="true"><path d="M3.5 8.5l3 3 6-7"/></svg></button></#if></#macro>
<#-- previewTheme: Light/Dark segmented switcher bound to a preview <img> element by CSS selector. -->
<#macro previewTheme target><div class="preview-theme" role="group" aria-label="Preview theme" data-preview-target="${target}"><button type="button" class="chip" data-preview-mode="light" aria-pressed="false">☀ Light</button><button type="button" class="chip" data-preview-mode="dark" aria-pressed="false">🌙 Dark</button></div></#macro>
<#-- page: the HTML page. With `user` (logged-in pages) a header with navigation and logout; with htmx=true HTMX 2.0.11 (ADR 0012: CDN + integrity hash). -->
<#macro page title user="" role="" htmx=false>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} · compose-cast admin</title>
  <script>(function(){try{var t=localStorage.getItem("composecast.adminTheme");if(t!=="dark"&&t!=="light")t=matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light";document.documentElement.dataset.theme=t;}catch(e){}})();</script>
  <style>
    :root { color-scheme: light; --fg: #1d1d1f; --bg: #f6f6f7; --card: #fff; --muted: #6b6b70;
            --accent: #3b5bdb; --error: #c92a2a; --line: #dcdce0; }
    :root[data-theme="dark"] { color-scheme: dark; --fg: #ececf0; --bg: #161618; --card: #232326; --muted: #9a9aa2;
                               --accent: #748ffc; --error: #ff8787; --line: #3a3a3f; }
    @media (prefers-color-scheme: dark) {
      :root:not([data-theme="light"]) { color-scheme: dark; --fg: #ececf0; --bg: #161618; --card: #232326; --muted: #9a9aa2;
                                        --accent: #748ffc; --error: #ff8787; --line: #3a3a3f; }
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
    .debug-badge { display: inline-block; margin-left: 8px; padding: 1px 7px; border-radius: 999px; font-size: 11px;
                   font-weight: 600; text-transform: uppercase; letter-spacing: .04em; color: var(--accent);
                   background: color-mix(in srgb, var(--accent) 12%, transparent); vertical-align: middle; }
    button.ui-id-btn { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px;
                       padding: 0; margin: 0 0 0 6px; vertical-align: middle; background: transparent;
                       color: var(--muted); border: 1px solid transparent; border-radius: 5px; opacity: .55;
                       cursor: pointer; transition: opacity .12s ease, color .12s ease, border-color .12s ease; }
    button.ui-id-btn:hover { opacity: 1; color: var(--accent); border-color: var(--line); background: var(--bg); }
    button.ui-id-btn svg { width: 13px; height: 13px; fill: none; stroke: currentColor; stroke-width: 1.8;
                           stroke-linecap: round; stroke-linejoin: round; }
    button.ui-id-btn .icon-ok { display: none; }
    button.ui-id-btn.copied { opacity: 1; color: #2b8a3e; border-color: #2b8a3e; background: var(--bg); }
    button.ui-id-btn.copied .icon-link { display: none; }
    button.ui-id-btn.copied .icon-ok { display: block; }
    .header-actions { display: flex; align-items: center; gap: 10px; }
    button.theme-toggle { margin: 0; padding: 5px 10px; background: var(--bg); color: var(--fg);
                          border: 1px solid var(--line); border-radius: 6px; font-size: 14px; line-height: 1.2; }
    button.theme-toggle:hover { border-color: var(--accent); }
    .preview-theme { display: inline-flex; gap: 4px; margin-bottom: 8px; }
    .preview-theme button.chip { font-size: 13px; padding: 3px 9px; }
    .preview-theme button.chip[aria-pressed="true"] { background: var(--accent); color: #fff; border-color: var(--accent); }
    .ai-messages { max-height: 360px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px;
                   padding: 12px; margin-bottom: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--bg); }
    .ai-msg { padding: 10px 12px; border-radius: 8px; max-width: 92%; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; }
    .ai-msg-user { align-self: flex-end; background: color-mix(in srgb, var(--accent) 14%, var(--card)); border: 1px solid color-mix(in srgb, var(--accent) 35%, var(--line)); }
    .ai-msg-assistant { align-self: flex-start; background: var(--card); border: 1px solid var(--line); }
    .ai-msg-img { display: block; max-width: 180px; max-height: 140px; border-radius: 6px; margin-bottom: 6px; border: 1px solid var(--line); }
    .ai-image-preview { display: inline-flex; align-items: center; gap: 8px; margin-bottom: 8px; padding: 6px 8px;
                        border: 1px solid var(--line); border-radius: 8px; background: var(--bg); }
    .ai-image-preview img { max-height: 56px; max-width: 96px; border-radius: 4px; }
    .ai-prompt { min-height: 76px; font-family: inherit; }
    .cc-color { display: flex; gap: 6px; align-items: center; }
    .cc-color input[type=text], .cc-color input:not([type]) { flex: 1; min-width: 0; }
    details.cc-picker { position: relative; flex: 0 0 auto; }
    details.cc-picker > summary { list-style: none; cursor: pointer; width: 36px; height: 34px; padding: 3px;
      display: flex; align-items: center; justify-content: center; border: 1px solid var(--line); border-radius: 6px;
      background: var(--bg); user-select: none; box-sizing: border-box; }
    details.cc-picker > summary::-webkit-details-marker { display: none; }
    details.cc-picker > summary:hover { border-color: var(--accent); }
    details.cc-picker[data-disabled="true"] > summary { pointer-events: none; opacity: .5; cursor: default; }
    .cc-chip { width: 100%; height: 100%; border-radius: 4px; border: 1px solid rgba(128,128,128,.35);
      background-image: linear-gradient(45deg, rgba(128,128,128,.22) 25%, transparent 25%),
        linear-gradient(-45deg, rgba(128,128,128,.22) 25%, transparent 25%),
        linear-gradient(45deg, transparent 75%, rgba(128,128,128,.22) 75%),
        linear-gradient(-45deg, transparent 75%, rgba(128,128,128,.22) 75%);
      background-size: 8px 8px; background-position: 0 0, 0 4px, 4px -4px, -4px 0; position: relative; overflow: hidden; }
    .cc-chip-fill { position: absolute; inset: 0; }
    details.cc-picker[open] > .cc-picker-pop { position: absolute; left: 0; top: calc(100% + 4px); z-index: 60;
      width: 232px; padding: 10px; background: var(--card); border: 1px solid var(--line); border-radius: 10px;
      box-shadow: 0 8px 24px rgba(0,0,0,.26); box-sizing: border-box; }
    details.cc-picker.cc-pop-right[open] > .cc-picker-pop { left: auto; right: 0; }
    .cc-sv { position: relative; width: 100%; height: 132px; border-radius: 6px; cursor: crosshair; touch-action: none;
      border: 1px solid rgba(128,128,128,.3); overflow: hidden; user-select: none; }
    .cc-sv::before { content: ""; position: absolute; inset: 0; background: linear-gradient(to right, #fff, rgba(255,255,255,0)); }
    .cc-sv::after { content: ""; position: absolute; inset: 0; background: linear-gradient(to top, #000, rgba(0,0,0,0)); }
    .cc-sv-thumb { position: absolute; z-index: 2; width: 14px; height: 14px; border-radius: 50%;
      border: 2px solid #fff; box-shadow: 0 0 0 1px rgba(0,0,0,.55), 0 1px 3px rgba(0,0,0,.4);
      transform: translate(-50%, -50%); pointer-events: none; }
    .cc-hue { position: relative; width: 100%; height: 14px; margin: 10px 0 10px; border-radius: 999px; cursor: ew-resize;
      touch-action: none; border: 1px solid rgba(128,128,128,.3); user-select: none;
      background: linear-gradient(to right, #f00 0%, #ff0 17%, #0f0 33%, #0ff 50%, #00f 67%, #f0f 83%, #f00 100%); }
    .cc-hue-thumb { position: absolute; top: 50%; width: 14px; height: 14px; border-radius: 50%;
      border: 2px solid #fff; box-shadow: 0 0 0 1px rgba(0,0,0,.55), 0 1px 3px rgba(0,0,0,.4);
      transform: translate(-50%, -50%); pointer-events: none; }
    .cc-swatch-title { font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase;
      letter-spacing: .04em; margin: 0 0 6px; padding-top: 8px; border-top: 1px solid var(--line); }
    .cc-swatch-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
    button.cc-swatch { margin: 0; padding: 0; width: 28px; height: 24px; border-radius: 5px;
      border: 1px solid rgba(128,128,128,.35); cursor: pointer; }
    button.cc-swatch:hover { transform: scale(1.1); border-color: var(--fg); }
  </style>
  <#if htmx><script src="https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js" integrity="sha384-2OatzQy1H+Zd/IIrjr1TcuDGqLXeHhbooAyJY1KdQMKnr4LZ22k31GBLdYKHmVjg" crossorigin="anonymous"></script></#if>
</head>
<body>
<#if user?has_content>
<header>
  <strong>compose-cast admin<#if debugMode!false><span class="debug-badge">debug</span></#if></strong>
  <div class="header-actions">
    <button type="button" id="theme-toggle" class="theme-toggle" aria-label="Toggle dark/light mode" title="Switch admin theme">🌙</button>
    <form method="post" action="/admin/logout" style="margin: 0">
      <span class="muted">${user.email} (${role})</span>
      <button type="submit">Log out</button>
    </form>
  </div>
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
    <#if isAdmin!false>
    <a href="/admin/users"<#if current == "users"> aria-current="page"</#if>>
      <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 19.5c.8-3.2 3.3-5 6.5-5s5.7 1.8 6.5 5"/><circle cx="17" cy="9.5" r="2.5"/><path d="M17.5 14.5c2 .3 3.5 1.7 4 4"/></svg>
      <span>Users</span>
    </a>
    </#if>
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
<script>
  (function () {
    function currentAdminTheme() {
      return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
    }
    function setPreviewImage(img, theme, bust) {
      if (!img) return;
      const base = (img.dataset.baseSrc || img.getAttribute("src") || "").split("?")[0];
      if (!base) return;
      img.dataset.baseSrc = base;
      img.dataset.previewTheme = theme;
      img.src = base + "?theme=" + theme + (bust ? "&t=" + Date.now() : "");
    }
    window.composeCastPreviewTheme = function (img) {
      return (img && img.dataset.previewTheme) || currentAdminTheme();
    };
    function syncSwitchers(adminTheme) {
      document.querySelectorAll(".preview-theme[data-preview-target]").forEach(function (group) {
        if (group.dataset.manual === "true") return;
        applyGroupTheme(group, adminTheme, false);
      });
      document.querySelectorAll("img[data-theme-thumb]").forEach(function (img) {
        setPreviewImage(img, adminTheme, false);
      });
    }
    function applyGroupTheme(group, theme, bust) {
      group.dataset.theme = theme;
      group.querySelectorAll("button[data-preview-mode]").forEach(function (b) {
        b.setAttribute("aria-pressed", String(b.dataset.previewMode === theme));
      });
      const img = document.querySelector(group.dataset.previewTarget);
      if (img) setPreviewImage(img, theme, bust);
    }
    const toggle = document.getElementById("theme-toggle");
    function updateToggleButton() {
      if (!toggle) return;
      const dark = currentAdminTheme() === "dark";
      toggle.textContent = dark ? "☀" : "🌙";
      toggle.title = dark ? "Switch admin to light mode" : "Switch admin to dark mode";
      toggle.setAttribute("aria-label", toggle.title);
    }
    updateToggleButton();
    syncSwitchers(currentAdminTheme());
    if (toggle) {
      toggle.addEventListener("click", function () {
        const next = currentAdminTheme() === "dark" ? "light" : "dark";
        document.documentElement.dataset.theme = next;
        try { localStorage.setItem("composecast.adminTheme", next); } catch (e) {}
        updateToggleButton();
        syncSwitchers(next);
      });
    }
    document.addEventListener("click", function (e) {
      const btn = e.target.closest(".preview-theme button[data-preview-mode]");
      if (!btn) return;
      const group = btn.closest(".preview-theme");
      group.dataset.manual = "true";
      applyGroupTheme(group, btn.dataset.previewMode, true);
    });

    // Unified color picker (custom HSV + Material Design palette in one popover) across Settings, Visual editor & Designer.
    const MATERIAL_COLORS = [
      ["Red", "#F44336"], ["Pink", "#E91E63"], ["Purple", "#9C27B0"], ["Deep Purple", "#673AB7"], ["Indigo", "#3F51B5"], ["Blue", "#2196F3"],
      ["Light Blue", "#03A9F4"], ["Cyan", "#00BCD4"], ["Teal", "#009688"], ["Green", "#4CAF50"], ["Light Green", "#8BC34A"], ["Lime", "#CDDC39"],
      ["Yellow", "#FFEB3B"], ["Amber", "#FFC107"], ["Orange", "#FF9800"], ["Deep Orange", "#FF5722"], ["M3 Primary", "#6200EE"], ["M3 Dark Primary", "#D0BCFF"],
      ["White", "#FFFFFF"], ["M3 Surface", "#F3EDF7"], ["Grey 300", "#E0E0E0"], ["Grey 600", "#757575"], ["M3 Dark Surface", "#211F26"], ["M3 Dark Bg", "#141218"],
    ];
    function parseHex6(val) {
      const s = (val || "").trim();
      if (/^#[0-9a-fA-F]{6}$/.test(s)) return s.toUpperCase();
      if (/^#[0-9a-fA-F]{8}$/.test(s)) return "#" + s.slice(3).toUpperCase();
      return null;
    }
    function hexToHsv(hex, fallbackHue) {
      const clean = parseHex6(hex) || "#000000";
      const r = parseInt(clean.slice(1, 3), 16) / 255;
      const g = parseInt(clean.slice(3, 5), 16) / 255;
      const b = parseInt(clean.slice(5, 7), 16) / 255;
      const max = Math.max(r, g, b), min = Math.min(r, g, b), d = max - min;
      let h = fallbackHue || 0;
      if (d !== 0) {
        if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
        else if (max === g) h = ((b - r) / d + 2) * 60;
        else h = ((r - g) / d + 4) * 60;
      }
      return { h: (h + 360) % 360, s: max === 0 ? 0 : d / max, v: max };
    }
    function hsvToHex(h, s, v) {
      const c = v * s;
      const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
      const m = v - c;
      let r = 0, g = 0, b = 0;
      if (h < 60) { r = c; g = x; }
      else if (h < 120) { r = x; g = c; }
      else if (h < 180) { g = c; b = x; }
      else if (h < 240) { g = x; b = c; }
      else if (h < 300) { r = x; b = c; }
      else { r = c; b = x; }
      const toByte = function (n) { return Math.round((n + m) * 255).toString(16).padStart(2, "0"); };
      return ("#" + toByte(r) + toByte(g) + toByte(b)).toUpperCase();
    }
    window.composeCastColorPicker = function (opts) {
      const disabled = !!opts.disabled;
      const onInput = opts.onInput || function () {};
      const onChange = opts.onChange || function () {};
      let hsv = hexToHsv(opts.value, 220);

      const details = document.createElement("details");
      details.className = "cc-picker";
      if (disabled) details.dataset.disabled = "true";

      const summary = document.createElement("summary");
      summary.title = "Pick color (custom or Material Design)";
      summary.setAttribute("aria-label", "Pick color");
      const chip = document.createElement("span");
      chip.className = "cc-chip";
      const chipFill = document.createElement("span");
      chipFill.className = "cc-chip-fill";
      chip.appendChild(chipFill);
      summary.appendChild(chip);

      const pop = document.createElement("div");
      pop.className = "cc-picker-pop";

      const sv = document.createElement("div");
      sv.className = "cc-sv";
      const svThumb = document.createElement("div");
      svThumb.className = "cc-sv-thumb";
      sv.appendChild(svThumb);

      const hue = document.createElement("div");
      hue.className = "cc-hue";
      const hueThumb = document.createElement("div");
      hueThumb.className = "cc-hue-thumb";
      hue.appendChild(hueThumb);

      const heading = document.createElement("p");
      heading.className = "cc-swatch-title";
      heading.textContent = "Material Design";
      const grid = document.createElement("div");
      grid.className = "cc-swatch-grid";

      function renderControls(explicitHex) {
        const hex = explicitHex === null ? null : (explicitHex || hsvToHex(hsv.h, hsv.s, hsv.v));
        chipFill.style.background = hex || "transparent";
        sv.style.backgroundColor = "hsl(" + Math.round(hsv.h) + ", 100%, 50%)";
        svThumb.style.left = (hsv.s * 100) + "%";
        svThumb.style.top = ((1 - hsv.v) * 100) + "%";
        svThumb.style.backgroundColor = hsvToHex(hsv.h, hsv.s, hsv.v);
        hueThumb.style.left = ((hsv.h / 360) * 100) + "%";
        hueThumb.style.backgroundColor = "hsl(" + Math.round(hsv.h) + ", 100%, 50%)";
      }

      function bindDrag(el, update) {
        el.addEventListener("pointerdown", function (ev) {
          if (disabled) return;
          ev.preventDefault();
          el.setPointerCapture(ev.pointerId);
          update(ev);
          onInput(hsvToHex(hsv.h, hsv.s, hsv.v));
          function move(e) {
            update(e);
            onInput(hsvToHex(hsv.h, hsv.s, hsv.v));
          }
          function up() {
            el.removeEventListener("pointermove", move);
            el.removeEventListener("pointerup", up);
            el.removeEventListener("pointercancel", up);
            onChange(hsvToHex(hsv.h, hsv.s, hsv.v));
          }
          el.addEventListener("pointermove", move);
          el.addEventListener("pointerup", up);
          el.addEventListener("pointercancel", up);
        });
      }

      bindDrag(sv, function (ev) {
        const r = sv.getBoundingClientRect();
        hsv.s = Math.max(0, Math.min(1, (ev.clientX - r.left) / r.width));
        hsv.v = Math.max(0, Math.min(1, 1 - (ev.clientY - r.top) / r.height));
        renderControls();
      });

      bindDrag(hue, function (ev) {
        const r = hue.getBoundingClientRect();
        hsv.h = Math.max(0, Math.min(359.99, ((ev.clientX - r.left) / r.width) * 360));
        renderControls();
      });

      MATERIAL_COLORS.forEach(function (entry) {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "cc-swatch";
        b.style.background = entry[1];
        b.title = entry[0] + " (" + entry[1] + ")";
        b.setAttribute("aria-label", entry[0] + " " + entry[1]);
        b.addEventListener("click", function () {
          if (disabled) return;
          hsv = hexToHsv(entry[1], hsv.h);
          renderControls(entry[1]);
          onInput(entry[1]);
          onChange(entry[1]);
          details.open = false;
        });
        grid.appendChild(b);
      });

      details.addEventListener("toggle", function () {
        if (!details.open) return;
        document.querySelectorAll("details.cc-picker[open]").forEach(function (other) {
          if (other !== details) other.open = false;
        });
        const rect = summary.getBoundingClientRect();
        details.classList.toggle("cc-pop-right", rect.left + 240 > window.innerWidth);
      });

      pop.append(sv, hue, heading, grid);
      details.append(summary, pop);
      renderControls(parseHex6(opts.value));

      return {
        el: details,
        setColor: function (val) {
          const parsed = parseHex6(val);
          if (parsed) {
            hsv = hexToHsv(parsed, hsv.h);
            renderControls(parsed);
          } else {
            renderControls(null);
          }
        },
      };
    };
    function enhanceColorInputs(root) {
      (root || document).querySelectorAll("input[data-color-picker='true']:not([data-color-ready])").forEach(function (input) {
        input.dataset.colorReady = "true";
        const disabled = input.disabled || input.readOnly;
        const wrap = document.createElement("div");
        wrap.className = "cc-color";
        input.parentNode.insertBefore(wrap, input);
        function applyHex(hex, commit) {
          const old = input.value.trim();
          const alpha = /^#[0-9a-fA-F]{8}$/.test(old) ? old.slice(1, 3) : "";
          input.value = "#" + alpha + hex.slice(1).toUpperCase();
          input.dispatchEvent(new Event("input", { bubbles: true }));
          if (commit) input.dispatchEvent(new Event("change", { bubbles: true }));
        }
        const picker = window.composeCastColorPicker({
          value: input.value,
          disabled: disabled,
          onInput: function (hex) { applyHex(hex, false); },
          onChange: function (hex) { applyHex(hex, true); },
        });
        input.addEventListener("input", function () {
          picker.setColor(input.value);
        });
        wrap.append(picker.el, input);
      });
    }
    enhanceColorInputs(document);
    <#if htmx>document.addEventListener("htmx:afterSwap", function (e) { enhanceColorInputs(e.target); });</#if>
    document.addEventListener("click", function (e) {
      document.querySelectorAll("details.cc-picker[open]").forEach(function (d) {
        if (!d.contains(e.target)) d.open = false;
      });
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        document.querySelectorAll("details.cc-picker[open]").forEach(function (d) { d.open = false; });
      }
    });
  })();
</script>
<#if debugMode!false>
<script>
  document.addEventListener("click", function (e) {
    const btn = e.target.closest("button.ui-id-btn[data-ui-id]");
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();
    const id = btn.dataset.uiId;
    const mark = () => {
      btn.classList.add("copied");
      clearTimeout(btn._t);
      btn._t = setTimeout(() => btn.classList.remove("copied"), 1200);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(id).then(mark).catch(() => fallbackCopy(id, mark));
    } else {
      fallbackCopy(id, mark);
    }
    function fallbackCopy(text, done) {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand("copy"); done(); } finally { ta.remove(); }
    }
  });
</script>
</#if>
</body>
</html>
</#macro>
