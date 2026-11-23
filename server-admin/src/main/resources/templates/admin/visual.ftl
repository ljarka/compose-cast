<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role htmx=canEdit>
<main class="wide editor">
  <p><a href="/admin/screens">← Screens</a> · <a href="/admin/screens/${id?url}">JSON editor, publish, history</a></p>
  <h1><code>${id}</code> <span class="muted">visual editor</span></h1>
  <p class="muted">Edits are saved as a draft (<#if isDraft>a draft exists<#else>from the live version</#if>);
    publish or preview on a phone from the JSON editor page.</p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <div class="split">
    <nav class="card tree" aria-label="Component tree">
      <a href="/admin/screens/${id?url}/visual?node="<#if node == ""> class="selected"</#if>>Screen</a>
      <#list tree as t>
        <#if t.type?has_content>
          <a href="/admin/screens/${id?url}/visual?node=${t.path?url}" style="padding-left: ${t.depth * 16}px"<#if t.path == node> class="selected" aria-current="true"</#if>><code>${t.type}</code> <span class="muted">${t.label}</span></a>
        <#else>
          <div class="muted tree-list" style="padding-left: ${t.depth * 16}px">${t.label}</div>
        </#if>
      </#list>
    </nav>
    <form class="card panel" method="post" action="/admin/screens/${id?url}/visual?node=${node?url}">
      <h2 style="margin-top: 0">${nodeLabel}</h2>
      <#if problems?has_content>
        <p class="error" role="alert">Not saved:</p>
        <ul class="problems"><#list problems as p><li><code>${p}</code></li></#list></ul>
      </#if>
      <fieldset class="plain"<#if !canEdit> disabled</#if>>
        <#include "_form.ftl">
      </fieldset>
      <#if canEdit><button type="submit">Save</button></#if>
    </form>
  </div>
</main>
</@layout.page>
