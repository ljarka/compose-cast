<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide">
  <p><a href="/admin/screens">← Screens</a></p>
  <h1><code>${id}</code> <span class="status status-${status}">${status}</span></h1>
  <p class="muted">
    <#if publishedAt?has_content>Live since ${publishedAt} (${publishedBy}).<#else>Never published: apps do not see this screen.</#if>
    <#if status == "draft"> The editor shows the draft; apps still get the live version.</#if>
  </p>
  <#if saved><p class="ok" role="status">Draft saved (as formatted JSON). Apps still get the live version.</p></#if>
  <form class="card" method="post" action="/admin/screens/${id?url}">
    <label for="json">Screen JSON <span class="muted">(<a href="https://github.com/ljarka/compose-cast/blob/main/docs/screen-json-format.md">format</a>)</span></label>
    <textarea id="json" name="json" spellcheck="false"<#if canEdit>
      hx-post="/admin/screens/${id?url}/validate" hx-trigger="input changed delay:400ms" hx-target="#problems"<#else> readonly</#if>>${json}</textarea>
    <div id="problems" aria-live="polite"><#include "_problems.ftl"></div>
    <#if canEdit><button type="submit">Save draft</button></#if>
  </form>
</main>
</@layout.page>
