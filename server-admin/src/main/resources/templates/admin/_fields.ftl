<#-- T-031 W3: the field picker (HTMX fragment of GET …/visual/fields). A chip inserts its binding
     into the text field that had the focus last (script in visual.ftl). -->
<#if groups?has_content>
  <#list groups as g>
    <h3 class="fields-title">${g.title}</h3>
    <#if g.error?has_content><p class="error">${g.error}</p></#if>
    <div class="chips"><#list g.fields as f><button type="button" class="chip" data-binding="${f.binding}" title="${f.sample}"><code>${f.binding}</code> <span class="muted">${f.sample}</span></button></#list></div>
  </#list>
<#else>
  <p class="muted">This screen has no data sources or parameters yet. Add one under <strong>Data sources</strong> in the tree.</p>
</#if>
