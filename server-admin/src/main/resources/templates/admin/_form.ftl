<#import "_layout.ftl" as layout>
<#-- The generated property form (T-029 C2): rows from VisualPages.formRows; also the HTMX fragment
     of POST …/visual/panel. Nested objects are fieldsets ("group" … "end"). -->
<div id="form-fields">
<#list rows as r>
  <#if r.input == "group">
  <details class="group"<#if r.open> open</#if>><summary>${r.label}<@layout.uid "admin:visual.group.${r.path}"/><#if r.help?has_content> <span class="muted help">${r.help}</span></#if></summary><div class="fields">
  <#elseif r.input == "end">
  </div></details>
  <#elseif r.input == "variant">
  <details class="group"<#if r.open> open</#if>><summary>${r.label}<@layout.uid "admin:visual.group.${r.path}"/></summary><div class="fields">
    <div class="field variant-type">
      <label for="f-${r.path}.type">Type<#if !r.optional> <span class="required" title="required">*</span></#if></label>
      <select id="f-${r.path}.type" name="${r.path}.type"<#if canEdit> hx-post="${panelUrl}" hx-trigger="change" hx-target="#form-fields" hx-swap="outerHTML" hx-include="closest form"</#if>>
        <#if r.optional><option value="">none</option></#if>
        <#list r.options as o><option value="${o}"<#if o == r.value> selected</#if>>${o}</option></#list>
      </select>
    </div>
  <#else>
  <div class="field">
    <#if r.input == "toggle">
      <label class="toggle"><input type="checkbox" name="${r.path}"<#if r.value == "on"> checked</#if>> ${r.label}</label>
    <#else>
      <label for="f-${r.path}">${r.label}<#if !r.optional> <span class="required" title="required">*</span></#if></label>
      <#if r.input == "choice">
        <select id="f-${r.path}" name="${r.path}">
          <option value=""><#if r.optional>default<#else>—</#if></option>
          <#list r.options as o><option value="${o}"<#if o == r.value> selected</#if>>${o}</option></#list>
        </select>
      <#elseif r.input == "number">
        <input id="f-${r.path}" name="${r.path}" type="number" step="${r.step}" value="${r.value}">
      <#elseif r.input == "json">
        <textarea id="f-${r.path}" name="${r.path}" rows="${r.lines}" class="json" spellcheck="false">${r.value}</textarea>
      <#else>
        <input id="f-${r.path}" name="${r.path}" value="${r.value}"<#if r.input == "color"> placeholder="#RRGGBB"<#elseif r.input == "dimension"> placeholder="16, fill or wrap"</#if>>
      </#if>
    </#if>
    <#if r.help?has_content><span class="muted help">${r.help}</span></#if>
  </div>
  </#if>
</#list>
</div>
