<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Screens<@layout.uid "admin:screens.header"/></h1>
  <#if canEdit>
  <div class="action-cards">
    <a class="action-card" href="/admin/templates">
      <span class="action-icon" aria-hidden="true">🧩</span>
      <span class="action-body">
        <span class="action-title">Start from a template <span class="action-arrow" aria-hidden="true">→</span></span>
        <span class="action-desc">A whole app (news, shop, events, weather) in one click.</span>
      </span>
    </a>
    <a class="action-card" href="/admin/wizard">
      <span class="action-icon" aria-hidden="true">⚡</span>
      <span class="action-body">
        <span class="action-title">Create screens from an API <span class="action-arrow" aria-hidden="true">→</span></span>
        <span class="action-desc">Paste a JSON API's address to generate a list and detail screen.</span>
      </span>
    </a>
  </div>
  </#if>
  <#if homeError??><p class="error" role="alert">${homeError}</p></#if>
  <div class="screen-grid">
    <#list screens as s>
    <article class="screen-card<#if !phone> no-thumb</#if>">
      <#if phone>
      <a class="screen-thumb" href="/admin/screens/${s.id?url}" tabindex="-1" aria-hidden="true">
        <img data-theme-thumb="true" src="/admin/screens/${s.id?url}/thumb.png" width="90" height="200" loading="lazy" alt="">
      </a>
      </#if>
      <div class="screen-info">
        <a class="screen-title" href="/admin/screens/${s.id?url}">${s.title}</a>
        <code class="screen-id">${s.id}</code>
        <div class="screen-badges">
          <span class="status status-${s.status}">${s.status}</span>
          <#if s.isHome><span class="status status-live">home</span></#if>
          <#if s.liveEveryMs gt 0><span class="live-badge" title="Live updates every ${s.liveEveryMs?c} ms"><span class="live-dot" style="animation-duration: ${s.liveEveryMs?c}ms"></span>live</span></#if>
        </div>
        <p class="screen-meta"><#if s.publishedAt?has_content>Published <time datetime="${s.publishedAtIso}" title="${s.publishedAtExact}">${s.publishedAt}</time><br>by ${s.publishedBy}<#else>Never published</#if></p>
      </div>
      <#if canEdit && !s.isHome>
        <details class="menu screen-menu">
          <summary aria-label="Actions for ${s.id}">⋯</summary>
          <div class="menu-list">
            <#if s.isLive>
              <form method="post" action="/admin/screens/${s.id?url}/home" style="margin:0"><button type="submit">Set as home</button></form>
            </#if>
            <form method="post" action="/admin/screens/${s.id?url}/delete" style="margin:0"><button type="submit" class="danger">Remove</button></form>
          </div>
        </details>
      </#if>
    </article>
    <#else>
    <p class="muted">No screens yet.</p>
    </#list>
  </div>
  <#if canEdit>
  <h2>New screen<@layout.uid "admin:screens.new"/></h2>
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
