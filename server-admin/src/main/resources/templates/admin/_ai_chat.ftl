<#-- Shared AI Assistant chat card included in designer.ftl (mode="design") and component.ftl (mode="json"). -->
<#if canEdit>
<div class="designer-code" id="ai">
  <h2>AI Assistant<@layout.uid aiUid/></h2>
  <div class="card">
    <#if hasAi!false>
      <div id="ai-chat" data-id="${id}" data-mode="${aiMode}">
        <div id="ai-messages" class="ai-messages" aria-live="polite">
          <p class="muted ai-empty" style="margin: 0">Describe the component you want to create or change, or attach/paste an inspiration image.</p>
        </div>
        <div id="ai-image-preview" class="ai-image-preview" hidden>
          <img id="ai-image-thumb" alt="Selected inspiration image">
          <button type="button" id="ai-image-remove" class="chip">Remove image</button>
        </div>
        <label for="ai-prompt" style="margin-top: 0">Message <span class="muted">(Ctrl+Enter to send · paste an image directly)</span></label>
        <textarea id="ai-prompt" class="ai-prompt" placeholder="e.g. Create a weather card with gradient background, temperature param, city title, and tap action…"></textarea>
        <div class="toolbar" style="margin-top: 8px; margin-bottom: 0">
          <button type="button" id="ai-send">Send</button>
          <label class="chip" style="display: inline-flex; align-items: center; gap: 6px; margin: 0; padding: 5px 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--bg); cursor: pointer; font-weight: 400; font-size: 14px">
            📎 Inspiration image
            <input type="file" id="ai-image" accept="image/png,image/jpeg,image/webp" hidden>
          </label>
          <button type="button" id="ai-clear" class="chip">Clear chat</button>
          <span id="ai-status" class="muted help" role="status"></span>
        </div>
      </div>
    <#else>
      <p class="muted" style="margin: 0">AI assistant is not configured yet. Set up <strong>Ollama</strong> or an <strong>OpenAI-compatible</strong> provider in <a href="/admin/settings">Settings</a> to create or edit components with AI.</p>
    </#if>
  </div>
</div>
</#if>
