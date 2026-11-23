<#macro page title>
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
  </style>
</head>
<body>
<#nested>
</body>
</html>
</#macro>
