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
<main class="wide editor<#if phone> with-phone</#if>">
  <p><a href="/admin/screens">← Screens</a> · <a href="#json-editor">JSON</a> · <a href="#history">History</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span></h1>
  <p class="muted">
    <#if publishedAt?has_content>Live since ${publishedAt} (${publishedBy}).<#else>Never published: apps do not see this screen.</#if>
    <#if status == "draft"> The editor shows the draft; apps still get the live version.</#if>
  </p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <#if canEdit>
  <div class="toolbar" style="margin-bottom: 16px">
    <button type="submit" form="screen-json-form" formaction="/admin/screens/${id?url}/publish">Publish</button>
    <button type="submit" form="screen-json-form" formaction="/admin/screens/${id?url}/preview">Preview on phone</button>
    <span class="muted">Publish makes the current draft live for apps.</span>
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
  <div class="split<#if phone> with-phone</#if>">
    <nav class="card tree" aria-label="Component tree">
      <a href="/admin/screens/${id?url}/visual?node="<#if canEdit> data-path="" data-list="true"</#if><#if node == "" || mode == "source" || mode == "value" || mode == "new-source" || mode == "new-value" || mode == "sources" || mode == "values"> class="selected"</#if>>Screen</a>
      <#list tree as t>
        <a href="/admin/screens/${id?url}/visual?node=${t.path?url}" style="padding-left: ${t.depth * 16 + 8}px"
           <#if canEdit><#if t.type?has_content>draggable="true" data-path="${t.path}" data-container="${t.isContainer?c}"<#elseif t.isList>data-path="${t.path}" data-list="true"</#if></#if>
           class="<#if !t.type?has_content>tree-list</#if><#if t.path == node> selected</#if>"<#if t.path == node> aria-current="true"</#if>><#if t.type?has_content><code>${t.type}</code> <span class="muted">${t.label}</span><#else>${t.label}</#if></a>
      </#list>
    </nav>
    <div class="panel">
      <#if visualProblems?has_content>
        <div class="card"><p class="error" role="alert">Not changed:</p>
          <ul class="problems"><#list visualProblems as p><li><code>${p}</code></li></#list></ul></div>
      </#if>
      <#if canEdit && mode != "source" && mode != "value" && mode != "new-source" && mode != "new-value">
      <form class="card tools" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}">
        <#if mode == "sources">
          <div class="toolbar">
            <label class="inline">Name <input name="name" placeholder="shows" style="width: 140px"></label>
            <label class="inline">Address <input name="url" placeholder="https://api.tvmaze.com/shows" style="width: 320px"></label>
            <button name="op" value="add-source">Add data source</button>
          </div>
          <p class="muted help">Bind to it as <code>{{name.field}}</code>; a key goes into the server's .env and here as <code>{{secret.NAME}}</code>. Or <a href="/admin/wizard">create screens from an API</a>.</p>
        <#elseif mode == "values">
          <div class="toolbar">
            <label class="inline">Name <input name="name" placeholder="volume" style="width: 130px"></label>
            <label class="inline">Default <input name="default" value="40" style="width: 80px"></label>
            <label class="inline">Min <input name="min" value="0" style="width: 70px"></label>
            <label class="inline">Max <input name="max" value="100" style="width: 70px"></label>
            <button name="op" value="add-value">Add screen value</button>
          </div>
          <p class="muted help">On-device reactive values: control them with a <code>slider</code> or turn on <strong>Animate</strong> inside the value's form. Read as <code>{{values.name}}</code>.</p>
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
      <#if mode != "list" && mode != "sources" && mode != "values">
      <div class="card">
        <#if mode == "source" || mode == "value" || mode == "new-source" || mode == "new-value">
          <p style="margin-top: 0"><a href="/admin/screens/${id?url}/visual?node=">← Back to Screen</a></p>
        </#if>
        <h2 style="margin-top: 0">${nodeLabel}</h2>
        <#if canEdit && (mode == "component" || mode == "source" || mode == "value")>
        <form class="tools" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}" style="margin-bottom: 12px">
          <#if mode == "component">
            <div class="toolbar" role="group" aria-label="Edit component" style="margin-bottom: 0">
              <button name="op" value="duplicate">Duplicate</button>
              <button name="op" value="delete" class="danger">Delete</button>
            </div>
          <#elseif mode == "source">
            <div class="toolbar" style="margin-bottom: 0"><button name="op" value="delete-source" class="danger">Delete this data source</button></div>
          <#else>
            <div class="toolbar" style="margin-bottom: 0"><button name="op" value="delete-value" class="danger">Delete this value</button></div>
          </#if>
        </form>
        </#if>
        <form class="panel-form" method="post" action="/admin/screens/${id?url}/visual?node=${node?url}">
          <fieldset class="plain"<#if !canEdit> disabled</#if>>
            <#if mode == "new-value" || mode == "new-source">
            <div class="field">
              <label for="f-__name">Name <span class="required" title="required">*</span></label>
              <input id="f-__name" name="__name" value="${newName}" placeholder="<#if mode == 'new-value'>volume<#else>shows</#if>" required>
              <span class="muted help">Letters, digits and _ (starting with a letter).</span>
            </div>
            </#if>
            <#include "_form.ftl">
          </fieldset>
          <#if canEdit && (mode == "new-value" || mode == "new-source")><button type="submit">Add</button></#if>
          <#if canEdit && mode != "new-value" && mode != "new-source"><p class="muted help" id="autosave-status" role="status" style="margin: 12px 0 0; min-height: 1.4em"></p></#if>
        </form>
      </div>
      <#if mode == "screen">
      <div class="card">
        <div class="card-head">
          <h2>Screen values</h2>
          <#if canEdit><a class="btn-link" href="/admin/screens/${id?url}/visual?node=values.__new__">+ Add value</a></#if>
        </div>
        <p class="muted help" style="margin: 0">On-device reactive values for sliders and automatic animations. Read as <code>{{values.name}}</code>.</p>
        <#if screenValues?has_content>
        <div class="item-list">
          <#list screenValues as v>
          <a class="item-row" href="/admin/screens/${id?url}/visual?node=${v.path?url}">
            <span class="item-main">
              <code class="item-name">${v.name}</code>
              <span class="item-badge">${v.badge}</span>
              <span class="item-meta">${v.summary}</span>
            </span>
            <span class="item-chevron" aria-hidden="true">›</span>
          </a>
          </#list>
        </div>
        <#else>
        <p class="muted help" style="margin: 12px 0 0">No screen values yet.</p>
        </#if>
      </div>
      <div class="card">
        <div class="card-head">
          <h2>Data sources</h2>
          <#if canEdit><a class="btn-link" href="/admin/screens/${id?url}/visual?node=data.__new__">+ Add data source</a></#if>
        </div>
        <p class="muted help" style="margin: 0">APIs this screen reads as <code>{{name.field}}</code>. Or <a href="/admin/wizard">create screens from an API</a>.</p>
        <#if sources?has_content>
        <div class="item-list">
          <#list sources as s>
          <a class="item-row" href="/admin/screens/${id?url}/visual?node=${s.path?url}">
            <span class="item-main">
              <code class="item-name">${s.name}</code>
              <span class="item-meta">${s.summary}</span>
            </span>
            <span class="item-chevron" aria-hidden="true">›</span>
          </a>
          </#list>
        </div>
        <#else>
        <p class="muted help" style="margin: 12px 0 0">No data sources yet.</p>
        </#if>
      </div>
      </#if>
      <#if canEdit && mode != "source" && mode != "value" && mode != "new-source" && mode != "new-value">
      <div class="card">
        <button type="button" hx-get="/admin/screens/${id?url}/visual/fields?node=${node?url}" hx-target="#data-fields" hx-indicator="#data-fields">Show fields from the data</button>
        <p class="muted help" id="picker-hint">Click a text field above, then a field below: its <code>{{binding}}</code> is inserted there.</p>
        <div id="data-fields"></div>
      </div>
      </#if>
      <#elseif mode == "sources">
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">The APIs this screen reads. Add one above, then select it in the tree to set headers, timeout and caching.</p></div>
      <#elseif mode == "values">
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">Reactive numbers that live on the phone. Add one above, then select it in the tree to set its range or configure automatic animation (mode &amp; easing dropdowns).</p></div>
      <#else>
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">A list of components. Add one above, or select a component in the tree.</p></div>
      </#if>
    </div>
    <#if phone>
    <aside class="phone" aria-label="Phone preview">
      <img src="/admin/screens/${id?url}/phone.png" width="411" height="914"
        alt="The saved screen as the app draws it on a phone">
      <p class="muted help">Drawn by the app's own player from the saved draft. Updates on every save.</p>
    </aside>
    </#if>
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
            <form method="post" action="/admin/screens/${id?url}/rollback" style="display:inline;margin:0">
              <input type="hidden" name="revision" value="${h.revision?c}">
              <button type="submit" style="margin:0">Roll back to this</button>
            </form>
            <form method="post" action="/admin/screens/${id?url}/revisions/${h.revision?c}/delete" style="display:inline;margin:0">
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
  <#if canEdit>
  <form id="move-form" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}" hidden>
    <input type="hidden" name="op" value="move-to">
    <input type="hidden" name="from" id="move-from">
    <input type="hidden" name="target" id="move-target">
    <input type="hidden" name="position" id="move-position">
  </form>
  </#if>
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
    const tree = document.querySelector(".tree");
    const moveForm = document.getElementById("move-form");
    if (tree && moveForm) {
      let dragged = null;
      let overLink = null;
      let dropPos = null;
      function clearDrop() {
        if (overLink) overLink.classList.remove("drop-before", "drop-after", "drop-inside");
        overLink = null;
        dropPos = null;
      }
      tree.addEventListener("dragstart", function (e) {
        const a = e.target.closest("a[draggable=true]");
        if (!a) return;
        dragged = a.dataset.path;
        a.classList.add("dragging");
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", dragged);
      });
      tree.addEventListener("dragend", function () {
        tree.querySelectorAll(".dragging").forEach(el => el.classList.remove("dragging"));
        dragged = null;
        clearDrop();
      });
      tree.addEventListener("dragover", function (e) {
        if (!dragged) return;
        const a = e.target.closest("a[data-path]");
        if (!a) { clearDrop(); return; }
        const target = a.dataset.path;
        if (target === dragged || target.startsWith(dragged + ".")) { clearDrop(); return; }
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        let pos;
        if (a.dataset.list === "true") {
          pos = "inside";
        } else {
          const rect = a.getBoundingClientRect();
          const y = (e.clientY - rect.top) / rect.height;
          pos = a.dataset.container === "true"
            ? (y < 0.25 ? "before" : y > 0.75 ? "after" : "inside")
            : (y < 0.5 ? "before" : "after");
        }
        if (overLink !== a || dropPos !== pos) {
          clearDrop();
          overLink = a;
          dropPos = pos;
          a.classList.add("drop-" + pos);
        }
      });
      tree.addEventListener("dragleave", function (e) {
        if (!tree.contains(e.relatedTarget)) clearDrop();
      });
      tree.addEventListener("drop", function (e) {
        e.preventDefault();
        if (!dragged || !overLink || !dropPos) return;
        document.getElementById("move-from").value = dragged;
        document.getElementById("move-target").value = overLink.dataset.path;
        document.getElementById("move-position").value = dropPos;
        clearDrop();
        saveScroll();
        moveForm.submit();
      });
    }
    function saveScroll() {
      sessionStorage.setItem("composecast.scrollY", String(window.scrollY));
      sessionStorage.setItem("composecast.scrollPath", location.pathname);
    }
    if (!location.hash && sessionStorage.getItem("composecast.scrollPath") === location.pathname) {
      const y = sessionStorage.getItem("composecast.scrollY");
      sessionStorage.removeItem("composecast.scrollY");
      if (y !== null) window.scrollTo(0, Number(y));
    }
    document.addEventListener("click", function (e) {
      if (e.target.closest(".tree a")) saveScroll();
    });
    document.addEventListener("submit", function (e) {
      if (e.target.closest(".editor")) saveScroll();
    });

    // Auto-save unsaved edits in the visual property form (debounced while editing, plus immediately on navigation/publish).
    const panelForm = document.querySelector(".panel-form");
    const isNewForm = "${mode}" === "new-value" || "${mode}" === "new-source";
    if (panelForm && !isNewForm) {
      let dirty = false;
      let saving = false;
      let timer = null;
      const statusEl = document.getElementById("autosave-status");
      const phoneImg = document.querySelector(".phone img");
      function setStatus(text, isError) {
        if (!statusEl) return;
        statusEl.textContent = text;
        statusEl.className = isError ? "error help" : "muted help";
      }
      function scheduleSave(delayMs) {
        dirty = true;
        setStatus("Unsaved changes…", false);
        clearTimeout(timer);
        timer = setTimeout(() => { flushDirty(false); }, delayMs);
      }
      panelForm.addEventListener("input", () => scheduleSave(600));
      panelForm.addEventListener("change", () => scheduleSave(50));
      document.addEventListener("click", function (e) {
        if (e.target.closest("[data-binding]") && last && panelForm.contains(last)) scheduleSave(200);
      });
      async function flushDirty(submitOnError = true) {
        clearTimeout(timer);
        if (!dirty || saving) return true;
        saving = true;
        setStatus("Saving…", false);
        try {
          const res = await fetch(panelForm.action, {
            method: "POST",
            body: new URLSearchParams(new FormData(panelForm)),
            redirect: "manual",
          });
          if (res.status === 422) {
            saving = false;
            if (submitOnError) {
              panelForm.submit();
            } else {
              setStatus("Invalid value — fix highlighted fields", true);
            }
            return false;
          }
          dirty = false;
          setStatus("Saved", false);
          if (phoneImg) {
            const base = phoneImg.src.split("?")[0];
            phoneImg.src = base + "?t=" + Date.now();
          }
          return true;
        } finally {
          saving = false;
        }
      }
      document.addEventListener("click", async function (e) {
        const link = e.target.closest(".tree a, .item-row, a[href*='/visual?node=']");
        if (link && dirty && !e.metaKey && !e.ctrlKey && !e.shiftKey) {
          e.preventDefault();
          saveScroll();
          if (await flushDirty()) location.href = link.href;
          return;
        }
        const btn = e.target.closest("button[form='screen-json-form'][formaction]");
        if (btn && dirty) {
          e.preventDefault();
          if (await flushDirty()) {
            const f = document.createElement("form");
            f.method = "POST";
            f.action = btn.getAttribute("formaction");
            document.body.appendChild(f);
            f.submit();
          }
        }
      });
      document.addEventListener("submit", async function (e) {
        if (e.target === panelForm) { dirty = false; return; }
        if (dirty && e.target.closest(".tools")) {
          e.preventDefault();
          if (await flushDirty()) e.target.submit();
        }
      });
    }
  })();
</script>
</@layout.page>
