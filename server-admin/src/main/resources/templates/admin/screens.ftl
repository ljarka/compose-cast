<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Screens</h1>
  <#if canEdit>
  <p><a href="/admin/templates">Start from a template…</a> <span class="muted">— a whole app (news, shop, events, weather) in one click</span></p>
  <p><a href="/admin/wizard">Create screens from an API…</a> <span class="muted">— paste a JSON API's address, get a list and a detail screen</span></p>
  </#if>
  <#if homeError??><p class="error" role="alert">${homeError}</p></#if>
  <div class="card">
    <table>
      <thead><tr><th>Id</th><th>Title</th><th>Status</th><th>Last published</th><th></th></tr></thead>
      <tbody>
      <#list screens as s>
        <tr>
          <td><a href="/admin/screens/${s.id?url}"><code>${s.id}</code></a><#if s.isHome> <span class="status status-live">home</span></#if></td>
          <td>${s.title}</td>
          <td><span class="status status-${s.status}">${s.status}</span><#if s.liveEveryMs gt 0> <span class="live-badge" title="Live updates every ${s.liveEveryMs?c} ms"><span class="live-dot" style="animation-duration: ${s.liveEveryMs?c}ms"></span>live</span></#if></td>
          <td class="muted"><#if s.publishedAt?has_content>${s.publishedAt} · ${s.publishedBy}<#else>never</#if></td>
          <td style="text-align: right">
            <#if canEdit && !s.isHome>
              <details class="menu">
                <summary aria-label="Actions for ${s.id}">⋯</summary>
                <div class="menu-list">
                  <#if s.isLive>
                    <form method="post" action="/admin/screens/${s.id?url}/home" style="margin:0"><button type="submit">Set as home</button></form>
                  </#if>
                  <form method="post" action="/admin/screens/${s.id?url}/delete" style="margin:0"><button type="submit" class="danger">Remove</button></form>
                </div>
              </details>
            </#if>
          </td>
        </tr>
      <#else>
        <tr><td colspan="5" class="muted">No screens yet.</td></tr>
      </#list>
      </tbody>
    </table>
  </div>
  <#if canEdit>
  <h2>New screen</h2>
  <form class="card" method="post" action="/admin/screens">
    <#if error??><p class="error" role="alert">${error}</p></#if>
    <div class="row">
      <div><label for="id">Id</label><input id="id" name="id" required pattern="[a-z0-9][a-z0-9_\-]{0,63}" value="${newId!""}" placeholder="e.g. settings"></div>
      <div><label for="title">Title</label><input id="title" name="title" value="${newTitle!""}" placeholder="shown in the app bar"></div>
      <div style="flex: 0 0 auto"><button type="submit">Create draft</button></div>
    </div>
  </form>
  </#if>
</main>
</@layout.page>
