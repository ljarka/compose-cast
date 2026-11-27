<#import "_layout.ftl" as layout>
<#-- T-057 component designer (ADR 0034): an SVG artboard edited by designer.js; every change is saved as the draft (debounced) and checked by the server like the JSON editor. -->
<@layout.page title=title user=user role=role>
<style>
  .designer { display: grid; grid-template-columns: 220px minmax(0, 1fr) 280px; gap: 16px; align-items: start;
    max-width: 1500px; margin: 16px auto; padding: 0 16px; }
  .designer .card { padding: 12px; }
  .designer h2 { font-size: 14px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); margin: 0 0 8px; }
  .tools { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; align-items: center; }
  .tools button { margin: 0; padding: 6px 10px; background: var(--card); color: var(--fg); border: 1px solid var(--line); }
  .tools button.active { background: var(--accent); color: #fff; border-color: var(--accent); }
  .tools .sep { width: 1px; height: 24px; background: var(--line); margin: 0 4px; }
  .stage { background: repeating-conic-gradient(var(--line) 0 25%, transparent 0 50%) 0 0 / 16px 16px;
    border: 1px solid var(--line); border-radius: 8px; padding: 32px; overflow: auto; }
  .stage svg { display: block; margin: 0 auto; overflow: visible; user-select: none; touch-action: none; }
  .layers { list-style: none; margin: 0; padding: 0; max-height: 50vh; overflow: auto; }
  .layers li { display: flex; gap: 6px; align-items: center; padding: 4px 6px; border-radius: 6px; cursor: pointer; font-size: 14px; }
  .layers li.selected { outline: 2px solid var(--accent); background: var(--bg); }
  .layers li .name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .layers li.hidden .name { opacity: .45; text-decoration: line-through; }
  .layer-actions button { margin: 0; padding: 4px 10px; background: var(--card); color: var(--fg); border: 1px solid var(--line); }
  .layers button, .props button.small { margin: 0; padding: 0 6px; font-size: 13px; background: transparent; color: var(--accent); border: 1px solid var(--line); }
  .layer-actions { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
  .props label { margin: 8px 0 2px; font-size: 13px; }
  .props input, .props select, .props textarea { width: 100%; padding: 4px 6px; font: 14px system-ui, sans-serif; color: inherit;
    background: var(--bg); border: 1px solid var(--line); border-radius: 6px; }
  .props textarea { min-height: 70px; font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
  .props .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 8px; }
  .props .color { display: flex; gap: 4px; }
  .props .color input[type=color] { width: 36px; flex: 0 0 36px; padding: 0; height: 30px; }
  .props .check { display: flex; gap: 6px; align-items: center; margin-top: 8px; font-size: 14px; }
  .props .check input { width: auto; }
  .props .hint { font-size: 12px; color: var(--muted); margin: 2px 0 0; }
  .param { border: 1px solid var(--line); border-radius: 6px; padding: 6px; margin-bottom: 6px; }
  #status { font-size: 14px; min-height: 1.5em; }
  #problems li { color: var(--error); font-size: 14px; }
  .phone-preview img { width: 100%; border-radius: 12px; border: 1px solid var(--line); }
  @media (max-width: 1100px) { .designer { grid-template-columns: 1fr; } }
  .designer-code { margin-top: 24px; }
  .designer-code textarea { min-height: 420px; }
  .designer-code pre { margin: 0; max-height: 70vh; overflow: auto; font: 13px/1.45 ui-monospace, SFMono-Regular, Menlo, monospace; }
</style>
<div style="max-width: 1500px; margin: 16px auto 0; padding: 0 16px">
  <p style="margin: 0"><a href="/admin/components">← Components</a> · <a href="#ai">AI</a> · <a href="#code">Code</a> · <a href="#history">History</a> · <a href="/admin/components/${id?url}">JSON editor</a></p>
  <h1 style="margin: 8px 0"><code>${id}</code> <span class="status status-${status}">${status}</span> <span class="muted" style="font-size: 16px">Designer</span><@layout.uid "admin:designer.header"/></h1>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
</div>
<div class="designer" id="designer" data-id="${id}" data-can-edit="${canEdit?c}">
  <aside>
    <div class="card">
      <h2>Layers<@layout.uid "admin:designer.layers"/></h2>
      <ul class="layers" id="layers"></ul>
      <div class="layer-actions">
        <button type="button" data-cmd="up" title="Bring forward">▲</button>
        <button type="button" data-cmd="down" title="Send backward">▼</button>
        <button type="button" data-cmd="duplicate" title="Duplicate (Ctrl+D)">⧉</button>
        <button type="button" data-cmd="delete" title="Delete (Del)">✕</button>
      </div>
    </div>
    <div class="card" style="margin-top: 16px">
      <h2>Component<@layout.uid "admin:designer.component"/></h2>
      <div class="props" id="component-props"></div>
    </div>
  </aside>
  <section>
    <#if canEdit>
    <div class="tools" id="size-presets" role="group" aria-label="Component size presets" style="margin-bottom: 8px">
      <span class="muted" style="font-size: 13px; margin-right: 4px">Size:</span>
      <button type="button" data-preset-w="360" data-preset-h="120" data-preset-fill="1" title="Full width banner (360×120 dp)">▬ Full width</button>
      <button type="button" data-preset-w="360" data-preset-h="220" data-preset-fill="1" title="Full width large widget (360×220 dp)">▭ Full tall</button>
      <button type="button" data-preset-w="172" data-preset-h="120" data-preset-fill="0.5" title="Half width widget (172×120 dp)">◧ Half width</button>
      <button type="button" data-preset-w="172" data-preset-h="172" data-preset-fill="0.5" title="Square half-width widget (172×172 dp)">▢ Square</button>
      <button type="button" data-preset-w="172" data-preset-h="260" data-preset-fill="0.5" title="Vertical tall widget (172×260 dp)">▮ Vertical</button>
    </div>
    </#if>
    <div class="tools" id="tools">
      <button type="button" data-tool="select" title="Select and move (V)">↖ Select</button>
      <button type="button" data-tool="rect" title="Rectangle (R)">▭ Rect</button>
      <button type="button" data-tool="ellipse" title="Ellipse (O)">◯ Ellipse</button>
      <button type="button" data-tool="line" title="Line (L)">╱ Line</button>
      <button type="button" data-tool="pen" title="Pen (P): click for a corner, press and drag for a curve (Alt: break the handles); click the first point to close, Enter or Esc to finish, Backspace removes the last point">✎ Pen</button>
      <button type="button" data-tool="text" title="Text (T)">T Text</button>
      <button type="button" data-tool="tap" title="Tap area: an invisible area that runs an action (A)">☝ Tap area</button>
      <span class="sep"></span>
      <button type="button" data-cmd="import-svg" title="Paste an SVG icon (path elements)">⇪ SVG</button>
      <button type="button" data-cmd="undo" title="Undo (Ctrl+Z)">↶</button>
      <button type="button" data-cmd="redo" title="Redo (Ctrl+Shift+Z)">↷</button>
      <span class="sep"></span>
      <label class="muted" style="margin: 0; font-weight: 400">Zoom <select id="zoom"><option value="1">100%</option><option value="1.5">150%</option><option value="2" selected>200%</option><option value="3">300%</option></select></label>
    </div>
    <div class="stage" id="stage"></div>
    <div id="status" aria-live="polite"></div>
    <ul id="problems" aria-live="polite"></ul>
    <p class="muted" style="font-size: 13px">Sizes are dp. The component fills the screen width; on wider or narrower phones each element keeps its <em>anchor</em>. Texts and colors can use <code>{{params.name}}</code> or <code>{{brand.primaryColor}}</code>. Every change is saved as the draft; screens use the component after <strong>Publish</strong>.</p>
    <#if canEdit><form method="post" action="/admin/components/${id?url}/publish" id="publish-form"><input type="hidden" name="from" value="design"><input type="hidden" name="json" id="publish-json"><button type="submit">Publish</button></form></#if>
<#assign aiMode = "design"><#assign aiUid = "admin:designer.ai"><#include "_ai_chat.ftl">
<div class="designer-code">
  <h2 id="code">Code<@layout.uid "admin:designer.code"/></h2>
  <div class="card">
    <div class="toolbar" role="tablist" aria-label="Code view">
      <button type="button" id="tab-json" class="chip" role="tab" aria-selected="true">JSON</button>
      <button type="button" id="tab-rc" class="chip" role="tab" aria-selected="false">Remote Compose</button>
    </div>
    <div id="code-json-pane">
      <label for="code-json">Component JSON <span class="muted">(<a href="https://github.com/ljarka/compose-cast/blob/main/docs/screen-json-format.md#custom-components-t-049-adr-0033">format</a>; follows the artboard, and edits here redraw it)</span></label>
      <textarea id="code-json" spellcheck="false"<#if !canEdit> readonly</#if>></textarea>
      <p id="code-status" class="muted" aria-live="polite"></p>
    </div>
    <div id="code-rc-pane" hidden>
      <p class="muted">The Remote Compose JSON the server compiled from the last saved design (read only).</p>
      <pre><code id="code-rc"></code></pre>
    </div>
  </div>
</div>
<div class="designer-code">
  <h2 id="history">History<@layout.uid "admin:designer.history"/></h2>
  <div class="card">
    <table>
      <thead><tr><th>Version</th><th>Published</th><th>By</th><th></th></tr></thead>
      <tbody>
      <#if hasDraft!false>
        <tr>
          <td>Draft <span class="status status-draft">draft</span></td>
          <td><time datetime="${draftUpdatedAtIso}" title="${draftUpdatedAtExact}">${draftUpdatedAt}</time></td>
          <td class="muted">unpublished</td>
          <td><#if canEdit>
            <form method="post" action="/admin/components/${id?url}/draft/revert" style="display:inline;margin:0">
              <input type="hidden" name="from" value="design">
              <button type="submit" class="danger" style="margin:0">Revert</button>
            </form>
          </#if></td>
        </tr>
      </#if>
      <#list history as h>
        <tr>
          <td><a href="/admin/components/${id?url}/revisions/${h.revision?c}">#${h.revision?c}</a><#if h?is_first> <span class="status status-published">published</span></#if></td>
          <td><time datetime="${h.publishedAtIso}" title="${h.publishedAtExact}">${h.publishedAt}</time></td>
          <td>${h.publishedBy}</td>
          <td><#if canEdit && !h?is_first>
            <form method="post" action="/admin/components/${id?url}/rollback" style="display:inline;margin:0">
              <input type="hidden" name="revision" value="${h.revision?c}">
              <input type="hidden" name="from" value="design">
              <button type="submit" style="margin:0">Roll back to this</button>
            </form>
            <form method="post" action="/admin/components/${id?url}/revisions/${h.revision?c}/delete" style="display:inline;margin:0">
              <input type="hidden" name="from" value="design">
              <button type="submit" class="danger" style="margin:0">Remove</button>
            </form>
          </#if></td>
        </tr>
      <#else>
        <tr><td colspan="4" class="muted">Never published.</td></tr>
      </#list>
      </tbody>
    </table>
  </div>
</div>
  </section>
  <aside>
    <div class="card">
      <h2>Properties<@layout.uid "admin:designer.properties"/></h2>
      <div class="props" id="props"><p class="muted">Select a layer.</p></div>
    </div>
    <#if phoneImage>
    <div class="card phone-preview" style="margin-top: 16px">
      <h2>On the phone<@layout.uid "admin:designer.phone"/></h2>
      <@layout.previewTheme "#phone"/>
      <img id="phone" src="/admin/components/${id?url}/phone.png" alt="The saved draft drawn by the app's player">
      <p class="hint muted" style="font-size: 12px">Drawn by the app's own player after each save.</p>
    </div>
    </#if>
  </aside>
</div>

<script type="application/json" id="definition">${json?no_esc}</script>
<script type="application/json" id="brand">${brand?no_esc}</script>
<script src="/admin/components/designer.js"></script>
<#if canEdit && (hasAi!false)><script src="/admin/components/component-ai.js"></script></#if>
</@layout.page>
