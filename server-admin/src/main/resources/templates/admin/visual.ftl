<#import "_layout.ftl" as layout>
<#macro typeSelect name="type" only=[]>
  <select name="${name}" aria-label="Component type">
    <#list palette as g>
      <optgroup label="${g.name}">
        <#list g.types as t><#if only?size == 0 || only?seq_contains(t)><option value="${t}">${t}</option></#if></#list>
      </optgroup>
    </#list>
  </select>
</#macro>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide editor">
  <p><a href="/admin/screens">← Screens</a> · <a href="#json-editor">JSON</a> · <a href="#history">History</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span></h1>
  <p class="muted">
    <#if publishedAt?has_content>Live since ${publishedAt} (${publishedBy}).<#else>Never published: apps do not see this screen.</#if>
    <#if status == "draft"> The editor shows the draft; apps still get the live version.</#if>
  </p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <#if canEdit>
  <div class="toolbar" style="margin-bottom: 16px">
    <button type="submit" form="screen-json-form">Save draft</button>
    <button type="submit" form="screen-json-form" formaction="/admin/screens/${id?url}/publish">Publish</button>
    <button type="submit" form="screen-json-form" formaction="/admin/screens/${id?url}/preview">Preview on phone</button>
    <span class="muted">Publish makes the current JSON live for apps.</span>
  </div>
  </#if>
  <#if preview?has_content>
  <div class="card" id="preview" style="margin-bottom: 16px">
    <h2 style="margin-top: 0">Preview on your phone</h2>
    <div class="row" style="align-items: center">
      <div style="flex: 0 0 auto">${preview.qr?no_esc}</div>
      <div>
        <p>Scan with the phone's camera and tap <strong>Open in compose-cast Preview</strong>. It shows
          the current JSON for 30 minutes; apps do not see it. Nothing was saved.</p>
        <p class="muted">On this phone: <a href="${preview.url}">open the preview</a>.
          No app yet? The page offers the download.</p>
      </div>
    </div>
  </div>
  </#if>
  <div class="split">
    <nav class="card tree" aria-label="Component tree">
      <a href="/admin/screens/${id?url}/visual?node="<#if node == ""> class="selected"</#if>>Screen</a>
      <#list tree as t>
        <a href="/admin/screens/${id?url}/visual?node=${t.path?url}" style="padding-left: ${t.depth * 16 + 8}px"
           class="<#if !t.type?has_content>tree-list</#if><#if t.path == node> selected</#if>"<#if t.path == node> aria-current="true"</#if>><#if t.type?has_content><code>${t.type}</code> <span class="muted">${t.label}</span><#else>${t.label}</#if></a>
      </#list>
    </nav>
    <div class="panel">
      <#if visualProblems?has_content>
        <div class="card"><p class="error" role="alert">Not changed:</p>
          <ul class="problems"><#list visualProblems as p><li><code>${p}</code></li></#list></ul></div>
      </#if>
      <#if canEdit>
      <#if mode == "component">
      <form class="card tools" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}">
        <div class="toolbar" role="group" aria-label="Move and edit" style="margin-bottom: 0">
          <button name="op" value="up"<#if isFirst> disabled</#if> title="Move up">↑ Up</button>
          <button name="op" value="down"<#if isLast> disabled</#if> title="Move down">↓ Down</button>
          <button name="op" value="into-previous"<#if isFirst> disabled</#if> title="Into the container above">→ Into above</button>
          <button name="op" value="out"<#if isTopLevel> disabled</#if> title="Out of its container">← Out</button>
          <button name="op" value="duplicate">Duplicate</button>
          <button name="op" value="delete" class="danger">Delete</button>
        </div>
      </form>
      </#if>
      <form class="card tools" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}">
        <#if mode == "sources">
          <div class="toolbar">
            <label class="inline">Name <input name="name" placeholder="shows" style="width: 140px"></label>
            <label class="inline">Address <input name="url" placeholder="https://api.tvmaze.com/shows" style="width: 320px"></label>
            <button name="op" value="add-source">Add data source</button>
          </div>
          <p class="muted help">Bind to it as <code>{{name.field}}</code>; a key goes into the server's .env and here as <code>{{secret.NAME}}</code>. Or <a href="/admin/wizard">create screens from an API</a>.</p>
        <#elseif mode == "source">
          <div class="toolbar" style="margin-bottom: 0"><button name="op" value="delete-source" class="danger">Delete this data source</button></div>
        <#else>
        <div class="toolbar" style="margin-bottom: 0">
          <label class="inline">Add <@typeSelect/></label>
          <#if mode == "component">
            <button name="op" value="add-after">after this</button>
            <#if isContainer><button name="op" value="add-inside">inside</button></#if>
          <#elseif mode == "list">
            <button name="op" value="add-end">to this list</button>
          <#else>
            <button name="op" value="add-end">to the screen</button>
          </#if>
        </div>
        </#if>
      </form>
      </#if>
      <#if mode != "list" && mode != "sources">
      <form class="card panel-form" method="post" action="/admin/screens/${id?url}/visual?node=${node?url}">
        <h2 style="margin-top: 0">${nodeLabel}</h2>
        <fieldset class="plain"<#if !canEdit> disabled</#if>>
          <#include "_form.ftl">
        </fieldset>
        <#if canEdit><button type="submit">Save</button></#if>
      </form>
      <#if canEdit && mode != "source">
      <div class="card">
        <button type="button" hx-get="/admin/screens/${id?url}/visual/fields?node=${node?url}" hx-target="#data-fields" hx-indicator="#data-fields">Show fields from the data</button>
        <p class="muted help" id="picker-hint">Click a text field above, then a field below: its <code>{{binding}}</code> is inserted there.</p>
        <div id="data-fields"></div>
      </div>
      </#if>
      <#elseif mode == "sources">
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">The APIs this screen reads. Add one above, then select it in the tree to set headers, timeout and caching.</p></div>
      <#else>
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">A list of components. Add one above, or select a component in the tree.</p></div>
      </#if>
    </div>
  </div>

  <h2 id="json-editor">Code</h2>
  <form id="screen-json-form" class="card" method="post" action="/admin/screens/${id?url}">
    <div class="toolbar" role="tablist" aria-label="Code view">
      <button type="button" id="tab-json" class="chip" role="tab" aria-selected="true">JSON</button>
      <button type="button" id="tab-rc" class="chip" role="tab" aria-selected="false"
        hx-post="/admin/screens/${id?url}/describe" hx-include="#json" hx-target="#rc-output">Remote Compose</button>
    </div>
    <div id="json-pane">
      <label for="json">Screen JSON <span class="muted">(<a href="https://github.com/ljarka/compose-cast/blob/main/docs/screen-json-format.md">format</a>)</span></label>
      <textarea id="json" name="json" spellcheck="false"<#if canEdit>
        hx-post="/admin/screens/${id?url}/validate" hx-trigger="input changed delay:400ms" hx-target="#problems"<#else> readonly</#if>>${json}</textarea>
      <div id="problems" aria-live="polite"><#include "_problems.ftl"></div>
    </div>
    <div id="rc-output" hidden></div>
    <#if canEdit>
      <button type="submit">Save draft</button>
      <button type="submit" formaction="/admin/screens/${id?url}/publish">Publish</button>
      <button type="submit" formaction="/admin/screens/${id?url}/preview">Preview on phone</button>
    </#if>
  </form>

  <h2 id="history">History</h2>
  <div class="card">
    <table>
      <thead><tr><th>Version</th><th>Published</th><th>By</th><th></th></tr></thead>
      <tbody>
      <#list history as h>
        <tr>
          <td><a href="/admin/screens/${id?url}/revisions/${h.revision?c}">#${h.revision?c}</a><#if h?is_first> <span class="status status-live">live</span></#if></td>
          <td>${h.publishedAt}</td>
          <td>${h.publishedBy}</td>
          <td><#if canEdit && !h?is_first>
            <form method="post" action="/admin/screens/${id?url}/rollback" style="margin:0">
              <input type="hidden" name="revision" value="${h.revision?c}">
              <button type="submit" style="margin:0">Roll back to this</button>
            </form>
          </#if></td>
        </tr>
      <#else>
        <tr><td colspan="4" class="muted">Never published.</td></tr>
      </#list>
      </tbody>
    </table>
  </div>
