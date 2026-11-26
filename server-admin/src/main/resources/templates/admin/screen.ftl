<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide">
  <p><a href="/admin/screens">← Screens</a> · <a href="/admin/screens/${id?url}/visual">Visual editor</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span><@layout.uid "admin:screen.header"/></h1>
  <p class="muted">
    <#if publishedAt?has_content>Published <time datetime="${publishedAtIso}" title="${publishedAtExact}">${publishedAt}</time> by ${publishedBy}.<#else>Never published: apps do not see this screen.</#if>
    <#if status == "draft"> The editor shows the draft; apps still get the live version.</#if>
  </p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <form class="card" method="post" action="/admin/screens/${id?url}">
    <label for="json">Screen JSON <span class="muted">(<a href="https://github.com/ljarka/compose-cast/blob/main/docs/screen-json-format.md">format</a>)</span></label>
    <textarea id="json" name="json" spellcheck="false"<#if canEdit>
      hx-post="/admin/screens/${id?url}/validate" hx-trigger="input changed delay:400ms" hx-target="#problems"<#else> readonly</#if>>${json}</textarea>
    <div id="problems" aria-live="polite"><#include "_problems.ftl"></div>
    <#if canEdit>
      <button type="submit">Save draft</button>
      <button type="submit" formaction="/admin/screens/${id?url}/publish">Publish</button>
      <button type="submit" formaction="/admin/screens/${id?url}/preview">Preview on phone</button>
      <span class="muted">Publish makes exactly this text live for apps.</span>
    </#if>
  </form>
  <#if preview?has_content>
  <div class="card" id="preview" style="margin-top: 16px">
    <h2 style="margin-top: 0">Preview on your phone<@layout.uid "admin:screen.preview"/></h2>
    <div class="row" style="align-items: center">
      <div style="flex: 0 0 auto">${preview.qr?no_esc}</div>
      <div>
        <p>Scan with the phone's camera and tap <strong>Open in compose-cast Preview</strong>. It shows
          exactly the text above, for 30 minutes; apps do not see it. Nothing was saved.</p>
        <p class="muted">On this phone: <a href="${preview.url}">open the preview</a>.
          No app yet? The page offers the download.</p>
      </div>
    </div>
  </div>
  </#if>
  <h2>History<@layout.uid "admin:screen.history"/></h2>
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
            <form method="post" action="/admin/screens/${id?url}/draft/revert" style="display:inline;margin:0">
              <button type="submit" class="danger" style="margin:0">Revert</button>
            </form>
          </#if></td>
        </tr>
      </#if>
      <#list history as h>
        <tr>
          <td><a href="/admin/screens/${id?url}/revisions/${h.revision?c}">#${h.revision?c}</a><#if h?is_first> <span class="status status-published">published</span></#if></td>
          <td><time datetime="${h.publishedAtIso}" title="${h.publishedAtExact}">${h.publishedAt}</time></td>
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
</main>
</@layout.page>
