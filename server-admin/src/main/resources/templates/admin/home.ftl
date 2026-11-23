<#import "_layout.ftl" as layout>
<@layout.page title="Admin">
<header>
  <strong>compose-cast admin</strong>
  <form method="post" action="/admin/logout">
    <span class="muted">${user.email} (${role})</span>
    <button type="submit">Log out</button>
  </form>
</header>
<main>
  <div class="card">
    <h1>Admin</h1>
    <p class="muted">Screens, publishing and app settings arrive in the next steps (T-007 A3–A5).</p>
  </div>
</main>
</@layout.page>
