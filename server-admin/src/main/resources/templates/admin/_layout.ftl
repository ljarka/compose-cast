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
    .wide { max-width: 1100px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 8px; border-bottom: 1px solid var(--line); }
    td a { color: var(--accent); }
    .status { display: inline-block; padding: 0 8px; border-radius: 999px; font-size: 14px; border: 1px solid var(--line); }
    .status-live { color: #2b8a3e; } .status-draft { color: #e67700; } .status-unpublished { color: var(--muted); }
    textarea { width: 100%; min-height: 60vh; padding: 12px; font: 14px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace;
               color: inherit; background: var(--bg); border: 1px solid var(--line); border-radius: 6px; tab-size: 2; }
    .problems { margin: 12px 0 0; padding-left: 20px; }
    .ok { color: #2b8a3e; font-weight: 600; } .warning { color: #e67700; }
    .row { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
    .row > div { flex: 1 1 200px; }
    code { font: 14px ui-monospace, SFMono-Regular, Menlo, monospace; }
    .split { display: grid; grid-template-columns: minmax(240px, 1fr) 2fr; gap: 16px; align-items: start; }
    @media (max-width: 760px) { .split { grid-template-columns: 1fr; } }
    .tree a, .tree-list { display: block; padding: 4px 8px; border-radius: 6px; text-decoration: none; color: inherit;
                          white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .tree a.selected { background: var(--bg); outline: 2px solid var(--accent); }
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
    .fields-title { font-size: 15px; margin: 12px 0 6px; }
    fieldset.plain { border: 0; margin: 0; padding: 0; }
    .field { margin-bottom: 8px; }
    .help { font-size: 13px; }
    select { padding: 8px; font: inherit; color: inherit; background: var(--bg); border: 1px solid var(--line); border-radius: 6px; }
    textarea.json { min-height: 0; }
    label.toggle { font-weight: normal; }
    label.toggle input { width: auto; }
  </style>
  <#if htmx><script src="https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js" integrity="sha384-2OatzQy1H+Zd/IIrjr1TcuDGqLXeHhbooAyJY1KdQMKnr4LZ22k31GBLdYKHmVjg" crossorigin="anonymous"></script></#if>
</head>
<body>
<#if user?has_content>
<header>
  <nav><strong>compose-cast admin</strong>&nbsp;&nbsp;<a href="/admin/screens">Screens</a><a href="/admin/settings">Settings</a></nav>
  <form method="post" action="/admin/logout">
    <span class="muted">${user.email} (${role})</span>
    <button type="submit">Log out</button>
  </form>
</header>
</#if>
<#nested>
</body>
</html>
</#macro>
