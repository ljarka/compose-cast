<#if error?has_content>
  <p class="error" role="alert">${error}</p>
<#else>
  <pre style="overflow:auto; max-height:50vh; padding:12px; background:var(--bg); border:1px solid var(--line); border-radius:6px; margin-top:8px"><code>${output}</code></pre>
</#if>
