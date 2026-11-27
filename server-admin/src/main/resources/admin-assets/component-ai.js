// AI-assisted custom component chat (Designer + JSON editor).
// Supports text prompts + inspiration image upload/paste (base64), server-side validation,
// 1-click Apply into Designer/JSON editor, and 1-click "Ask AI to fix" on validation errors.
(function () {
  const root = document.getElementById('ai-chat');
  if (!root) return;

  const id = root.dataset.id;
  const mode = root.dataset.mode || 'design'; // 'design' | 'json'
  const logEl = document.getElementById('ai-messages');
  const promptEl = document.getElementById('ai-prompt');
  const sendBtn = document.getElementById('ai-send');
  const clearBtn = document.getElementById('ai-clear');
  const fileEl = document.getElementById('ai-image');
  const previewWrap = document.getElementById('ai-image-preview');
  const previewImg = document.getElementById('ai-image-thumb');
  const removeImgBtn = document.getElementById('ai-image-remove');
  const statusEl = document.getElementById('ai-status');

  const history = [];
  let pendingImage = null; // { base64, mime, dataUrl }
  let busy = false;

  function currentJson() {
    if (mode === 'design' && window.composeCastDesigner && window.composeCastDesigner.definition) {
      return JSON.stringify(window.composeCastDesigner.definition(), null, 2);
    }
    const ta = document.getElementById('json');
    return ta ? ta.value : '';
  }

  function applyJsonToEditor(jsonText) {
    if (mode === 'design' && window.composeCastDesigner && window.composeCastDesigner.applyJson) {
      if (window.composeCastDesigner.applyJson(jsonText)) return true;
    }
    const ta = document.getElementById('json');
    if (ta) {
      ta.value = jsonText;
      ta.dispatchEvent(new Event('input', { bubbles: true }));
      return true;
    }
    return false;
  }

  function setPendingImage(file) {
    if (!file) return;
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
      statusEl.textContent = 'Only PNG, JPEG, or WebP images are supported.';
      statusEl.className = 'error help';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      statusEl.textContent = 'Image must be at most 5 MB.';
      statusEl.className = 'error help';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const comma = dataUrl.indexOf(',');
      if (comma < 0) return;
      pendingImage = { base64: dataUrl.slice(comma + 1), mime: file.type, dataUrl };
      previewImg.src = dataUrl;
      previewWrap.hidden = false;
      statusEl.textContent = '';
    };
    reader.readAsDataURL(file);
  }

  function clearPendingImage() {
    pendingImage = null;
    if (fileEl) fileEl.value = '';
    if (previewImg) previewImg.removeAttribute('src');
    if (previewWrap) previewWrap.hidden = true;
  }

  if (fileEl) {
    fileEl.addEventListener('change', () => setPendingImage(fileEl.files && fileEl.files[0]));
  }
  if (removeImgBtn) {
    removeImgBtn.addEventListener('click', clearPendingImage);
  }
  if (promptEl) {
    promptEl.addEventListener('paste', (e) => {
      const items = e.clipboardData && e.clipboardData.items;
      if (!items) return;
      for (const item of items) {
        if (item.type && item.type.startsWith('image/')) {
          setPendingImage(item.getAsFile());
          break;
        }
      }
    });
    promptEl.addEventListener('keydown', (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
        e.preventDefault();
        send();
      }
    });
  }

  function stripCodeFences(text) {
    return text.replace(/```(?:json)?\s*\n[\s\S]*?```/gi, '').trim();
  }

  function appendMessage(role, text, opts) {
    opts = opts || {};
    const empty = logEl.querySelector('.ai-empty');
    if (empty) empty.remove();

    const item = document.createElement('div');
    item.className = 'ai-msg ai-msg-' + role;

    if (opts.imageDataUrl) {
      const img = document.createElement('img');
      img.src = opts.imageDataUrl;
      img.alt = 'Inspiration image';
      img.className = 'ai-msg-img';
      item.appendChild(img);
    }

    const body = document.createElement('div');
    body.className = 'ai-msg-text';
    const summary = role === 'assistant' && opts.json ? (stripCodeFences(text) || 'Generated updated component JSON.') : text;
    body.textContent = summary;
    item.appendChild(body);

    if (opts.problems && opts.problems.length) {
      const errBox = document.createElement('div');
      errBox.className = 'ai-msg-problems';
      const title = document.createElement('p');
      title.className = 'error';
      title.style.margin = '6px 0 4px';
      title.textContent = 'Validation issues in generated JSON:';
      errBox.appendChild(title);
      const ul = document.createElement('ul');
      ul.className = 'problems';
      opts.problems.forEach((p) => {
        const li = document.createElement('li');
        const code = document.createElement('code');
        code.textContent = p;
        li.appendChild(code);
        ul.appendChild(li);
      });
      errBox.appendChild(ul);
      const fixBtn = document.createElement('button');
      fixBtn.type = 'button';
      fixBtn.className = 'chip';
      fixBtn.style.marginTop = '8px';
      fixBtn.textContent = 'Ask AI to fix';
      fixBtn.addEventListener('click', () => {
        promptEl.value = 'Fix these validation errors in the component JSON:\n- ' + opts.problems.join('\n- ');
        send();
      });
      errBox.appendChild(fixBtn);
      item.appendChild(errBox);
    } else if (opts.json) {
      const actions = document.createElement('div');
      actions.className = 'toolbar';
      actions.style.marginTop = '8px';
      actions.style.marginBottom = '0';
      const applyBtn = document.createElement('button');
      applyBtn.type = 'button';
      applyBtn.textContent = 'Apply to component';
      applyBtn.addEventListener('click', () => {
        const ok = applyJsonToEditor(opts.json);
        if (ok) {
          applyBtn.textContent = 'Applied ✓';
          statusEl.textContent = 'Applied to component.';
          statusEl.className = 'ok help';
        } else {
          statusEl.textContent = 'Could not apply this JSON to the visual designer (open JSON editor to apply raw RC JSON).';
          statusEl.className = 'error help';
        }
      });
      actions.appendChild(applyBtn);
      item.appendChild(actions);
    }

    logEl.appendChild(item);
    logEl.scrollTop = logEl.scrollHeight;
  }

  async function send() {
    if (busy) return;
    const text = (promptEl.value || '').trim();
    if (!text && !pendingImage) return;

    const msg = {
      role: 'user',
      content: text || 'Create a component inspired by this image.',
    };
    const imgForUi = pendingImage ? pendingImage.dataUrl : null;
    if (pendingImage) {
      msg.imageBase64 = pendingImage.base64;
      msg.imageMime = pendingImage.mime;
    }

    history.push(msg);
    appendMessage('user', msg.content, { imageDataUrl: imgForUi });
    promptEl.value = '';
    clearPendingImage();

    busy = true;
    sendBtn.disabled = true;
    statusEl.textContent = 'Thinking…';
    statusEl.className = 'muted help';

    try {
      const res = await fetch(`/admin/components/${encodeURIComponent(id)}/ai/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentJson: currentJson(), messages: history }),
      });
      const data = await res.json();
      if (!res.ok || data.error) {
        history.pop();
        statusEl.textContent = data.error || `Request failed (HTTP ${res.status})`;
        statusEl.className = 'error help';
        return;
      }
      history.push({ role: 'assistant', content: data.reply });
      appendMessage('assistant', data.reply, { json: data.json || null, problems: data.problems || [] });
      statusEl.textContent = '';
    } catch (err) {
      history.pop();
      statusEl.textContent = `Request failed: ${err.message}`;
      statusEl.className = 'error help';
    } finally {
      busy = false;
      sendBtn.disabled = false;
    }
  }

  sendBtn.addEventListener('click', send);
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      history.length = 0;
      logEl.innerHTML = '<p class="muted ai-empty" style="margin: 0">Describe the component you want to create or change, or attach/paste an inspiration image.</p>';
      statusEl.textContent = '';
    });
  }
})();
