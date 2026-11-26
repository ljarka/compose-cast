<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Components<@layout.uid "admin:components.header"/></h1>
  <p class="muted">Your own components: draw them in the <strong>designer</strong> (shapes, gradients,
    imported SVG icons, texts, tap areas), or write raw Remote Compose JSON. Use them on screens by id (<code>{"type": "custom", "component": "…"}</code>) or from
    <strong>My components</strong> in the visual editor's palette. This server renders with RC ${rcVersion}.</p>
  <#if error?has_content><p class="error" role="alert">${error}</p></#if>
  <div class="card">
    <table>
      <thead><tr><#if phone><th><span class="visually-hidden">Preview</span></th></#if><th>Id</th><th>Title</th><th>Status</th><th>Version</th><th>Last published</th><th></th></tr></thead>
      <tbody>
      <#list components as c>
        <tr>
          <#if phone><td class="thumb thumb-wide"><a href="/admin/components/${c.id?url}<#if c.designed>/design</#if>" tabindex="-1" aria-hidden="true"><img data-theme-thumb="true" src="/admin/components/${c.id?url}/thumb.png" width="180" loading="lazy" alt=""></a></td></#if>
          <td><a href="/admin/components/${c.id?url}<#if c.designed>/design</#if>"><code>${c.id}</code></a></td>
          <td>${c.title}<#if c.description?has_content><br><span class="muted">${c.description}</span></#if></td>
          <td><span class="status status-${c.status}">${c.status}</span></td>
          <td class="muted">v${c.version?c}<#if c.rcVersion?has_content> · RC ${c.rcVersion}</#if></td>
          <td class="muted"><#if c.publishedAt?has_content><time datetime="${c.publishedAtIso}" title="${c.publishedAtExact}">${c.publishedAt}</time> · ${c.publishedBy}<#else>never</#if></td>
          <td style="text-align: right">
            <#if canEdit>
              <details class="menu">
                <summary aria-label="Actions for ${c.id}">⋯</summary>
                <div class="menu-list">
                  <form method="post" action="/admin/components/${c.id?url}/delete" style="margin:0"><button type="submit" class="danger">Remove</button></form>
                </div>
              </details>
            </#if>
          </td>
        </tr>
      <#else>
        <tr><td colspan="<#if phone>7<#else>6</#if>" class="muted">No components yet.</td></tr>
      </#list>
      </tbody>
    </table>
  </div>
  <#if canEdit>
  <h2>New component<@layout.uid "admin:components.new"/></h2>
  <form class="card" method="post" action="/admin/components">
    <div class="row">
      <div><label for="id">Id</label><input id="id" name="id" required pattern="[a-z0-9][a-z0-9_\-]{0,63}" value="${newId}" placeholder="e.g. promo-tile"></div>
      <div style="flex: 0 0 auto"><button type="submit">Create draft</button></div>
    </div>
  </form>
  </#if>
</main>
</@layout.page>
