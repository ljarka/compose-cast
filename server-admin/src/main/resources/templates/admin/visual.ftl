<#import "_layout.ftl" as layout>
<#macro typeSelect name="type" only=[]>
  <select name="${name}" aria-label="Component type">
    <#list palette as g>
      <optgroup label="${g.name}">
        <#list g.types as t><#if only?size == 0 || only?seq_contains(t)><option value="${t}">${t}</option></#if></#list>
      </optgroup>
    </#list>
  </select>
</#macro>
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
        <a href="/admin/screens/${id?url}/visual?node=${t.path?url}" style="padding-left: ${t.depth * 16 + 8}px"
           class="<#if !t.type?has_content>tree-list</#if><#if t.path == node> selected</#if>"<#if t.path == node> aria-current="true"</#if>><#if t.type?has_content><code>${t.type}</code> <span class="muted">${t.label}</span><#else>${t.label}</#if></a>
      </#list>
    </nav>
    <div class="panel">
      <#if problems?has_content>
        <div class="card"><p class="error" role="alert">Not changed:</p>
          <ul class="problems"><#list problems as p><li><code>${p}</code></li></#list></ul></div>
      </#if>
      <#if canEdit>
      <form class="card tools" method="post" action="/admin/screens/${id?url}/visual/edit?node=${node?url}">
        <#if mode == "component">
          <div class="toolbar" role="group" aria-label="Move and edit">
            <button name="op" value="up"<#if isFirst> disabled</#if> title="Move up">↑ Up</button>
            <button name="op" value="down"<#if isLast> disabled</#if> title="Move down">↓ Down</button>
            <button name="op" value="into-previous"<#if isFirst> disabled</#if> title="Into the container above">→ Into above</button>
            <button name="op" value="out"<#if isTopLevel> disabled</#if> title="Out of its container">← Out</button>
            <button name="op" value="duplicate">Duplicate</button>
            <button name="op" value="delete" class="danger">Delete</button>
          </div>
        </#if>
        <div class="toolbar">
          <label class="inline">Add <@typeSelect/></label>
          <#if mode == "component">
            <button name="op" value="add-after">after this</button>
            <#if isContainer><button name="op" value="add-inside">inside</button></#if>
          <#elseif mode == "list">
            <button name="op" value="add-end">to this list</button>
          <#else>
            <button name="op" value="add-end">to the screen</button>
          </#if>
        </div>
        <#if mode == "component">
          <div class="toolbar">
            <label class="inline">Wrap in <@typeSelect name="type" only=containers/></label>
            <button name="op" value="wrap">Wrap</button>
          </div>
        </#if>
      </form>
      </#if>
      <#if mode != "list">
      <form class="card" method="post" action="/admin/screens/${id?url}/visual?node=${node?url}">
        <h2 style="margin-top: 0">${nodeLabel}</h2>
        <fieldset class="plain"<#if !canEdit> disabled</#if>>
          <#include "_form.ftl">
        </fieldset>
        <#if canEdit><button type="submit">Save</button></#if>
      </form>
      <#else>
      <div class="card"><h2 style="margin-top: 0">${nodeLabel}</h2>
        <p class="muted">A list of components. Add one above, or select a component in the tree.</p></div>
      </#if>
    </div>
  </div>
</main>
</@layout.page>
