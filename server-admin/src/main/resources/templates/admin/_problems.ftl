<#-- The validation result under the editor; also the HTMX fragment of POST /admin/screens/{id}/validate. -->
<#if problems?has_content>
  <p class="error" role="alert">${problems?size} problem<#if problems?size != 1>s</#if> — not saved until fixed:</p>
  <ul class="problems"><#list problems as p><li><code>${p}</code></li></#list></ul>
<#else>
  <p class="ok">Valid.</p>
</#if>
<#if warnings?has_content>
  <ul class="problems warning"><#list warnings as w><li>Warning: <code>${w}</code></li></#list></ul>
</#if>
