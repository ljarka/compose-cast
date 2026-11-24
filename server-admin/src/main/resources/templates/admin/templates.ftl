<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <p><a href="/admin/screens">← Screens</a></p>
  <h1>Start from a template</h1>
  <p class="muted">A template is a whole app: its screens, their data and the navigation between them. Its screens
    are added as <strong>drafts</strong> — nothing changes in the app until you publish.</p>

  <#if applied?has_content>
  <div class="card" id="applied" style="margin-bottom: 16px">
    <h2 style="margin-top: 0">${applied.name}: ${applied.screens?size} drafts added</h2>
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
    <h2 style="margin-top: 0">${t.name}<#if t.live> <span class="status status-live">live</span></#if></h2>
    <p>${t.description}</p>
    <p class="muted">Screens: <#list t.screens as s><code>${s}</code><#sep>, </#list></p>
    <#if t.secrets?has_content>
      <p class="muted">Needs: <#list t.secrets as s><code>COMPOSECAST_SECRET_${s.name}</code> (${s.why})<#sep>, </#list></p>
    </#if>
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
</main>
</@layout.page>
