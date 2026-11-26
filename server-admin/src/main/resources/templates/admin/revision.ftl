<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <p><a href="/admin/screens/${id?url}">← <code>${id}</code></a></p>
  <h1><code>${id}</code> version #${revision?c}</h1>
  <p class="muted">Published <time datetime="${publishedAtIso}" title="${publishedAtExact}">${publishedAt}</time> by ${publishedBy}. Read-only; roll back to it from the screen's history.</p>
  <div class="card"><textarea readonly spellcheck="false">${json}</textarea></div>
</main>
</@layout.page>