</main>
<script>
  // T-031 W3 field picker: remember the last text field of the property form, insert a chip's
  // binding at its cursor. No library (tech stack: small vanilla JS islands).
  (function () {
    let last = null;
    document.addEventListener("focusin", function (e) {
      if (e.target.closest(".panel-form") && e.target.matches("input:not([type=checkbox]):not([type=number]), textarea")) last = e.target;
    });
    document.addEventListener("click", function (e) {
      if (e.target.id === "tab-json" || e.target.id === "tab-rc") {
        const showRc = e.target.id === "tab-rc";
        document.getElementById("json-pane").hidden = showRc;
        document.getElementById("rc-output").hidden = !showRc;
        document.getElementById("tab-json").setAttribute("aria-selected", String(!showRc));
        document.getElementById("tab-rc").setAttribute("aria-selected", String(showRc));
        return;
      }
      const chip = e.target.closest("[data-binding]");
      if (!chip) return;
      if (!last) { document.getElementById("picker-hint").classList.add("error"); return; }
      const text = chip.dataset.binding, start = last.selectionStart ?? last.value.length, end = last.selectionEnd ?? start;
      last.value = last.value.slice(0, start) + text + last.value.slice(end);
      last.focus();
      last.setSelectionRange(start + text.length, start + text.length);
    });
  })();
</script>
</@layout.page>
