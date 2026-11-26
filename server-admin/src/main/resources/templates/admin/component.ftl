<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide">
  <p><a href="/admin/components">← Components</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span><@layout.uid "admin:component.header"/></h1>
  <p class="muted">
    <#if publishedAt?has_content>Published <time datetime="${publishedAtIso}" title="${publishedAtExact}">${publishedAt}</time> by ${publishedBy}.<#else>Never published: screens cannot use it yet.</#if>
    <#if status == "draft"> The editor shows the draft; screens still use the published version.</#if>
  </p>
  <p><#if designed><a href="/admin/components/${id?url}/design"><strong>Open in the designer →</strong></a> <span class="muted">This component is drawn in the designer: its body is compiled from <code>design</code>, edit that or use the designer.</span><#else><span class="muted">Hand-written Remote Compose JSON. The <a href="/admin/components/${id?url}/design">designer</a> can replace it with a drawing.</span></#if></p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <div class="row" style="align-items: flex-start">
    <form class="card" method="post" action="/admin/components/${id?url}" style="flex: 2 1 480px">
      <label for="json">Component JSON <span class="muted">(<a href="https://github.com/ljarka/compose-cast/blob/main/docs/screen-json-format.md#custom-components-t-049-adr-0033">format</a>; body = Remote Compose JSON, RC ${rcVersion})</span></label>
      <textarea id="json" name="json" spellcheck="false" style="min-height: 420px"<#if canEdit>
        hx-post="/admin/components/${id?url}/validate" hx-trigger="input changed delay:400ms" hx-target="#problems"<#else> readonly</#if>>${json}</textarea>
      <div id="problems" aria-live="polite"><#include "_problems.ftl"></div>
      <#if canEdit>
        <button type="submit">Save draft</button>
        <button type="submit" formaction="/admin/components/${id?url}/publish">Publish</button>
        <span class="muted">Publishing checks every live screen that uses it.</span>
      </#if>
    </form>
    <div style="flex: 1 1 300px">
      <#if phoneImage>
      <div class="card">
        <h2 style="margin-top: 0">Preview<@layout.uid "admin:component.preview"/></h2>
        <@layout.previewTheme "#component-phone-img"/>
        <p class="muted">The saved draft alone, with its sample arguments.</p>
        <img id="component-phone-img" src="/admin/components/${id?url}/phone.png" alt="Phone preview of ${id}" style="width: 100%; border-radius: 12px">
      </div>
      </#if>
      <#if usage?has_content>
      <div class="card" style="margin-top: 16px">
        <h2 style="margin-top: 0">Use on a screen<@layout.uid "admin:component.usage"/></h2>
        <pre><code>${usage}</code></pre>
      </div>
      </#if>
    </div>
  </div>
  <h2>History<@layout.uid "admin:component.history"/></h2>
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
              <button type="submit" style="margin:0">Roll back to this</button>
            </form>
            <form method="post" action="/admin/components/${id?url}/revisions/${h.revision?c}/delete" style="display:inline;margin:0">
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
