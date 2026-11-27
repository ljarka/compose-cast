<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<style>
  .tpl-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
  .tpl-head h2 { margin: 0; }
  .tpl-palette { display: inline-flex; align-items: center; gap: 5px; }
  .tpl-dot { width: 16px; height: 16px; border-radius: 50%; border: 1px solid rgba(128,128,128,.4); display: inline-block; }
  .tpl-previews { display: flex; gap: 14px; flex-wrap: wrap; margin: 14px 0 16px; }
  .tpl-screen-btn { margin: 0; padding: 8px; background: var(--bg); color: var(--fg); border: 1px solid var(--line);
    border-radius: 12px; display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer;
    transition: border-color .15s, transform .15s; }
  .tpl-screen-btn:hover { border-color: var(--accent); transform: translateY(-2px); }
  .tpl-screen-btn img { display: block; width: 110px; height: 244px; object-fit: cover; border-radius: 8px;
    border: 1px solid var(--line); background: var(--card); }
  .tpl-screen-label { font-size: 12px; font-weight: 600; display: flex; align-items: center; gap: 5px; }
  .tpl-wire { padding: 10px 12px; min-width: 170px; max-width: 240px; border: 1px solid var(--line);
    border-radius: 10px; background: var(--bg); display: flex; flex-direction: column; gap: 6px; }
  .tpl-wire-title { font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .tpl-chips { display: flex; gap: 4px; flex-wrap: wrap; }
  .tpl-chip { font-size: 11px; padding: 2px 7px; border-radius: 999px; border: 1px solid var(--line);
    background: var(--card); color: var(--muted); }
  dialog.tpl-dialog { padding: 16px; border: 1px solid var(--line); border-radius: 14px; background: var(--card);
    color: var(--fg); max-width: 380px; width: calc(100vw - 32px); box-shadow: 0 16px 48px rgba(0,0,0,.4); }
  dialog.tpl-dialog::backdrop { background: rgba(0,0,0,.55); }
  .tpl-dialog-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
  .tpl-dialog-head h3 { margin: 0; font-size: 16px; }
  .tpl-dialog-bar { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
  .tpl-dialog img { display: block; width: 100%; height: auto; border-radius: 12px; border: 1px solid var(--line); }
</style>
<main class="wide">
  <p><a href="/admin/screens">← Screens</a></p>
  <h1>Start from a template<@layout.uid "admin:templates.header"/></h1>
  <p class="muted">A template is a whole app: its screens, their data and the navigation between them. Its screens
    are added as <strong>drafts</strong> — nothing changes in the app until you publish.</p>

  <#if applied?has_content>
  <div class="card" id="applied" style="margin-bottom: 16px">
    <h2 style="margin-top: 0">${applied.name}: ${applied.screens?size} drafts added<@layout.uid "admin:templates.applied"/></h2>
    <ul>
      <#list applied.screens as s><li><a href="/admin/screens/${s?url}/visual?node="><code>${s}</code></a><#if s == applied.home> — home screen</#if></li></#list>
    </ul>
    <#if applied.brandApplied><p class="ok">The app now uses the template's colors.</p></#if>
    <#if applied.brandError?has_content><p class="error" role="alert">Colors not changed: ${applied.brandError}</p></#if>
    <#if applied.secrets?has_content>
      <p>Before publishing, set these on the server (<code>.env</code>):</p>
      <ul><#list applied.secrets as s><li><code>COMPOSECAST_SECRET_${s.name}</code> — ${s.why}</li></#list></ul>
    </#if>
    <p class="muted">Edit them, preview them on a phone, or make them live now — <code>${applied.home}</code> becomes the screen the app opens on.</p>
    <#if canEdit>
    <form method="post" action="/admin/templates/${applied.id?url}/publish" style="margin: 0">
      <input type="hidden" name="prefix" value="${applied.prefix}">
      <button type="submit">Publish all ${applied.screens?size} screens</button>
    </form>
    </#if>
  </div>
  </#if>

  <#if empty && !applied?has_content><p class="ok">This app has no screens yet — pick a template to get a working app in a minute.</p></#if>

  <#list templates as t>
  <div class="card" id="template-${t.id}" style="margin-bottom: 16px">
    <div class="tpl-head">
      <h2>${t.name}<#if t.live> <span class="status status-live">live</span></#if><@layout.uid "admin:templates.card.${t.id}"/></h2>
      <#if t.colors?has_content>
      <div class="tpl-palette" aria-label="Template color palette">
        <#list t.colors as c>
          <span class="tpl-dot" style="background: ${c.hex}" title="${c.label}: ${c.hex}"></span>
        </#list>
      </div>
      </#if>
    </div>
    <p style="margin: 8px 0">${t.description}</p>
    <#if t.secrets?has_content>
      <p class="muted" style="margin: 4px 0">Needs: <#list t.secrets as s><code>COMPOSECAST_SECRET_${s.name}</code> (${s.why})<#sep>, </#list></p>
    </#if>

    <div class="tpl-previews">
      <#list t.screenDetails as s>
        <#if phone>
        <button type="button" class="tpl-screen-btn"
          data-tpl-preview="/admin/templates/${t.id?url}/screens/${s.id?url}/phone.png"
          data-tpl-title="${t.name} — ${s.title} (${s.id})"
          title="Click to inspect full-size preview of ${s.id}">
          <img data-theme-thumb="true" src="/admin/templates/${t.id?url}/screens/${s.id?url}/thumb.png" width="110" height="244" loading="lazy" alt="${t.name} ${s.id} preview">
          <span class="tpl-screen-label"><code>${s.id}</code><#if s.isHome> <span class="status status-live">home</span></#if></span>
        </button>
        <#else>
        <div class="tpl-wire">
          <div class="tpl-wire-title">
            <span>${s.title}</span>
            <code>${s.id}</code>
            <#if s.isHome><span class="status status-live">home</span></#if>
          </div>
          <#if s.components?has_content>
          <div class="tpl-chips">
            <#list s.components as comp><span class="tpl-chip">${comp}</span></#list>
          </div>
          </#if>
        </div>
        </#if>
      </#list>
    </div>

    <#if t.error?has_content><p class="error" role="alert">${t.error}</p></#if>
    <#if canEdit>
    <form method="post" action="/admin/templates/${t.id?url}">
      <div class="row">
        <div><label for="prefix-${t.id}">Prefix for screen ids (only if they clash)</label>
          <input id="prefix-${t.id}" name="prefix" pattern="[a-z0-9][a-z0-9_\-]{0,30}" placeholder="e.g. ${t.id}-"></div>
        <div style="flex: 0 0 auto"><label><input type="checkbox" name="brand"<#if empty> checked</#if>> Use its colors</label></div>
        <div style="flex: 0 0 auto"><button type="submit">Use ${t.name}</button></div>
      </div>
    </form>
    </#if>
  </div>
  </#list>

  <#if phone>
  <dialog id="tpl-preview-dialog" class="tpl-dialog">
    <div class="tpl-dialog-head">
      <h3 id="tpl-preview-title">Template preview</h3>
      <button type="button" id="tpl-preview-close" aria-label="Close preview" style="margin: 0; padding: 4px 10px">✕</button>
    </div>
    <div class="tpl-dialog-bar">
      <span class="muted" style="font-size: 12px">Rendered with bundled sample data</span>
      <div class="preview-theme" role="group" aria-label="Preview theme" data-preview-target="tpl-preview-img">
        <button type="button" data-preview-mode="light">Light</button>
        <button type="button" data-preview-mode="dark">Dark</button>
      </div>
    </div>
    <img id="tpl-preview-img" src="" alt="Template screen preview">
  </dialog>
  <script>
    (function () {
      const dialog = document.getElementById("tpl-preview-dialog");
      const titleEl = document.getElementById("tpl-preview-title");
      const imgEl = document.getElementById("tpl-preview-img");
      const closeBtn = document.getElementById("tpl-preview-close");
      if (!dialog) return;
      document.querySelectorAll("button[data-tpl-preview]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          const theme = document.documentElement.dataset.theme === "dark" ? "dark" : "light";
          titleEl.textContent = btn.dataset.tplTitle || "Template preview";
          imgEl.dataset.baseSrc = btn.dataset.tplPreview;
          imgEl.dataset.previewTheme = theme;
          imgEl.src = btn.dataset.tplPreview + "?theme=" + theme;
          dialog.querySelectorAll(".preview-theme button[data-preview-mode]").forEach(function (b) {
            b.setAttribute("aria-pressed", String(b.dataset.previewMode === theme));
          });
          dialog.showModal();
        });
      });
      closeBtn.addEventListener("click", function () { dialog.close(); });
      dialog.addEventListener("click", function (e) { if (e.target === dialog) dialog.close(); });
    })();
  </script>
  </#if>
</main>
</@layout.page>
