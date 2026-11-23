<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Screens</h1>
  <div class="card">
    <table>
      <thead><tr><th>Id</th><th>Title</th><th>Status</th><th>Last published</th></tr></thead>
      <tbody>
      <#list screens as s>
        <tr>
          <td><a href="/admin/screens/${s.id?url}"><code>${s.id}</code></a></td>
          <td>${s.title}</td>
          <td><span class="status status-${s.status}">${s.status}</span></td>
          <td class="muted"><#if s.publishedAt?has_content>${s.publishedAt} · ${s.publishedBy}<#else>never</#if></td>
        </tr>
      <#else>
        <tr><td colspan="4" class="muted">No screens yet.</td></tr>
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
