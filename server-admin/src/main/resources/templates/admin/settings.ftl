<#import "_layout.ftl" as layout>
<#macro color field label value="" optional=false>
  <div>
    <label for="${field}">${label}<#if optional> <span class="muted">(optional)</span></#if></label>
    <input id="${field}" name="${field}" value="${value}" placeholder="<#if optional>built-in default<#else>#RRGGBB</#if>"<#if !isAdmin> readonly</#if>>
  </div>
</#macro>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Settings<@layout.uid "admin:settings.header"/></h1>
  <#if saved><p class="ok" role="status">Saved. Apps get the new brand from their next request.</p></#if>
  <#if problems?has_content>
    <div class="card"><p class="error" role="alert">Not saved:</p>
      <ul class="problems"><#list problems as p><li><code>${p}</code></li></#list></ul></div>
  </#if>
  <#if !isAdmin><p class="muted">Only admins can change settings.</p></#if>
  <form class="card" method="post" action="/admin/settings" enctype="multipart/form-data">
    <h2>App<@layout.uid "admin:settings.app"/></h2>
    <div class="row">
      <div><label for="name">App name</label><input id="name" name="name" required value="${brand.name}"<#if !isAdmin> readonly</#if>></div>
      <div><label for="applicationId">Application id</label><input id="applicationId" name="applicationId" value="${applicationId}" placeholder="com.example.app"<#if !isAdmin> readonly</#if>></div>
      <div><label for="serverUrl">Server URL</label><input id="serverUrl" name="serverUrl" value="${serverUrl}" placeholder="https://app.example.com"<#if !isAdmin> readonly</#if>></div>
    </div>
    <p class="muted">Used when the app is built (export, T-010). The name is also the brand name in <code>/brand</code>.</p>

    <h2>Brand colors<@layout.uid "admin:settings.brand"/></h2>
    <div class="row">
      <@color "primaryColor" "Primary" brand.primaryColor/>
      <@color "secondaryColor" "Secondary" brand.secondaryColor/>
      <@color "backgroundColor" "Background" brand.backgroundColor/>
    </div>
    <div class="row">
      <@color "surfaceColor" "Surface (cards)" brand.surfaceColor true/>
      <@color "textColor" "Text" brand.textColor true/>
      <@color "outlineColor" "Outline (dividers)" brand.outlineColor true/>
    </div>
    <p class="muted">Colors as <code>#RRGGBB</code> or <code>#AARRGGBB</code>. Saving re-renders every screen with them.</p>

    <h2>Logo<@layout.uid "admin:settings.logo"/></h2>
    <#if hasLogo><p><img src="/admin/settings/logo" alt="Current logo" width="96" height="96" style="border-radius: 12px; border: 1px solid var(--line)"></p></#if>
    <#if isAdmin>
      <input type="file" name="logo" accept="image/png,image/jpeg">
      <p class="muted">Square PNG or JPEG, at most 1 MB, 432 px or more recommended (launcher icon).</p>
      <#if hasLogo><label style="font-weight: normal"><input type="checkbox" name="removeLogo" style="width: auto"> Remove the logo</label></#if>
    </#if>

    <h2>Developer<@layout.uid "admin:settings.developer"/></h2>
    <label class="toggle"><input type="checkbox" name="debugMode"<#if debugMode!false> checked</#if><#if !isAdmin> disabled</#if>> Debug mode (show AI reference IDs on headings)</label>
    <p class="muted">Adds a copy button next to each heading and section so you can reference exact parts of the admin panel in AI prompts.</p>
    <#if isAdmin>
      <button type="submit">Save settings</button>
    </#if>
  </form>
</main>
</@layout.page>
