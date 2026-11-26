// T-057 component designer (ADR 0034): a vanilla JS island over an SVG artboard. The design is the
// component definition's `design` (protocol/ComponentDesign.kt); the server compiles it to RC JSON,
// checks it like the JSON editor and keeps it as the draft. Units are dp, y grows downwards.
(() => {
  'use strict';
  const root = document.getElementById('designer');
  if (!root) return;
  const id = root.dataset.id;
  const canEdit = root.dataset.canEdit === 'true';
  const SVG = 'http://www.w3.org/2000/svg';
  const brand = JSON.parse(document.getElementById('brand').textContent);
  let def = JSON.parse(document.getElementById('definition').textContent);

  const DEFAULTS = {
    x: 0, y: 0, w: 0, h: 0, anchor: 'left', fill: null, fill2: null, gradientAngle: 90, stroke: null,
    strokeWidth: 0, radius: 0, opacity: 1, rotation: 0, path: null, pathWidth: null, pathHeight: null,
    text: null, fontSize: 16, bold: false, textAlign: 'start', maxLines: 1, action: null, hidden: false, name: '',
  };
  const TYPE_NAMES = { rect: 'Rectangle', ellipse: 'Ellipse', line: 'Line', path: 'Path', text: 'Text', tap: 'Tap area' };
  const ICONS = { rect: '▭', ellipse: '◯', line: '╱', path: '✎', text: 'T', tap: '☝' };

  const $ = (sel) => document.querySelector(sel);
  const stage = $('#stage');
  const layersEl = $('#layers');
  const propsEl = $('#props');
  const componentEl = $('#component-props');
  const statusEl = $('#status');
  const problemsEl = $('#problems');

  let tool = 'select';
  let selected = null;
  let zoom = 2;
  const undoStack = [];
  const redoStack = [];
  let drag = null;
  let pen = null;

  function normalize() {
    def.params = def.params || {};
    if (!def.design) return;
    const d = def.design;
    d.width = d.width ?? 360;
    d.height = d.height ?? 160;
    d.cornerRadius = d.cornerRadius ?? 0;
    d.elements = (d.elements || []).map((e) => Object.assign({}, DEFAULTS, e));
  }
  normalize();

  // ---------- model helpers ----------
  const D = () => def.design;
  const el = (eid) => D().elements.find((e) => e.id === eid);
  const sel = () => (selected ? el(selected) : null);
  const round = (v) => Math.round(v * 100) / 100;

  function newId(type) {
    let n = 1;
    while (el(`${type}${n}`)) n++;
    return `${type}${n}`;
  }

  function clean() {
    const copy = JSON.parse(JSON.stringify(def));
    delete copy.body;
    if (copy.design) {
      copy.design.elements = copy.design.elements.map((e) => {
        const out = {};
        Object.keys(e).forEach((k) => {
          const v = e[k];
          if (v === null || v === undefined || v === '') return;
          if (k !== 'id' && k !== 'type' && DEFAULTS[k] === v) return;
          out[k] = v;
        });
        if (e.type === 'text' && out.text === undefined) out.text = '';
        return out;
      });
    }
    return copy;
  }

  function pushUndo() {
    undoStack.push(JSON.stringify(def));
    if (undoStack.length > 200) undoStack.shift();
    redoStack.length = 0;
  }

  function restore(json) {
    def = JSON.parse(json);
    normalize();
    if (selected && !el(selected)) selected = null;
    renderAll();
    scheduleSave();
  }

  function undo() {
    if (!undoStack.length) return;
    redoStack.push(JSON.stringify(def));
    restore(undoStack.pop());
  }

  function redo() {
    if (!redoStack.length) return;
    undoStack.push(JSON.stringify(def));
    restore(redoStack.pop());
  }

  // ---------- values for the preview ----------
  function placeholder(value) {
    const m = /^\s*\{\{\s*([A-Za-z]+)\.([A-Za-z_][A-Za-z0-9_]*)\s*}}\s*$/.exec(value || '');
    return m ? { root: m[1], name: m[2] } : null;
  }

  function resolveText(text) {
    return (text || '').replace(/\{\{\s*([A-Za-z]+)\.([A-Za-z_][A-Za-z0-9_]*)\s*}}/g, (all, r, n) => {
      if (r === 'params') {
        const p = def.params[n];
        return p && p.default ? p.default : `[${n}]`;
      }
      if (r === 'brand') return brand[n] || all;
      return all;
    });
  }

  /** `#RRGGBB`, `#AARRGGBB` or a placeholder → a CSS color (null = none). */
  function cssColor(value) {
    if (!value) return null;
    let v = value;
    const ph = placeholder(value);
    if (ph) v = ph.root === 'params' ? (def.params[ph.name] || {}).default : brand[ph.name];
    if (!v) return '#888888';
    const m8 = /^#([0-9a-fA-F]{2})([0-9a-fA-F]{6})$/.exec(v);
    if (m8) {
      const a = parseInt(m8[1], 16) / 255;
      const rgb = m8[2];
      return `rgba(${parseInt(rgb.slice(0, 2), 16)},${parseInt(rgb.slice(2, 4), 16)},${parseInt(rgb.slice(4, 6), 16)},${a.toFixed(3)})`;
    }
    return /^#[0-9a-fA-F]{6}$/.test(v) ? v : '#888888';
  }

  function hex6(value) {
    const c = cssColor(value);
    if (!c) return '#000000';
    if (c.startsWith('#')) return c.toLowerCase();
    const m = /rgba\((\d+),(\d+),(\d+)/.exec(c);
    return m ? '#' + [m[1], m[2], m[3]].map((n) => (+n).toString(16).padStart(2, '0')).join('') : '#000000';
  }

  // ---------- SVG rendering ----------
  let svg;
  function node(name, attrs, parent) {
    const n = document.createElementNS(SVG, name);
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (v !== null && v !== undefined) n.setAttribute(k, v);
    });
    if (parent) parent.appendChild(n);
    return n;
  }

  function renderStage() {
    const d = D();
    stage.innerHTML = '';
    svg = node('svg', {
      width: d.width * zoom, height: d.height * zoom, viewBox: `0 0 ${d.width} ${d.height}`, tabindex: 0,
    }, stage);
    const defs = node('defs', {}, svg);
    node('clipPath', { id: 'artboard-clip' }, defs).appendChild(
      node('rect', { width: d.width, height: d.height, rx: d.cornerRadius, ry: d.cornerRadius }),
    );
    node('rect', {
      width: d.width, height: d.height, rx: d.cornerRadius, ry: d.cornerRadius, fill: '#ffffff',
      stroke: '#b0b0b8', 'stroke-width': 1 / zoom, 'stroke-dasharray': `${4 / zoom} ${4 / zoom}`, 'data-bg': '1',
    }, svg);
    const content = node('g', { 'clip-path': 'url(#artboard-clip)' }, svg);
    d.elements.forEach((e) => {
      if (!e.hidden) drawElement(e, content, defs);
    });
    drawSelection();
    if (pen) drawPen();
    svg.addEventListener('pointerdown', onPointerDown);
    if (tool === 'pen') svg.addEventListener('pointermove', penHover);
  }

  function paintAttrs(e, defs) {
    let fill = cssColor(e.fill) || 'none';
    if (e.fill && e.fill2 && e.type !== 'line') {
      // Like the compiler: a path paints in its own box (pathWidth × pathHeight), shapes in theirs.
      const isPath = e.type === 'path';
      const w = isPath && e.pathWidth ? e.pathWidth : e.w;
      const h = isPath && e.pathHeight ? e.pathHeight : e.h;
      const rad = (e.gradientAngle * Math.PI) / 180;
      const dx = (Math.cos(rad) * w) / 2;
      const dy = (Math.sin(rad) * h) / 2;
      const ox = isPath ? 0 : e.x;
      const oy = isPath ? 0 : e.y;
      const g = node('linearGradient', {
        id: `grad-${e.id}`, gradientUnits: 'userSpaceOnUse',
        x1: ox + w / 2 - dx, y1: oy + h / 2 - dy, x2: ox + w / 2 + dx, y2: oy + h / 2 + dy,
      }, defs);
      node('stop', { offset: 0, 'stop-color': cssColor(e.fill) }, g);
      node('stop', { offset: 1, 'stop-color': cssColor(e.fill2) }, g);
      fill = `url(#grad-${e.id})`;
    }
    const attrs = { fill, opacity: e.opacity < 1 ? e.opacity : null, 'data-id': e.id };
    if (e.stroke && e.strokeWidth > 0) {
      Object.assign(attrs, { stroke: cssColor(e.stroke), 'stroke-width': e.strokeWidth, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' });
    }
    return attrs;
  }

  function rotation(e) {
    return e.rotation ? `rotate(${e.rotation} ${e.x + e.w / 2} ${e.y + e.h / 2})` : null;
  }

  function drawElement(e, parent, defs) {
    const a = paintAttrs(e, defs);
    switch (e.type) {
      case 'rect':
        node('rect', Object.assign(a, { x: e.x, y: e.y, width: Math.max(e.w, 0), height: Math.max(e.h, 0), rx: e.radius, ry: e.radius, transform: rotation(e) }), parent);
        break;
      case 'ellipse':
        node('ellipse', Object.assign(a, { cx: e.x + e.w / 2, cy: e.y + e.h / 2, rx: Math.max(e.w / 2, 0), ry: Math.max(e.h / 2, 0), transform: rotation(e) }), parent);
        break;
      case 'line':
        node('line', Object.assign(a, { x1: e.x, y1: e.y, x2: e.x + e.w, y2: e.y + e.h, fill: null }), parent);
        // A wide transparent line makes thin lines easy to grab.
        node('line', { x1: e.x, y1: e.y, x2: e.x + e.w, y2: e.y + e.h, stroke: 'transparent', 'stroke-width': 10 / zoom, 'data-id': e.id }, parent);
        break;
      case 'path': {
        const sx = e.pathWidth ? e.w / e.pathWidth : 1;
        const sy = e.pathHeight ? e.h / e.pathHeight : 1;
        const tr = `translate(${e.x} ${e.y})` + (e.rotation ? ` rotate(${e.rotation} ${e.w / 2} ${e.h / 2})` : '') + ` scale(${sx} ${sy})`;
        const g = node('g', { transform: tr }, parent);
        node('path', Object.assign(a, { d: e.path || '' }), g);
        node('rect', { x: 0, y: 0, width: e.pathWidth || e.w, height: e.pathHeight || e.h, fill: 'transparent', 'data-id': e.id }, g);
        break;
      }
      case 'text': {
        const fo = node('foreignObject', { x: e.x, y: e.y, width: Math.max(e.w, 1), height: Math.max(e.h, e.fontSize * 1.4 * e.maxLines), 'data-id': e.id }, parent);
        const div = document.createElement('div');
        div.setAttribute('data-id', e.id);
        div.textContent = resolveText(e.text);
        Object.assign(div.style, {
          font: `${e.bold ? 700 : 400} ${e.fontSize}px/1.25 Roboto, system-ui, sans-serif`,
          color: cssColor(e.fill) || '#000', textAlign: e.textAlign === 'end' ? 'right' : e.textAlign === 'center' ? 'center' : 'left',
          opacity: e.opacity, display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: String(e.maxLines),
          overflow: 'hidden', overflowWrap: 'anywhere', cursor: 'default',
        });
        fo.appendChild(div);
        break;
      }
      case 'tap':
        node('rect', {
          x: e.x, y: e.y, width: Math.max(e.w, 0), height: Math.max(e.h, 0), fill: 'rgba(59,91,219,.12)',
          stroke: '#3b5bdb', 'stroke-width': 1 / zoom, 'stroke-dasharray': `${3 / zoom} ${3 / zoom}`, 'data-id': e.id,
        }, parent);
        break;
      default:
        break;
    }
  }

  function bounds(e) {
    const x1 = Math.min(e.x, e.x + e.w);
    const y1 = Math.min(e.y, e.y + e.h);
    return { x: x1, y: y1, w: Math.abs(e.w), h: Math.abs(e.h) };
  }

  function drawSelection() {
    const e = sel();
    if (!e || e.hidden) return;
    const b = bounds(e);
    node('rect', {
      x: b.x, y: b.y, width: b.w, height: b.h, fill: 'none', stroke: '#3b5bdb', 'stroke-width': 1.5 / zoom, 'pointer-events': 'none',
    }, svg);
    if (!canEdit) return;
    const s = 8 / zoom;
    const corners = e.type === 'line'
      ? [['p1', e.x, e.y], ['p2', e.x + e.w, e.y + e.h]]
      : [['nw', b.x, b.y], ['ne', b.x + b.w, b.y], ['sw', b.x, b.y + b.h], ['se', b.x + b.w, b.y + b.h]];
    corners.forEach(([h, x, y]) => {
      node('rect', {
        x: x - s / 2, y: y - s / 2, width: s, height: s, fill: '#fff', stroke: '#3b5bdb', 'stroke-width': 1.5 / zoom,
        'data-handle': h, style: 'cursor: nwse-resize',
      }, svg);
    });
  }

  /** The pen path so far: segments, the rubber band to the pointer, anchors and Bézier handles. */
  function drawPen() {
    const pts = pen.points;
    if (!pts.length) return;
    const blue = '#3b5bdb';
    const thin = 1.5 / zoom;
    let d = `M${pts[0].x} ${pts[0].y}`;
    for (let i = 1; i < pts.length; i++) d += ' ' + segment(pts[i - 1], pts[i]);
    // A white halo under the blue line keeps it visible on any fill (blue cards included).
    node('path', { d, fill: 'none', stroke: '#fff', 'stroke-width': thin * 3, 'stroke-opacity': 0.8, 'pointer-events': 'none' }, svg);
    node('path', { d, fill: 'none', stroke: blue, 'stroke-width': thin, 'pointer-events': 'none' }, svg);
    const last = pts[pts.length - 1];
    if (pen.cursor && pen.drag === null) {
      const c = { x: pen.cursor[0], y: pen.cursor[1], hin: pen.cursor, hout: pen.cursor };
      node('path', {
        d: `M${last.x} ${last.y} ${segment(last, c)}`, fill: 'none', stroke: blue, 'stroke-width': thin,
        'stroke-dasharray': `${4 / zoom} ${3 / zoom}`, 'pointer-events': 'none',
      }, svg);
    }
    pts.forEach((p, i) => {
      [p.hin, p.hout].forEach((h) => {
        if (h[0] === p.x && h[1] === p.y) return;
        node('line', { x1: p.x, y1: p.y, x2: h[0], y2: h[1], stroke: blue, 'stroke-width': 1 / zoom, 'pointer-events': 'none' }, svg);
        node('circle', { cx: h[0], cy: h[1], r: 2.5 / zoom, fill: '#fff', stroke: blue, 'stroke-width': 1 / zoom, 'pointer-events': 'none' }, svg);
      });
      const first = i === 0 && pts.length > 2;
      node('rect', {
        x: p.x - (first ? 5 : 3.5) / zoom, y: p.y - (first ? 5 : 3.5) / zoom, width: (first ? 10 : 7) / zoom, height: (first ? 10 : 7) / zoom,
        fill: i === pts.length - 1 ? blue : '#fff', stroke: blue, 'stroke-width': 1 / zoom, 'data-pen-start': i === 0 ? '1' : null,
        style: first ? 'cursor: pointer' : null,
      }, svg);
    });
  }

  /**
   * The SVG segment from anchor [a] to anchor [b]: a straight `L` when neither has a handle on that
   * side, else a cubic `C` (a's out-handle, b's in-handle) — both are in RC's path subset (fact 10).
   */
  function segment(a, b) {
    const straight = a.hout[0] === a.x && a.hout[1] === a.y && b.hin[0] === b.x && b.hin[1] === b.y;
    return straight ? `L${b.x} ${b.y}` : `C${a.hout[0]} ${a.hout[1]} ${b.hin[0]} ${b.hin[1]} ${b.x} ${b.y}`;
  }

  // ---------- the pen (like Figma) ----------
  // Click: a corner point. Press and drag: a smooth point whose handles follow the pointer
  // (mirrored; Alt moves only the outgoing one). Click the first point to close, Enter or Esc to
  // finish an open path, Backspace removes the last point.
  function penDown(ev, x, y) {
    pen = pen || { points: [], drag: null, cursor: null };
    if (ev.target.getAttribute('data-pen-start') && pen.points.length > 2) {
      pen.closing = true;
      pen.drag = 0;
    } else {
      pen.points.push({ x, y, hin: [x, y], hout: [x, y] });
      pen.drag = pen.points.length - 1;
    }
    window.addEventListener('pointermove', penMove);
    window.addEventListener('pointerup', penUp);
    window.addEventListener('pointercancel', penUp);
    renderStage();
  }

  function penMove(ev) {
    if (!pen || pen.drag === null) return;
    const [x, y] = point(ev);
    const p = pen.points[pen.drag];
    if (Math.abs(x - p.x) < 2 && Math.abs(y - p.y) < 2) return; // a click, not a drag
    if (pen.closing) {
      // Dragging on the first point shapes the closing curve: its in-handle follows, mirrored out.
      p.hin = [2 * p.x - x, 2 * p.y - y];
      if (!ev.altKey) p.hout = [x, y];
    } else {
      p.hout = [x, y];
      if (!ev.altKey) p.hin = [2 * p.x - x, 2 * p.y - y];
    }
    renderStage();
  }

  function penUp() {
    window.removeEventListener('pointermove', penMove);
    window.removeEventListener('pointerup', penUp);
    window.removeEventListener('pointercancel', penUp);
    if (!pen) return;
    pen.drag = null;
    if (pen.closing) return finishPen(true);
    renderStage();
  }

  function penHover(ev) {
    if (!pen || pen.drag !== null) return;
    pen.cursor = point(ev);
    renderStage();
  }

  // ---------- pointer interaction ----------
  function point(ev) {
    const p = svg.createSVGPoint();
    p.x = ev.clientX;
    p.y = ev.clientY;
    const r = p.matrixTransform(svg.getScreenCTM().inverse());
    return [Math.round(r.x), Math.round(r.y)];
  }

  /**
   * Moves and the release are followed on the window, not on the artboard: every move redraws the
   * artboard (a new <svg>), so listeners or a pointer capture on the old one would stop after the
   * first step. The window also keeps the drag going when the pointer leaves the artboard.
   */
  function listenToDrag() {
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    window.addEventListener('pointercancel', onPointerUp);
  }

  function onPointerDown(ev) {
    if (!canEdit && tool !== 'select') return;
    const [x, y] = point(ev);
    const target = ev.target;
    if (tool === 'pen') return penDown(ev, x, y);
    if (tool !== 'select') {
      pushUndo();
      const type = tool;
      const e = Object.assign({}, DEFAULTS, { id: newId(type), type, x, y, w: 0, h: 0, name: '' });
      if (type === 'rect' || type === 'ellipse') e.fill = '#FFB0B4BC';
      if (type === 'line') Object.assign(e, { stroke: '#FF1D1D1F', strokeWidth: 2 });
      if (type === 'text') Object.assign(e, { text: 'Text', fill: '#FF1D1D1F' });
      if (type === 'tap') e.action = 'navigate:main';
      D().elements.push(e);
      selected = e.id;
      drag = { kind: 'create', id: e.id, x0: x, y0: y };
      listenToDrag();
      return;
    }
    const handle = target.getAttribute('data-handle');
    const hitId = target.getAttribute('data-id') || (target.closest && target.closest('[data-id]') && target.closest('[data-id]').getAttribute('data-id'));
    if (handle && sel()) {
      pushUndo();
      drag = { kind: 'resize', handle, id: selected, start: Object.assign({}, sel()), x0: x, y0: y };
    } else if (hitId) {
      selected = hitId;
      if (canEdit) {
        pushUndo();
        drag = { kind: 'move', id: hitId, start: Object.assign({}, el(hitId)), x0: x, y0: y, moved: false };
      }
      renderAll();
    } else {
      selected = null;
      renderAll();
      return;
    }
    if (drag) {
      listenToDrag();
    }
  }

  function onPointerMove(ev) {
    if (!drag) return;
    const [x, y] = point(ev);
    const e = el(drag.id);
    if (!e) return;
    const dx = x - drag.x0;
    const dy = y - drag.y0;
    if (drag.kind === 'create') {
      e.w = dx;
      e.h = dy;
      if (ev.shiftKey && e.type !== 'line') e.w = e.h = Math.max(Math.abs(dx), Math.abs(dy)) * Math.sign(dx || 1);
    } else if (drag.kind === 'move') {
      e.x = drag.start.x + dx;
      e.y = drag.start.y + dy;
      drag.moved = drag.moved || dx !== 0 || dy !== 0;
    } else if (drag.kind === 'resize') {
      const s = drag.start;
      if (e.type === 'line') {
        if (drag.handle === 'p1') {
          e.x = s.x + dx; e.y = s.y + dy; e.w = s.w - dx; e.h = s.h - dy;
        } else {
          e.w = s.w + dx; e.h = s.h + dy;
        }
      } else {
        let { x: nx, y: ny, w: nw, h: nh } = s;
        if (drag.handle.includes('w')) { nx = s.x + dx; nw = s.w - dx; }
        if (drag.handle.includes('e')) { nw = s.w + dx; }
        if (drag.handle.includes('n')) { ny = s.y + dy; nh = s.h - dy; }
        if (drag.handle.includes('s')) { nh = s.h + dy; }
        if (ev.shiftKey && s.w > 0 && s.h > 0) {
          const ratio = s.w / s.h;
          if (Math.abs(nw) / ratio > Math.abs(nh)) nh = nw / ratio; else nw = nh * ratio;
          if (drag.handle.includes('n')) ny = s.y + s.h - nh;
          if (drag.handle.includes('w')) nx = s.x + s.w - nw;
        }
        Object.assign(e, { x: nx, y: ny, w: nw, h: nh });
      }
    }
    renderStage();
  }

  function onPointerUp() {
    window.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('pointerup', onPointerUp);
    window.removeEventListener('pointercancel', onPointerUp);
    const e = drag && el(drag.id);
    if (e && e.type !== 'line') {
      // Drawn right-to-left or bottom-up: keep x, y the top left corner.
      if (e.w < 0) { e.x += e.w; e.w = -e.w; }
      if (e.h < 0) { e.y += e.h; e.h = -e.h; }
    }
    if (drag && drag.kind === 'create' && e && Math.abs(e.w) < 3 && Math.abs(e.h) < 3) {
      const size = { text: [160, 24], line: [80, 0], tap: [120, 40] }[e.type] || [80, 48];
      e.w = size[0];
      e.h = size[1];
    }
    const changedSomething = drag && (drag.kind !== 'move' || drag.moved);
    if (drag && drag.kind === 'move' && !drag.moved) undoStack.pop();
    if (drag && drag.kind === 'create') setTool('select');
    drag = null;
    renderAll();
    if (changedSomething) scheduleSave();
  }

  function finishPen(closed) {
    if (!pen || pen.points.length < 2) {
      pen = null;
      renderStage();
      return;
    }
    pushUndo();
    const pts = pen.points;
    // The box holds the anchors and the handles: a cubic curve stays inside its control points.
    const xs = pts.flatMap((p) => [p.x, p.hin[0], p.hout[0]]);
    const ys = pts.flatMap((p) => [p.y, p.hin[1], p.hout[1]]);
    const minX = Math.min(...xs);
    const minY = Math.min(...ys);
    const w = Math.max(Math.max(...xs) - minX, 1);
    const h = Math.max(Math.max(...ys) - minY, 1);
    const local = (p) => ({ x: p.x - minX, y: p.y - minY, hin: [p.hin[0] - minX, p.hin[1] - minY], hout: [p.hout[0] - minX, p.hout[1] - minY] });
    const lp = pts.map(local);
    let d = `M${lp[0].x} ${lp[0].y}`;
    for (let i = 1; i < lp.length; i++) d += ' ' + segment(lp[i - 1], lp[i]);
    if (closed) d += ' ' + segment(lp[lp.length - 1], lp[0]) + ' Z';
    const e = Object.assign({}, DEFAULTS, {
      id: newId('path'), type: 'path', x: minX, y: minY, w, h, path: d, pathWidth: w, pathHeight: h,
      stroke: '#FF1D1D1F', strokeWidth: 2, fill: closed ? '#FFB0B4BC' : null,
    });
    D().elements.push(e);
    selected = e.id;
    pen = null;
    setTool('select');
    renderAll();
    scheduleSave();
  }

  // ---------- commands ----------
  function command(cmd) {
    const e = sel();
    const list = D() ? D().elements : [];
    const i = e ? list.indexOf(e) : -1;
    switch (cmd) {
      case 'undo': return undo();
      case 'redo': return redo();
      case 'import-svg': return importSvg();
      case 'delete':
        if (!e) return;
        pushUndo();
        list.splice(i, 1);
        selected = null;
        break;
      case 'duplicate': {
        if (!e) return;
        pushUndo();
        const copy = Object.assign({}, e, { id: newId(e.type), x: e.x + 8, y: e.y + 8, name: e.name ? `${e.name} copy` : '' });
        list.splice(i + 1, 0, copy);
        selected = copy.id;
        break;
      }
      case 'up':
        if (!e || i === list.length - 1) return;
        pushUndo();
        [list[i], list[i + 1]] = [list[i + 1], list[i]];
        break;
      case 'down':
        if (!e || i <= 0) return;
        pushUndo();
        [list[i], list[i - 1]] = [list[i - 1], list[i]];
        break;
      default:
        return;
    }
    renderAll();
    scheduleSave();
  }

  function setTool(t) {
    tool = t;
    if (t !== 'pen') pen = null;
    document.querySelectorAll('[data-tool]').forEach((b) => b.classList.toggle('active', b.dataset.tool === t));
    if (svg) renderStage(); // the pen's rubber band listens on the artboard
    if (svg) svg.style.cursor = t === 'select' ? 'default' : 'crosshair';
  }

  document.querySelectorAll('[data-tool]').forEach((b) => b.addEventListener('click', () => setTool(b.dataset.tool)));
  document.querySelectorAll('[data-cmd]').forEach((b) => b.addEventListener('click', () => command(b.dataset.cmd)));
  $('#zoom').addEventListener('change', (ev) => {
    zoom = +ev.target.value;
    renderStage();
  });

  document.addEventListener('keydown', (ev) => {
    if (!D()) return;
    const typing = ev.target.closest && ev.target.closest('input, textarea, select');
    if (typing) return;
    const mod = ev.ctrlKey || ev.metaKey;
    if (mod && ev.key.toLowerCase() === 'z') { ev.preventDefault(); return ev.shiftKey ? redo() : undo(); }
    if (mod && ev.key.toLowerCase() === 'y') { ev.preventDefault(); return redo(); }
    if (mod && ev.key.toLowerCase() === 'd') { ev.preventDefault(); return command('duplicate'); }
    if (!canEdit) return;
    if ((ev.key === 'Enter' || ev.key === 'Escape') && pen && pen.points.length) {
      ev.preventDefault();
      return finishPen(false);
    }
    if ((ev.key === 'Delete' || ev.key === 'Backspace') && pen && pen.points.length) {
      ev.preventDefault();
      pen.points.pop();
      if (!pen.points.length) pen = null;
      return renderStage();
    }
    if (ev.key === 'Escape') {
      pen = null;
      selected = null;
      setTool('select');
      return renderAll();
    }
    if ((ev.key === 'Delete' || ev.key === 'Backspace') && sel()) { ev.preventDefault(); return command('delete'); }
    const arrows = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
    if (arrows[ev.key] && sel()) {
      ev.preventDefault();
      pushUndo();
      const step = ev.shiftKey ? 10 : 1;
      sel().x += arrows[ev.key][0] * step;
      sel().y += arrows[ev.key][1] * step;
      renderAll();
      return scheduleSave();
    }
    const keys = { v: 'select', r: 'rect', o: 'ellipse', l: 'line', p: 'pen', t: 'text', a: 'tap' };
    if (!mod && keys[ev.key.toLowerCase()]) setTool(keys[ev.key.toLowerCase()]);
  });

  // ---------- layers ----------
  function renderLayers() {
    layersEl.innerHTML = '';
    [...D().elements].reverse().forEach((e) => {
      const li = document.createElement('li');
      li.className = (e.id === selected ? 'selected ' : '') + (e.hidden ? 'hidden' : '');
      const icon = document.createElement('span');
      icon.textContent = ICONS[e.type] || '?';
      const name = document.createElement('span');
      name.className = 'name';
      name.textContent = e.name || (e.type === 'text' ? resolveText(e.text) : '') || `${TYPE_NAMES[e.type]} (${e.id})`;
      const eye = document.createElement('button');
      eye.type = 'button';
      eye.title = e.hidden ? 'Show' : 'Hide';
      eye.textContent = e.hidden ? '◌' : '◉';
      eye.addEventListener('click', (ev) => {
        ev.stopPropagation();
        if (!canEdit) return;
        pushUndo();
        e.hidden = !e.hidden;
        renderAll();
        scheduleSave();
      });
      li.append(icon, name, eye);
      li.addEventListener('click', () => {
        selected = e.id;
        renderAll();
      });
      layersEl.appendChild(li);
    });
    if (!D().elements.length) layersEl.innerHTML = '<li class="muted">Nothing yet: pick a tool and drag on the artboard.</li>';
  }

  // ---------- property forms ----------
  /** A label above its input (and hint), in one box so `.grid2` can put two side by side. */
  function field(parent, label, input, hint) {
    const box = document.createElement('div');
    const l = document.createElement('label');
    l.textContent = label;
    box.append(l, input);
    if (hint) {
      const p = document.createElement('p');
      p.className = 'hint';
      p.textContent = hint;
      box.appendChild(p);
    }
    parent.appendChild(box);
    return input;
  }

  function input(type, value, onChange, attrs) {
    const i = document.createElement(type === 'textarea' ? 'textarea' : 'input');
    if (type !== 'textarea') i.type = type;
    if (type === 'checkbox') i.checked = !!value; else i.value = value ?? '';
    Object.assign(i, attrs || {});
    i.disabled = !canEdit;
    i.addEventListener('change', () => {
      pushUndo();
      onChange(type === 'checkbox' ? i.checked : type === 'number' ? (i.value === '' ? 0 : +i.value) : i.value);
      renderStage();
      renderLayers();
      scheduleSave();
    });
    return i;
  }

  function select(options, value, onChange) {
    const s = document.createElement('select');
    options.forEach(([v, label]) => {
      const o = document.createElement('option');
      o.value = v;
      o.textContent = label;
      o.selected = v === value;
      s.appendChild(o);
    });
    s.disabled = !canEdit;
    s.addEventListener('change', () => {
      pushUndo();
      onChange(s.value);
      renderStage();
      scheduleSave();
    });
    return s;
  }

  function colorField(parent, label, e, key, allowNone) {
    const wrap = document.createElement('div');
    wrap.className = 'color';
    const picker = document.createElement('input');
    picker.type = 'color';
    picker.value = hex6(e[key]);
    picker.disabled = !canEdit;
    const text = document.createElement('input');
    text.type = 'text';
    text.value = e[key] || '';
    text.placeholder = allowNone ? 'none' : '#FF000000';
    text.disabled = !canEdit;
    picker.addEventListener('input', () => {
      const old = e[key] || '';
      const alpha = /^#[0-9a-fA-F]{8}$/.test(old) ? old.slice(1, 3) : 'FF';
      e[key] = `#${alpha}${picker.value.slice(1)}`.toUpperCase();
      text.value = e[key];
      renderStage();
    });
    picker.addEventListener('change', () => {
      undoStack.push(JSON.stringify(def));
      scheduleSave();
    });
    text.addEventListener('change', () => {
      pushUndo();
      e[key] = text.value.trim() || null;
      picker.value = hex6(e[key]);
      renderStage();
      scheduleSave();
    });
    wrap.append(picker, text);
    field(parent, label, wrap);
  }

  function renderProps() {
    propsEl.innerHTML = '';
    const e = sel();
    if (!e) {
      propsEl.innerHTML = '<p class="muted">Select a layer, or pick a tool and drag on the artboard.</p>';
      return;
    }
    const p = propsEl;
    const set = (k) => (v) => { e[k] = v; };
    field(p, 'Name', input('text', e.name, set('name'), { placeholder: TYPE_NAMES[e.type] }));
    const g = document.createElement('div');
    g.className = 'grid2';
    p.appendChild(g);
    field(g, 'X', input('number', e.x, set('x')));
    field(g, 'Y', input('number', e.y, set('y')));
    field(g, 'Width', input('number', e.w, set('w')));
    field(g, 'Height', input('number', e.h, set('h')));
    field(p, 'On other widths', select(
      [['left', 'Keep distance from left'], ['right', 'Keep distance from right'], ['center', 'Keep distance from center'], ['stretch', 'Stretch with the component']],
      e.anchor, set('anchor'),
    ));
    if (e.type === 'text') {
      field(p, 'Text', input('textarea', e.text, set('text')), 'Use {{params.name}} for a value set on each screen.');
      colorField(p, 'Color', e, 'fill');
      const t = document.createElement('div');
      t.className = 'grid2';
      p.appendChild(t);
      field(t, 'Size', input('number', e.fontSize, set('fontSize'), { min: 6, max: 96 }));
      field(t, 'Max lines', input('number', e.maxLines, set('maxLines'), { min: 1, max: 20 }));
      field(p, 'Align', select([['start', 'Left'], ['center', 'Center'], ['end', 'Right']], e.textAlign, set('textAlign')));
      const c = document.createElement('label');
      c.className = 'check';
      c.append(input('checkbox', e.bold, set('bold')), document.createTextNode('Bold'));
      p.appendChild(c);
    } else if (e.type !== 'tap') {
      if (e.type !== 'line') {
        colorField(p, 'Fill', e, 'fill', true);
        if (e.fill) {
          colorField(p, 'Gradient to', e, 'fill2', true);
          if (e.fill2) field(p, 'Gradient angle (°, 0 = left to right)', input('number', e.gradientAngle, set('gradientAngle')));
        }
      }
      colorField(p, 'Stroke', e, 'stroke', true);
      field(p, 'Stroke width', input('number', e.strokeWidth, set('strokeWidth'), { min: 0, step: 0.5 }));
      if (e.type === 'rect') field(p, 'Corner radius', input('number', e.radius, set('radius'), { min: 0 }));
      if (e.type !== 'line') field(p, 'Rotation (°)', input('number', e.rotation, set('rotation')));
      if (e.type === 'path') {
        field(p, 'Path data', input('textarea', e.path, (v) => {
          try {
            e.path = normalizePath(v, 0, 0);
          } catch (err) {
            e.path = v;
            showProblems([`path: ${err.message}`]);
          }
        }), 'SVG path data; relative commands are converted.');
      }
    }
    field(p, 'Opacity', input('number', e.opacity, set('opacity'), { min: 0, max: 1, step: 0.05 }));
    field(p, 'On tap', input('text', e.action, (v) => { e.action = v.trim() || null; }, { placeholder: 'navigate:screen or {{params.action}}' }),
      e.type === 'tap' ? 'An invisible area that runs this action.' : 'Optional: tapping this element runs the action.');
  }

  function renderComponent() {
    const c = componentEl;
    c.innerHTML = '';
    field(c, 'Title', input('text', def.title || '', (v) => { def.title = v; }));
    field(c, 'Description', input('textarea', def.description || '', (v) => { def.description = v; }));
    const g = document.createElement('div');
    g.className = 'grid2';
    c.appendChild(g);
    field(g, 'Height', input('number', D().height, (v) => { D().height = v; }, { min: 8 }));
    field(g, 'Corners', input('number', D().cornerRadius, (v) => { D().cornerRadius = v; }, { min: 0 }));
    field(c, 'Artboard width', input('number', D().width, (v) => { D().width = v; }, { min: 40 }), 'The width you design on; phones vary (anchors).');
    const h = document.createElement('h2');
    h.textContent = 'Params';
    h.style.marginTop = '16px';
    c.appendChild(h);
    Object.entries(def.params).forEach(([name, param]) => {
      const box = document.createElement('div');
      box.className = 'param';
      const title = document.createElement('div');
      title.style.display = 'flex';
      title.style.justifyContent = 'space-between';
      const code = document.createElement('code');
      code.textContent = `{{params.${name}}}`;
      const del = document.createElement('button');
      del.type = 'button';
      del.className = 'small';
      del.textContent = '✕';
      del.title = 'Remove param';
      del.disabled = !canEdit;
      del.addEventListener('click', () => {
        pushUndo();
        delete def.params[name];
        renderComponent();
        renderStage();
        scheduleSave();
      });
      title.append(code, del);
      box.appendChild(title);
      field(box, 'Type', select([['text', 'Text'], ['color', 'Color'], ['number', 'Number'], ['action', 'Action']], param.type || 'text', (v) => {
        param.type = v === 'text' ? undefined : v;
      }));
      field(box, 'Default', input('text', param.default ?? '', (v) => { param.default = v === '' ? undefined : v; }));
      field(box, 'Description', input('text', param.description || '', (v) => { param.description = v || undefined; }));
      c.appendChild(box);
    });
    if (canEdit) {
      const row = document.createElement('div');
      row.className = 'color';
      const name = document.createElement('input');
      name.placeholder = 'new param name';
      const add = document.createElement('button');
      add.type = 'button';
      add.className = 'small';
      add.textContent = 'Add';
      add.addEventListener('click', () => {
        const n = name.value.trim();
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(n) || def.params[n]) return;
        pushUndo();
        def.params[n] = { description: '' };
        renderComponent();
        scheduleSave();
      });
      row.append(name, add);
      c.appendChild(row);
    }
  }

  function renderAll() {
    renderStage();
    renderLayers();
    renderProps();
    renderCode();
  }

  // ---------- code (JSON under the artboard, like the screen editor) ----------
  const codeEl = $('#code-json');
  const codeStatus = $('#code-status');
  const rcEl = $('#code-rc');
  let compiled = null;

  /** The definition as JSON; left alone while the owner is typing in it. */
  function renderCode() {
    if (!codeEl || document.activeElement === codeEl) return;
    codeEl.value = JSON.stringify(clean(), null, 2);
  }

  let codeTimer = null;
  if (codeEl && canEdit) {
    codeEl.addEventListener('input', () => {
      clearTimeout(codeTimer);
      codeTimer = setTimeout(applyCode, 500);
    });
    codeEl.addEventListener('blur', renderCode);
  }

  /** Typed JSON → the design (redrawn and saved); the server checks it like any save. */
  function applyCode() {
    let parsed;
    try {
      parsed = JSON.parse(codeEl.value);
    } catch (err) {
      codeStatus.textContent = `Not valid JSON yet: ${err.message}`;
      codeStatus.className = 'error';
      return;
    }
    if (!parsed || typeof parsed !== 'object' || !parsed.design) {
      codeStatus.textContent = 'The designer needs a "design" object (raw Remote Compose bodies are edited in the JSON editor).';
      codeStatus.className = 'error';
      return;
    }
    codeStatus.textContent = '';
    codeStatus.className = 'muted';
    undoStack.push(JSON.stringify(def));
    redoStack.length = 0;
    def = parsed;
    normalize();
    if (selected && !el(selected)) selected = null;
    renderStage();
    renderLayers();
    renderProps();
    renderComponent();
    scheduleSave();
  }

  function showTab(rc) {
    $('#code-json-pane').hidden = rc;
    $('#code-rc-pane').hidden = !rc;
    $('#tab-json').setAttribute('aria-selected', String(!rc));
    $('#tab-rc').setAttribute('aria-selected', String(rc));
    if (rc && !compiled) rcEl.textContent = 'Compiled on the next save…';
  }
  if ($('#tab-json')) {
    $('#tab-json').addEventListener('click', () => showTab(false));
    $('#tab-rc').addEventListener('click', () => {
      showTab(true);
      if (!compiled && canEdit) save();
    });
  }

  // ---------- saving ----------
  let saveTimer = null;
  function scheduleSave() {
    renderCode();
    if (!canEdit) return;
    statusEl.textContent = 'Saving…';
    statusEl.className = 'muted';
    clearTimeout(saveTimer);
    saveTimer = setTimeout(save, 600);
  }

  function showProblems(problems) {
    problemsEl.innerHTML = '';
    problems.forEach((p) => {
      const li = document.createElement('li');
      li.textContent = p;
      problemsEl.appendChild(li);
    });
  }

  async function save() {
    try {
      const res = await fetch(`/admin/components/${encodeURIComponent(id)}/design`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(clean()),
        credentials: 'same-origin',
      });
      const answer = await res.json();
      showProblems(answer.problems || []);
      if (answer.body) {
        compiled = answer.body;
        rcEl.textContent = JSON.stringify(compiled, null, 2);
      }
      if (answer.saved) {
        statusEl.textContent = 'Draft saved.';
        statusEl.className = 'ok';
        const phone = document.getElementById('phone');
        if (phone) phone.src = `/admin/components/${encodeURIComponent(id)}/phone.png?t=${Date.now()}`;
      } else {
        statusEl.textContent = 'Not saved: fix the problems below.';
        statusEl.className = 'error';
      }
    } catch (err) {
      statusEl.textContent = `Not saved: ${err.message}`;
      statusEl.className = 'error';
    }
  }

  const publishForm = document.getElementById('publish-form');
  if (publishForm) {
    publishForm.addEventListener('submit', () => {
      document.getElementById('publish-json').value = JSON.stringify(clean());
    });
  }

  // ---------- SVG import + path normalization ----------
  /**
   * SVG path data → the subset RC parses (absolute M L C Q A Z; RC JSON fact 10), moved by
   * (-dx, -dy). Relative commands, H/V, S/T and packed arc flags are converted.
   */
  function normalizePath(d, dx, dy) {
    let i = 0;
    const s = d || '';
    const out = [];
    const fmt = (n) => String(Math.round(n * 1000) / 1000);
    const ws = () => { while (i < s.length && /[\s,]/.test(s[i])) i++; };
    const num = () => {
      ws();
      const m = /^[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/.exec(s.slice(i));
      if (!m) throw new Error(`expected a number at ${i}`);
      i += m[0].length;
      return parseFloat(m[0]);
    };
    const flag = () => {
      ws();
      const c = s[i];
      if (c !== '0' && c !== '1') throw new Error(`expected an arc flag at ${i}`);
      i++;
      return +c;
    };
    const more = () => { ws(); return i < s.length && /[-+.\d]/.test(s[i]); };
    let cx = 0, cy = 0, sx = 0, sy = 0, lastC = null, lastQ = null, cmd = null;
    const P = (x, y) => `${fmt(x - dx)} ${fmt(y - dy)}`;
    while (true) {
      ws();
      if (i >= s.length) break;
      if (/[A-Za-z]/.test(s[i])) cmd = s[i++];
      else if (!cmd) throw new Error(`expected a command at ${i}`);
      const rel = cmd === cmd.toLowerCase();
      const C = cmd.toUpperCase();
      const ox = rel ? cx : 0;
      const oy = rel ? cy : 0;
      switch (C) {
        case 'M': {
          cx = ox + num(); cy = oy + num(); sx = cx; sy = cy;
          out.push(`M${P(cx, cy)}`);
          cmd = rel ? 'l' : 'L';
          lastC = lastQ = null;
          break;
        }
        case 'L': cx = ox + num(); cy = oy + num(); out.push(`L${P(cx, cy)}`); lastC = lastQ = null; break;
        case 'H': cx = ox + num(); out.push(`L${P(cx, cy)}`); lastC = lastQ = null; break;
        case 'V': cy = oy + num(); out.push(`L${P(cx, cy)}`); lastC = lastQ = null; break;
        case 'C': {
          const x1 = ox + num(), y1 = oy + num(), x2 = ox + num(), y2 = oy + num();
          cx = ox + num(); cy = oy + num();
          out.push(`C${P(x1, y1)} ${P(x2, y2)} ${P(cx, cy)}`);
          lastC = [x2, y2]; lastQ = null;
          break;
        }
        case 'S': {
          const x1 = lastC ? 2 * cx - lastC[0] : cx, y1 = lastC ? 2 * cy - lastC[1] : cy;
          const x2 = ox + num(), y2 = oy + num();
          cx = ox + num(); cy = oy + num();
          out.push(`C${P(x1, y1)} ${P(x2, y2)} ${P(cx, cy)}`);
          lastC = [x2, y2]; lastQ = null;
          break;
        }
        case 'Q': {
          const x1 = ox + num(), y1 = oy + num();
          cx = ox + num(); cy = oy + num();
          out.push(`Q${P(x1, y1)} ${P(cx, cy)}`);
          lastQ = [x1, y1]; lastC = null;
          break;
        }
        case 'T': {
          const x1 = lastQ ? 2 * cx - lastQ[0] : cx, y1 = lastQ ? 2 * cy - lastQ[1] : cy;
          cx = ox + num(); cy = oy + num();
          out.push(`Q${P(x1, y1)} ${P(cx, cy)}`);
          lastQ = [x1, y1]; lastC = null;
          break;
        }
        case 'A': {
          const rx = num(), ry = num(), rot = num(), large = flag(), sweep = flag();
          cx = ox + num(); cy = oy + num();
          out.push(`A${fmt(rx)} ${fmt(ry)} ${fmt(rot)} ${large} ${sweep} ${P(cx, cy)}`);
          lastC = lastQ = null;
          break;
        }
        case 'Z': cx = sx; cy = sy; out.push('Z'); lastC = lastQ = null; cmd = null; continue;
        default: throw new Error(`unsupported command ${cmd}`);
      }
      if (!more() && i < s.length && !/[A-Za-z]/.test(s[i])) throw new Error(`unexpected '${s[i]}' at ${i}`);
    }
    return out.join(' ');
  }

  /** Shapes of an SVG → path data (in the SVG's user units). */
  function shapeToPath(n) {
    const a = (k) => parseFloat(n.getAttribute(k) || '0');
    switch (n.tagName.toLowerCase()) {
      case 'path': return n.getAttribute('d') || '';
      case 'rect': {
        const x = a('x'), y = a('y'), w = a('width'), h = a('height');
        return `M${x} ${y} L${x + w} ${y} L${x + w} ${y + h} L${x} ${y + h} Z`;
      }
      case 'circle': case 'ellipse': {
        const cx = a('cx'), cy = a('cy');
        const rx = n.tagName === 'circle' ? a('r') : a('rx');
        const ry = n.tagName === 'circle' ? a('r') : a('ry');
        return `M${cx - rx} ${cy} A${rx} ${ry} 0 1 0 ${cx + rx} ${cy} A${rx} ${ry} 0 1 0 ${cx - rx} ${cy} Z`;
      }
      case 'line': return `M${a('x1')} ${a('y1')} L${a('x2')} ${a('y2')}`;
      case 'polyline': case 'polygon': {
        const pts = (n.getAttribute('points') || '').trim().split(/[\s,]+/).map(Number);
        let d = '';
        for (let k = 0; k + 1 < pts.length; k += 2) d += `${k ? 'L' : 'M'}${pts[k]} ${pts[k + 1]} `;
        return d + (n.tagName.toLowerCase() === 'polygon' ? 'Z' : '');
      }
      default: return '';
    }
  }

  function inherited(n, attr) {
    for (let p = n; p && p.getAttribute; p = p.parentNode) {
      const v = p.getAttribute(attr);
      if (v !== null) return v;
      const st = p.getAttribute('style');
      const m = st && new RegExp(`(?:^|;)\\s*${attr}\\s*:\\s*([^;]+)`).exec(st);
      if (m) return m[1].trim();
    }
    return null;
  }

  function svgColor(v) {
    if (!v || v === 'none') return null;
    if (v === 'currentColor') return '#FF1D1D1F';
    let m = /^#([0-9a-f]{3})$/i.exec(v);
    if (m) return '#FF' + m[1].split('').map((c) => c + c).join('').toUpperCase();
    m = /^#([0-9a-f]{6})$/i.exec(v);
    if (m) return '#FF' + m[1].toUpperCase();
    return '#FF1D1D1F';
  }

  function importSvg() {
    if (!canEdit) return;
    const dialog = document.createElement('dialog');
    dialog.innerHTML = '<form method="dialog" style="min-width: min(560px, 90vw)"><h2 style="margin-top:0">Paste an SVG</h2>' +
      '<p class="muted">An icon from Figma, Material Symbols, Lucide…: its paths, rects, circles and lines become path layers.</p>' +
      '<textarea style="width:100%;min-height:200px;font:12px ui-monospace,monospace"></textarea>' +
      '<p class="error" data-error></p><button value="ok">Import</button> <button value="cancel" formnovalidate>Cancel</button></form>';
    document.body.appendChild(dialog);
    dialog.addEventListener('close', () => {
      if (dialog.returnValue === 'ok') {
        const error = doImport(dialog.querySelector('textarea').value);
        if (error) {
          alert(error);
        }
      }
      dialog.remove();
    });
    dialog.showModal();
  }

  function doImport(text) {
    const doc = new DOMParser().parseFromString(text, 'image/svg+xml');
    const svgEl = doc.querySelector('svg');
    if (!svgEl || doc.querySelector('parsererror')) return 'That is not SVG markup.';
    const vb = (svgEl.getAttribute('viewBox') || '').trim().split(/[\s,]+/).map(Number);
    const [vx, vy, vw, vh] = vb.length === 4 ? vb : [0, 0, parseFloat(svgEl.getAttribute('width')) || 24, parseFloat(svgEl.getAttribute('height')) || 24];
    const shapes = [...svgEl.querySelectorAll('path, rect, circle, ellipse, line, polyline, polygon')];
    if (!shapes.length) return 'No shapes found in the SVG.';
    pushUndo();
    const size = 48;
    const x = Math.round((D().width - size) / 2);
    const y = Math.round((D().height - size) / 2);
    let added = 0;
    const errors = [];
    shapes.forEach((n) => {
      let d;
      try {
        d = normalizePath(shapeToPath(n), vx, vy);
      } catch (err) {
        errors.push(err.message);
        return;
      }
      if (!d) return;
      const fill = svgColor(inherited(n, 'fill') ?? '#000');
      const stroke = svgColor(inherited(n, 'stroke'));
      const sw = parseFloat(inherited(n, 'stroke-width') || '1');
      const e = Object.assign({}, DEFAULTS, {
        id: newId('icon'), type: 'path', name: `Icon ${added + 1}`, x, y, w: size, h: size, path: d, pathWidth: vw, pathHeight: vh,
        fill, stroke, strokeWidth: stroke ? sw : 0,
      });
      D().elements.push(e);
      selected = e.id;
      added++;
    });
    renderAll();
    scheduleSave();
    return errors.length ? `Imported ${added}; skipped ${errors.length}: ${errors[0]}` : null;
  }

  // For debugging in the browser console: the JSON the designer saves.
  window.composeCastDesigner = { definition: () => clean(), importSvg: (text) => doImport(text) };

  // ---------- start ----------
  if (!def.design) {
    stage.innerHTML = '';
    const p = document.createElement('div');
    p.className = 'card';
    p.innerHTML = '<p>This component is written in raw Remote Compose JSON, which the designer cannot open.</p>';
    if (canEdit) {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = 'Start a design (replaces the JSON body)';
      b.addEventListener('click', () => {
        if (!confirm('Replace the hand-written body with a new design?')) return;
        def.design = { width: 360, height: 120, cornerRadius: 16, elements: [] };
        normalize();
        renderComponent();
        renderAll();
        scheduleSave();
      });
      p.appendChild(b);
    }
    stage.appendChild(p);
    return;
  }
  setTool('select');
  renderComponent();
  renderAll();
})();
