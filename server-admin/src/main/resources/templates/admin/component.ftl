<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide">
  <p><a href="/admin/components">← Components</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span></h1>
  <p class="muted">
    <#if publishedAt?has_content>Live since ${publishedAt} (${publishedBy}).<#else>Never published: screens cannot use it yet.</#if>
    <#if status == "draft"> The editor shows the draft; screens still use the published version.</#if>
  </p>
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
        <h2 style="margin-top: 0">Preview</h2>
        <p class="muted">The saved draft alone, with its sample arguments.</p>
        <img src="/admin/components/${id?url}/phone.png" alt="Phone preview of ${id}" style="width: 100%; border-radius: 12px">
      </div>
      </#if>
      <#if usage?has_content>
      <div class="card" style="margin-top: 16px">
        <h2 style="margin-top: 0">Use on a screen</h2>
        <pre><code>${usage}</code></pre>
      </div>
      </#if>
    </div>
  </div>
  <h2>History</h2>
  <div class="card">
    <table>
      <thead><tr><th>Version</th><th>Published</th><th>By</th><th></th></tr></thead>
      <tbody>
      <#list history as h>
        <tr>
          <td>#${h.revision?c}<#if h?is_first> <span class="status status-published">published</span></#if></td>
          <td>${h.publishedAt}</td>
          <td>${h.publishedBy}</td>
          <td><#if canEdit && !h?is_first>
            <form method="post" action="/admin/components/${id?url}/rollback" style="display:inline;margin:0">
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
</@layout.page>
