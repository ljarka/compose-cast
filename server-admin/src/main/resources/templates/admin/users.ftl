<#import "_layout.ftl" as layout>
<@layout.page title=title user=user role=role>
<main class="wide">
  <h1>Users<@layout.uid "admin:users.header"/></h1>
  <p class="muted">Roles: <strong>viewer</strong> reads, <strong>editor</strong> edits and publishes screens, <strong>admin</strong> manages everything including accounts and settings.</p>
  <#if notice?has_content><p class="ok" role="status">${notice}</p></#if>
  <#if error?has_content><p class="error" role="alert">${error}</p></#if>

  <h2>Accounts<@layout.uid "admin:users.list"/></h2>
  <div class="card">
    <table>
      <thead><tr><th>Email</th><th>Role</th><th>Last login</th><th>Actions</th></tr></thead>
      <tbody>
      <#list users as u>
        <tr>
          <td>
            <code>${u.email}</code>
            <#if u.isSelf> <span class="status">you</span></#if>
          </td>
          <td>
            <form method="post" action="/admin/users/${u.id?c}/role" class="toolbar" style="margin: 0">
              <select name="role" aria-label="Role for ${u.email}"<#if u.isLastAdmin> disabled title="Cannot change the role of the last admin"</#if>>
                <#list roles as r><option value="${r}"<#if r == u.role> selected</#if>>${r}</option></#list>
              </select>
              <#if !u.isLastAdmin><button type="submit">Save</button></#if>
            </form>
          </td>
          <td class="muted">
            <#if u.lastLoginAt?has_content><time datetime="${u.lastLoginAtIso}" title="${u.lastLoginAtExact}">${u.lastLoginAt}</time><#else>never</#if>
          </td>
          <td>
            <div class="toolbar" style="margin: 0">
              <details class="menu">
                <summary>Reset password</summary>
                <div class="menu-list" style="min-width: 280px; padding: 12px">
                  <form method="post" action="/admin/users/${u.id?c}/password" style="margin: 0">
                    <label for="pw-${u.id?c}" style="margin-top: 0">New temporary password</label>
                    <div class="toolbar" style="margin: 4px 0 8px">
                      <input id="pw-${u.id?c}" name="password" type="text" required minlength="${minPasswordLength}" autocomplete="new-password" placeholder="At least ${minPasswordLength} characters" style="flex: 1; min-width: 140px">
                      <button type="button" class="chip" data-gen-pw="pw-${u.id?c}" title="Generate temporary password">Generate</button>
                      <button type="button" class="chip" data-copy-pw="pw-${u.id?c}" title="Copy password">Copy</button>
                    </div>
                    <button type="submit" style="margin: 0; width: 100%; background: var(--accent); color: #fff; text-align: center">Set password</button>
                  </form>
                </div>
              </details>
              <#if u.isLastAdmin>
                <button type="button" class="danger" disabled title="The last admin cannot be removed">Remove</button>
              <#else>
                <form method="post" action="/admin/users/${u.id?c}/delete" style="margin: 0">
                  <button type="submit" class="danger">Remove</button>
                </form>
              </#if>
            </div>
          </td>
        </tr>
      </#list>
      </tbody>
    </table>
  </div>

  <h2>Add user<@layout.uid "admin:users.new"/></h2>
  <form class="card" method="post" action="/admin/users">
    <div class="row">
      <div>
        <label for="new-email">Email</label>
        <input id="new-email" name="email" type="email" required value="${newEmail}" placeholder="colleague@example.com" autocomplete="off">
      </div>
      <div style="flex: 0 1 160px">
        <label for="new-role">Role</label>
        <select id="new-role" name="role" style="width: 100%">
          <#list roles as r><option value="${r}"<#if r == newRole> selected</#if>>${r}</option></#list>
        </select>
      </div>
      <div>
        <label for="new-password">Temporary password</label>
        <div class="toolbar" style="margin: 0">
          <input id="new-password" name="password" type="text" required minlength="${minPasswordLength}" autocomplete="new-password" placeholder="At least ${minPasswordLength} characters" style="flex: 1; min-width: 160px">
          <button type="button" class="chip" data-gen-pw="new-password">Generate</button>
          <button type="button" class="chip" data-copy-pw="new-password">Copy</button>
        </div>
      </div>
    </div>
    <p class="muted help">Share the temporary password with the person. At least ${minPasswordLength} characters.</p>
    <button type="submit">Add user</button>
  </form>
</main>
<script>
  (function () {
    const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    function generatePassword(len) {
      const bytes = new Uint8Array(len);
      crypto.getRandomValues(bytes);
      return Array.from(bytes, b => alphabet[b % alphabet.length]).join("");
    }
    document.addEventListener("click", function (e) {
      const gen = e.target.closest("button[data-gen-pw]");
      if (gen) {
        const input = document.getElementById(gen.dataset.genPw);
        if (input) { input.value = generatePassword(16); input.focus(); input.select(); }
        return;
      }
      const copy = e.target.closest("button[data-copy-pw]");
      if (copy) {
        const input = document.getElementById(copy.dataset.copyPw);
        if (!input || !input.value) return;
        const orig = copy.textContent;
        const done = () => {
          copy.textContent = "Copied";
          clearTimeout(copy._t);
          copy._t = setTimeout(() => { copy.textContent = orig; }, 1200);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(input.value).then(done).catch(() => {});
        }
      }
    });
  })();
</script>
</@layout.page>
