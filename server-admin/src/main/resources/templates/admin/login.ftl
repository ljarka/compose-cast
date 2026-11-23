<#import "_layout.ftl" as layout>
<@layout.page title="Log in">
<main>
  <form class="card" method="post" action="/admin/login">
    <h1>compose-cast admin</h1>
    <#if error?has_content><p class="error" role="alert">${error}</p></#if>
    <label for="email">Email</label>
    <input id="email" name="email" type="email" autocomplete="username" required value="${email}" autofocus>
    <label for="password">Password</label>
    <input id="password" name="password" type="password" autocomplete="current-password" required>
    <button type="submit">Log in</button>
  </form>
</main>
</@layout.page>
