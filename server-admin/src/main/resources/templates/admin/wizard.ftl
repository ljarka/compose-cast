<#import "_layout.ftl" as layout>
<#macro fieldSelect name value optional=true>
  <select id="${name}" name="${name}">
    <#if optional><option value="">none</option></#if>
    <#list fieldPaths as f><option value="${f}"<#if f == value> selected</#if>>${f}</option></#list>
  </select>
</#macro>
<@layout.page title=title user=user role=role>
<main class="wide">
  <p><a href="/admin/screens">← Screens</a></p>
  <h1>Create screens from an API<@layout.uid "admin:wizard.header"/></h1>
  <p class="muted">Paste the address of a JSON API. The server fetches it, finds the list in the answer and
    creates a list screen and a detail screen you can then change in the visual editor.</p>
  <#if problems?has_content>
    <div class="card"><p class="error" role="alert">Not created:</p>
      <ul class="problems"><#list problems as p><li>${p}</li></#list></ul></div>
  </#if>
  <form class="card" method="post" action="/admin/wizard">
    <label for="url">API address</label>
    <input id="url" name="url" value="${url}" placeholder="https://api.tvmaze.com/shows" required>
    <details<#if headerName?has_content> open</#if>><summary class="muted">Header (e.g. an API key)</summary>
      <div class="row">
        <div><label for="headerName">Name</label><input id="headerName" name="headerName" value="${headerName}" placeholder="X-Api-Key"></div>
        <div><label for="headerValue">Value</label><input id="headerValue" name="headerValue" value="${headerValue}" placeholder="{{secret.MYKEY}}"></div>
      </div>
      <p class="muted help">Keys never go into screens: set <code>COMPOSECAST_SECRET_MYKEY=…</code> in the server's .env and write <code>{{secret.MYKEY}}</code> here.</p>
    </details>
    <#if !probed><button type="submit" name="step" value="probe">Look at the API</button></#if>

    <#if probed>
      <p class="ok">The API answered HTTP ${status} in ${millis} ms.</p>
      <input type="hidden" name="previousList" value="${list}">
      <label for="list">The list to show</label>
      <select id="list" name="list" onchange="this.form.querySelector('[name=step][value=probe]').click()">
        <#list lists as l><option value="${l.path}"<#if l.path == list> selected</#if>>${l.label} — ${l.size} items</option></#list>
      </select>

      <h2>Fields of one item<@layout.uid "admin:wizard.fields"/></h2>
      <table>
        <thead><tr><th>Field</th><th>Kind</th><th>Example</th></tr></thead>
        <tbody><#list fields as f><tr><td><code>${f.path}</code></td><td class="muted">${f.kind}</td><td>${f.sample}</td></tr></#list></tbody>
      </table>

      <h2>What goes where<@layout.uid "admin:wizard.mapping"/></h2>
      <div class="row">
        <div><label for="title">Title <span class="required">*</span></label><@fieldSelect "title" plan.title false/></div>
        <div><label for="subtitle">Subtitle</label><@fieldSelect "subtitle" plan.subtitle/></div>
        <div><label for="image">Image</label><@fieldSelect "image" plan.image/></div>
        <div><label for="id">Id (opens the detail)</label><@fieldSelect "id" plan.id/></div>
      </div>

      <h2>Screens<@layout.uid "admin:wizard.screens"/></h2>
      <div class="row">
        <div><label for="listScreen">List screen id</label><input id="listScreen" name="listScreen" value="${plan.listScreen}"></div>
        <div><label for="listTitle">List title</label><input id="listTitle" name="listTitle" value="${plan.listTitle}"></div>
        <div><label for="detailScreen">Detail screen id</label><input id="detailScreen" name="detailScreen" value="${plan.detailScreen}"></div>
        <div><label for="source">Data source name</label><input id="source" name="source" value="${plan.source}"></div>
      </div>
      <fieldset class="plain">
        <legend><strong>The detail screen gets its data</strong></legend>
        <label style="font-weight: normal"><input type="radio" name="detailMode" value="url" style="width: auto"<#if plan.detailMode == "url"> checked</#if>>
          from its own address:</label>
        <input name="detailUrl" value="${plan.detailUrl}" aria-label="Detail address">
        <label style="font-weight: normal"><input type="radio" name="detailMode" value="params" style="width: auto"<#if plan.detailMode == "params"> checked</#if>>
          from the list (the API has no address per item)</label>
      </fieldset>
      <button type="submit" name="step" value="probe">Look again</button>
      <button type="submit" name="step" value="create">Create the screens</button>
      <p class="muted">Both screens are drafts: preview them on a phone and publish them from their pages.
        Lists show at most 20 items (images are embedded in the screen).</p>
    </#if>
  </form>
</main>
</@layout.page>
