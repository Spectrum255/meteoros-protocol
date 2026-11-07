/* =============================================================================
 * Meteoros Protocol · GUI 布局编辑器 —— 应用逻辑（原生 JS，无依赖）
 *
 * 纯静态：由 tools/gui-editor/server.py 托管，浏览器打开 http://127.0.0.1:8765/
 *
 * 数据流：
 *   GET  /api/layout/<id>          → 加载布局（失败回退 DEFAULT_LAYOUTS 并 warn）
 *   POST /api/layout/<id>          → 保存（服务端 400 返回 errors[]）
 *   POST /api/build                → 启动构建
 *   GET  /api/build/log?since&job  → 轮询构建日志
 *   GET  /assets/icons/<name>.png  → 图标
 *
 * 编辑操作：选中 / 拖动 / 右下角手柄改尺寸 / 吸附 2px、18px / 元素树顺序 /
 *           属性栏 / 6 条规则实时校验 / Ctrl+Z/Ctrl+Y / Ctrl+S / Delete / 方向键
 * ========================================================================== */
(function () {
  'use strict';

  var R = window.GuiRender;
  if (!R) {
    console.error('[gui-editor] primitives.js 未加载');
    return;
  }

  /* ═══════════════ 常量 ═══════════════ */

  var SCREENS = ['intake', 'machine_status'];
  var KNOWN_SCREEN_LABEL = {
    intake: 'intake · 粒子进气塔',
    machine_status: 'machine_status · 机器状态'
  };
  var TYPE_LABEL = {
    title: '标题', text: '文字', phase_rows: '相行区', bar_phase: '相/混合条',
    bar_energy: 'FE 条', slot: '物品槽', icon_button: '图标按钮',
    inventory: '玩家背包', spacer: '占位标记'
  };
  var ACTIONS = ['toggle', 'redstone', 'extract', 'auto_io', 'side_config'];
  var SLOT_ROLES = ['filter', 'canister'];
  var RESIZABLE = {
    phase_rows: true, bar_phase: true, bar_energy: true,
    spacer: true, icon_button: true
  };
  /* 画布标题演示文字：与 tools/preview_gui_mock.py 同一口径（JSON 的 text 只是占位标记，
     真正标题由语言文件 container.meteorosprotocol.<block> 提供） */
  var DEFAULT_TITLE = { intake: '粒子进气塔', machine_status: '热力发电机' };
  var STATUS_TEXT = '工作中';
  var INVENTORY_LABEL = '玩家背包';
  var PHASE_COUNT = R.PHASES.length; /* 7 */

  /* 规则 6：加载失败时的内置默认布局（= 两份 GUI JSON 的副本） */
  var DEFAULT_LAYOUTS = {
    intake: {
      schema: 1, id: 'intake', size: [176, 224],
      elements: [
        { type: 'title', x: 8, y: 6, text: 'dynamic', centered: true },
        { type: 'phase_rows', x: 8, y: 19, w: 62, h: 10, step: 11, visible: 7, scrollbar: { x: 71, y: 19, w: 2, h: 77 } },
        { type: 'bar_phase', x: 8, y: 100, w: 60, h: 6, channel: 'mixed' },
        { type: 'bar_energy', x: 8, y: 110, w: 96, h: 8 },
        { type: 'slot', x: 76, y: 20, role: 'filter' },
        { type: 'slot', x: 76, y: 44, role: 'canister' },
        { type: 'icon_button', x: 112, y: 18, size: 20, action: 'toggle' },
        { type: 'icon_button', x: 112, y: 40, size: 20, action: 'redstone' },
        { type: 'icon_button', x: 112, y: 62, size: 20, action: 'extract' },
        { type: 'icon_button', x: 132, y: 4, size: 20, action: 'auto_io' },
        { type: 'icon_button', x: 152, y: 4, size: 20, action: 'side_config' },
        { type: 'spacer', x: 134, y: 18, w: 34, h: 66, role: 'upgrade_band' },
        { type: 'inventory', label: [8, 128], rows: [140, 158, 176], hotbar: 198, step: 18 }
      ]
    },
    machine_status: {
      schema: 1, id: 'machine_status', size: [176, 224],
      elements: [
        { type: 'title', x: 8, y: 6, text: 'dynamic', centered: true },
        { type: 'bar_energy', x: 8, y: 26, w: 96, h: 8 },
        { type: 'bar_phase', x: 8, y: 37, w: 96, h: 4, channel: 'mixed', hide_when: 'hidesMixedBuffer' },
        { type: 'phase_rows', x: 8, y: 44, w: 96, h: 10, step: 11, visible: 5, scrollbar: { x: 106, y: 44, w: 2, h: 55 } },
        { type: 'text', x: 8, y: 104, role: 'status' },
        { type: 'icon_button', x: 112, y: 18, size: 20, action: 'toggle' },
        { type: 'icon_button', x: 112, y: 40, size: 20, action: 'redstone' },
        { type: 'icon_button', x: 132, y: 4, size: 20, action: 'auto_io' },
        { type: 'icon_button', x: 152, y: 4, size: 20, action: 'side_config' },
        { type: 'inventory', label: [8, 128], rows: [140, 158, 176], hotbar: 198, step: 18 }
      ]
    }
  };

  /* ═══════════════ DOM ═══════════════ */

  var dom = {};
  function $(id) { return document.getElementById(id); }

  function cacheDom() {
    dom.screenSelect = $('screen-select');
    dom.zoomGroup = $('zoom-group');
    dom.snapSelect = $('snap-select');
    dom.gridToggle = $('grid-toggle');
    dom.reloadBtn = $('reload-btn');
    dom.undoBtn = $('undo-btn');
    dom.redoBtn = $('redo-btn');
    dom.saveBtn = $('save-btn');
    dom.buildBtn = $('build-btn');
    dom.dirtyPill = $('dirty-pill');
    dom.serverPill = $('server-pill');

    dom.treeCount = $('tree-count');
    dom.treeList = $('tree-list');
    dom.treeEmpty = $('tree-empty');
    dom.addType = $('add-type');
    dom.addBtn = $('add-btn');
    dom.treeOps = $('tree-ops');

    dom.canvasTitle = $('canvas-title');
    dom.cursorReadout = $('cursor-readout');
    dom.compareLabel = $('compare-label');
    dom.compareToggle = $('compare-toggle');
    dom.canvasScroll = $('canvas-scroll');
    dom.canvasStage = $('canvas-stage');
    dom.canvas = $('canvas');
    dom.compareFrame = $('compare-frame');
    dom.compareImg = $('compare-img');
    dom.canvasError = $('canvas-error');
    dom.canvasErrorMsg = $('canvas-error-msg');
    dom.canvasErrorUrl = $('canvas-error-url');
    dom.retryBtn = $('retry-btn');
    dom.selectionReadout = $('selection-readout');

    dom.propsType = $('props-type');
    dom.propsBody = $('props-body');

    dom.validationSummary = $('validation-summary');
    dom.validationList = $('validation-list');
    dom.buildResult = $('build-result');
    dom.clearLog = $('clear-log');
    dom.log = $('log');
  }

  /* ═══════════════ 状态 ═══════════════ */

  var state = {
    screen: 'intake',
    layout: null,
    selection: null,
    zoom: 2,
    snap: 2,
    showGrid: false,
    hoverEl: null,
    drag: null,
    issues: [],
    issueMap: {},      /* elementIndex → 'error'|'warn' */
    serverErrors: [],
    serverOnline: null,
    fromFallback: false,
    savedJson: '',
    history: [],
    historyIndex: -1,
    icons: {},
    compare: { available: false },
    build: { job: null, since: 0, busy: false, timer: null },
    liveRaf: 0,
    canvasRaf: 0
  };

  var measureCanvas = document.createElement('canvas');
  var measureCtx = measureCanvas.getContext('2d');

  /* ═══════════════ 通用工具 ═══════════════ */

  function clone(obj) { return JSON.parse(JSON.stringify(obj)); }
  function num(v, d) { return (typeof v === 'number' && isFinite(v)) ? v : d; }
  function isInt(v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v; }
  function layoutW() { return (state.layout && state.layout.size && state.layout.size[0]) || 176; }
  function layoutH() { return (state.layout && state.layout.size && state.layout.size[1]) || 224; }
  function elements() { return (state.layout && state.layout.elements) || []; }
  function selIndex() {
    if (!state.layout || !state.selection) { return -1; }
    return state.layout.elements.indexOf(state.selection);
  }
  function measureW(text) {
    if (!text) { return 1; }
    return Math.max(1, R.textWidth(measureCtx, text, 8));
  }
  function el(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) { node.className = cls; }
    if (text != null) { node.textContent = text; }
    return node;
  }
  function fmtBytes(n) {
    if (typeof n !== 'number') { return '?'; }
    if (n >= 1024 * 1024) { return (n / 1024 / 1024).toFixed(2) + ' MiB'; }
    if (n >= 1024) { return (n / 1024).toFixed(1) + ' KiB'; }
    return n + ' B';
  }

  /* ═══════════════ 演示内容（与游戏运行时一致的口径） ═══════════════ */

  function demoTitleText(e) {
    var t = e.text;
    if (!t || t === 'dynamic') { return DEFAULT_TITLE[state.screen] || '热力发电机'; }
    return String(t);
  }
  function demoTextFor(e) {
    if (e.type === 'title') { return demoTitleText(e); }
    if (e.role === 'status') { return STATUS_TEXT; }
    if (e.role) { return '[' + e.role + ']'; }
    return 'text';
  }

  /* ═══════════════ 几何 ═══════════════ */

  function inventoryRect(e) {
    var step = num(e.step, 18);
    var label = Array.isArray(e.label) ? e.label : [8, 128];
    var rows = (Array.isArray(e.rows) && e.rows.length) ? e.rows : [140, 158, 176];
    var lx = num(label[0], 8);
    var ly = num(label[1], 128);
    var maxRow = 0;
    for (var i = 0; i < rows.length; i++) { maxRow = Math.max(maxRow, num(rows[i], 0)); }
    var hotbar = num(e.hotbar, maxRow + 22);
    var bottom = Math.max(hotbar, maxRow) + 18;
    return { x: lx - 1, y: ly, w: Math.max(1, 9 * step), h: Math.max(1, bottom - ly) };
  }

  /** 数据矩形（与服务端语义一致：phase_rows 用 step×visible 的外接矩形）。 */
  function dataRect(e) {
    var t = e.type;
    if (t === 'title' || t === 'text') {
      return { x: num(e.x, 0), y: num(e.y, 0), w: measureW(demoTextFor(e)), h: 10 };
    }
    if (t === 'phase_rows') {
      var step = num(e.step, 11);
      var vis = Math.max(1, num(e.visible, 1));
      return { x: num(e.x, 0), y: num(e.y, 0), w: Math.max(0, num(e.w, 0)), h: Math.max(num(e.h, 0), step * vis) };
    }
    if (t === 'inventory') { return inventoryRect(e); }
    if (t === 'slot') { return { x: num(e.x, 0), y: num(e.y, 0), w: 18, h: 18 }; }
    if (t === 'icon_button') {
      var s = num(e.size, 20);
      return { x: num(e.x, 0), y: num(e.y, 0), w: s, h: s };
    }
    return { x: num(e.x, 0), y: num(e.y, 0), w: Math.max(0, num(e.w, 0)), h: Math.max(0, num(e.h, 0)) };
  }

  /** 视觉矩形（title 居中、phase_rows 只到实际行底）。 */
  function visualRect(e) {
    if (e.type === 'title') {
      var w = measureW(demoTitleText(e));
      var x = e.centered ? Math.round((layoutW() - w) / 2) : num(e.x, 0);
      return { x: x, y: num(e.y, 0), w: w, h: 10 };
    }
    if (e.type === 'text') {
      return { x: num(e.x, 0), y: num(e.y, 0), w: measureW(demoTextFor(e)), h: 10 };
    }
    if (e.type === 'phase_rows') {
      var step = num(e.step, 11);
      var vis = Math.max(1, num(e.visible, 1));
      return { x: num(e.x, 0), y: num(e.y, 0), w: num(e.w, 0), h: (vis - 1) * step + num(e.h, 10) };
    }
    if (e.type === 'slot') {
      /* 游戏把 18x18 凹槽画在 (x-1, y-1)，选中框/命中区跟着画出来的框走 */
      return { x: num(e.x, 0) - 1, y: num(e.y, 0) - 1, w: 18, h: 18 };
    }
    return dataRect(e);
  }

  function pointIn(rect, x, y) {
    return x >= rect.x && y >= rect.y && x < rect.x + rect.w && y < rect.y + rect.h;
  }
  function intersects(a, b) {
    return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
  }

  /** 命中测试：返回 { el, part:'element'|'scrollbar' }，从最上层往下找。 */
  function hitTest(lx, ly) {
    var els = elements();
    for (var i = els.length - 1; i >= 0; i--) {
      var e = els[i];
      if (e.type === 'phase_rows') {
        var sb = e.scrollbar;
        if (sb && num(sb.w, 0) > 0 && num(sb.h, 0) > 0 && pointIn({ x: num(sb.x, 0), y: num(sb.y, 0), w: num(sb.w, 0), h: num(sb.h, 0) }, lx, ly)) {
          return { el: e, part: 'scrollbar' };
        }
      }
      if (pointIn(visualRect(e), lx, ly)) { return { el: e, part: 'element' }; }
    }
    return null;
  }

  /* ═══════════════ 历史（撤销 / 重做） ═══════════════ */

  function snapshot() {
    return { layout: clone(state.layout), selectionIndex: selIndex() };
  }

  function resetHistory() {
    state.history = [];
    state.historyIndex = -1;
    if (state.layout) { pushHistory(); }
    updateHistoryButtons();
  }

  function pushHistory() {
    if (!state.layout) { return; }
    var snap = snapshot();
    var json = JSON.stringify(snap.layout);
    var cur = state.history[state.historyIndex];
    if (cur && JSON.stringify(cur.layout) === json) { return; }
    state.history = state.history.slice(0, state.historyIndex + 1);
    state.history.push(snap);
    if (state.history.length > 200) { state.history.shift(); }
    state.historyIndex = state.history.length - 1;
    updateHistoryButtons();
  }

  function applyHistory(index) {
    var snap = state.history[index];
    if (!snap) { return; }
    state.historyIndex = index;
    state.layout = clone(snap.layout);
    var els = state.layout.elements || [];
    state.selection = (snap.selectionIndex >= 0 && snap.selectionIndex < els.length) ? els[snap.selectionIndex] : null;
    state.serverErrors = [];
    state.drag = null;
    refreshAll();
  }

  function undo() {
    if (state.historyIndex > 0) {
      applyHistory(state.historyIndex - 1);
      log('撤销（剩余 ' + state.historyIndex + ' 步可撤销）', 'line-dim');
    } else {
      log('没有可撤销的操作', 'line-dim');
    }
  }

  function redo() {
    if (state.historyIndex < state.history.length - 1) {
      applyHistory(state.historyIndex + 1);
      log('重做（可重做 ' + (state.history.length - 1 - state.historyIndex) + ' 步）', 'line-dim');
    } else {
      log('没有可重做的操作', 'line-dim');
    }
  }

  function updateHistoryButtons() {
    dom.undoBtn.disabled = !(state.historyIndex > 0);
    dom.redoBtn.disabled = !(state.historyIndex < state.history.length - 1);
  }

  /* ═══════════════ 脏状态 / 工具条 ═══════════════ */

  function updateToolbar() {
    var dirty = false;
    if (state.layout) {
      try { dirty = JSON.stringify(state.layout) !== state.savedJson; } catch (e) { dirty = true; }
    }
    if (!state.layout) {
      dom.dirtyPill.textContent = '—';
      dom.dirtyPill.className = 'pill';
    } else if (dirty) {
      dom.dirtyPill.textContent = '未保存';
      dom.dirtyPill.className = 'pill dirty';
    } else if (state.fromFallback) {
      dom.dirtyPill.textContent = '内置默认';
      dom.dirtyPill.className = 'pill dirty';
    } else {
      dom.dirtyPill.textContent = '已同步';
      dom.dirtyPill.className = 'pill clean';
    }
    updateHistoryButtons();
  }

  function setServerPill(kind, note) {
    var pill = dom.serverPill;
    if (kind === 'online') {
      pill.className = 'pill online';
      pill.textContent = '服务端在线';
    } else if (kind === 'offline') {
      pill.className = 'pill offline';
      pill.textContent = '服务端离线';
    } else {
      pill.className = 'pill';
      pill.textContent = '连接中…';
    }
    if (note) { pill.title = note; }
  }

  /* ═══════════════ 日志 ═══════════════ */

  function log(text, cls) {
    if (!dom.log) { return; }
    var line = document.createElement('div');
    line.className = cls || '';
    var d = new Date();
    var hh = ('0' + d.getHours()).slice(-2);
    var mm = ('0' + d.getMinutes()).slice(-2);
    var ss = ('0' + d.getSeconds()).slice(-2);
    line.textContent = '[' + hh + ':' + mm + ':' + ss + '] ' + text;
    dom.log.appendChild(line);
    while (dom.log.childElementCount > 600) { dom.log.removeChild(dom.log.firstChild); }
    dom.log.scrollTop = dom.log.scrollHeight;
  }

  function setBuildResult(text, isError) {
    dom.buildResult.textContent = text || '';
    dom.buildResult.className = 'build-result' + (isError ? ' is-error' : '');
  }

  /* ═══════════════ 网络 ═══════════════ */

  async function fetchJSON(url, opts) {
    var options = Object.assign({ cache: 'no-store' }, opts || {});
    var res = await fetch(url, options);
    var data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    return { res: res, data: data };
  }

  async function initServer() {
    setServerPill('checking');
    try {
      var r = await fetchJSON('/api/screens');
      if (r.res.ok && r.data && r.data.ok) {
        state.serverOnline = true;
        setServerPill('online');
        applyScreens(r.data.screens || []);
        log('服务端就绪 · 屏幕：' + (r.data.screens || []).join(', '), 'line-ok');
      } else {
        state.serverOnline = false;
        setServerPill('offline', 'HTTP ' + r.res.status);
        log('服务端响应异常（HTTP ' + r.res.status + '）', 'line-warn');
      }
    } catch (err) {
      state.serverOnline = false;
      setServerPill('offline', err.message);
      log('服务端离线：' + err.message, 'line-warn');
    }
  }

  function applyScreens(screens) {
    var current = state.screen;
    dom.screenSelect.innerHTML = '';
    var list = (screens && screens.length) ? screens.slice() : SCREENS.slice();
    list.forEach(function (id) {
      var opt = document.createElement('option');
      opt.value = id;
      opt.textContent = KNOWN_SCREEN_LABEL[id] || id;
      dom.screenSelect.appendChild(opt);
    });
    if (list.indexOf(current) < 0 && list.length) { current = list[0]; }
    dom.screenSelect.value = current;
  }

  /* ═══════════════ 图标 ═══════════════ */

  function loadIcons() {
    R.ICON_NAMES.forEach(function (name) {
      var img = new Image();
      img.onload = function () {
        state.icons[name] = img;
        scheduleCanvas();
      };
      img.onerror = function () { state.icons[name] = null; };
      img.src = '/assets/icons/' + name + '.png';
    });
  }

  /* ═══════════════ 加载布局 ═══════════════ */

  async function loadLayout(id, quiet) {
    if (!quiet) { log('加载布局 ' + id + ' …', 'line-dim'); }
    state.screen = id;
    var r;
    try {
      r = await fetchJSON('/api/layout/' + encodeURIComponent(id));
    } catch (err) {
      fallback(id, '无法连接服务端：' + err.message);
      return;
    }
    if (!r.res.ok || !r.data || !r.data.ok || !r.data.layout) {
      var msg = (r.data && r.data.error) ? r.data.error : ('HTTP ' + r.res.status);
      fallback(id, msg);
      return;
    }
    var layout = r.data.layout;
    if (layout.schema !== 1 || !Array.isArray(layout.elements)) {
      fallback(id, '布局结构不合法（schema !== 1 或 elements 不是数组）');
      return;
    }
    layout.id = id;
    state.layout = layout;
    state.savedJson = JSON.stringify(layout);
    state.fromFallback = false;
    state.selection = null;
    state.serverErrors = [];
    state.hoverEl = null;
    state.drag = null;
    hideCanvasError();
    setServerPill('online');
    resetHistory();
    refreshAll();
    if (!quiet) {
      log('已加载 ' + id + '.json · ' + layout.elements.length + ' 个元素 · ' + layoutW() + '×' + layoutH(), 'line-ok');
    }
    probeCompare(id);
  }

  function fallback(id, message) {
    var base = DEFAULT_LAYOUTS[id] || DEFAULT_LAYOUTS.intake;
    state.layout = clone(base);
    state.layout.id = id;
    state.savedJson = JSON.stringify(state.layout);
    state.fromFallback = true;
    state.selection = null;
    state.hoverEl = null;
    state.drag = null;
    state.serverErrors = [];
    resetHistory();
    refreshAll();
    showCanvasError(message);
    log('加载 ' + id + ' 失败：' + message + ' —— 已回退内置默认布局（规则 6）', 'line-error');
  }

  function showCanvasError(message) {
    dom.canvasErrorMsg.textContent = message || '未知错误';
    dom.canvasErrorUrl.textContent = location.origin + '/api/layout/' + state.screen;
    dom.canvasError.hidden = false;
    if (!dom.canvasError.querySelector('[data-action="fallback-continue"]')) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn';
      btn.setAttribute('data-action', 'fallback-continue');
      btn.textContent = '编辑内置默认';
      btn.addEventListener('click', function () {
        dom.canvasError.hidden = true;
        log('已切换到内置默认布局（保存会写回 ' + state.screen + '.json）', 'line-warn');
      });
      var actions = dom.canvasError.querySelector('.canvas-error-actions');
      if (actions) { actions.appendChild(btn); }
    }
  }

  function hideCanvasError() {
    dom.canvasError.hidden = true;
  }

  async function probeCompare(id) {
    state.compare.available = false;
    dom.compareToggle.disabled = true;
    dom.compareToggle.checked = false;
    dom.compareFrame.hidden = true;
    try {
      var res = await fetch('/assets/preview/' + encodeURIComponent(id) + '.png', { cache: 'no-store' });
      if (res.ok) {
        state.compare.available = true;
        dom.compareToggle.disabled = false;
        dom.compareLabel.title = '并排显示服务端渲染的对照图';
        log('检测到 /assets/preview/' + id + '.png，可开启「服务端对照图」', 'line-dim');
        return;
      }
    } catch (e) { /* 忽略 */ }
    dom.compareLabel.title = '服务端未提供 /assets/preview/' + id + '.png（可选功能）';
  }

  /* ═══════════════ 刷新 ═══════════════ */

  function computeIssues() {
    var issues = [];
    var layout = state.layout;
    var els = layout ? (layout.elements || []) : [];
    var lw = layoutW();
    var lh = layoutH();
    var i, j, e, r;

    function add(sev, rule, msg, index) {
      issues.push({ sev: sev, rule: rule, msg: msg, index: index == null ? -1 : index });
    }

    for (i = 0; i < els.length; i++) {
      e = els[i];
      var tag = '#' + (i + 1) + ' ' + (e.type || '(无 type)');

      /* 规则 6：未知类型 / 缺字段 → 加载器会忽略并 warn */
      if (!TYPE_LABEL[e.type]) {
        add('warn', '6', tag + ' 的 type 不在布局规范内，加载器会忽略它并输出 warn', i);
        continue;
      }

      /* 规则 1：边界 */
      r = dataRect(e);
      if (e.type === 'title' || e.type === 'text') {
        if (num(e.x, -1) < 0 || num(e.y, -1) < 0 || num(e.y, 0) + 10 > lh || num(e.x, 0) >= lw) {
          add('error', '1', tag + ' 越界：起点 (' + num(e.x, 0) + ',' + num(e.y, 0) + ')，y+10 必须 ≤ ' + lh, i);
        }
      } else if (e.type !== 'inventory') {
        if (r.w <= 0 || r.h <= 0) {
          add('error', '1', tag + ' 尺寸必须 > 0（当前 ' + r.w + '×' + r.h + '）', i);
        } else if (r.x < 0 || r.y < 0 || r.x + r.w > lw || r.y + r.h > lh) {
          add('error', '1', tag + ' 越界：(' + r.x + ',' + r.y + ') ' + r.w + '×' + r.h + '，超出 ' + lw + '×' + lh, i);
        }
      } else {
        if (r.x < 0 || r.y < 0 || r.x + r.w > lw || r.y + r.h > lh) {
          add('error', '1', tag + ' 越界：label+行区合计 ' + r.w + '×' + r.h + '，超出 ' + lw + '×' + lh, i);
        }
      }

      /* 规则 2：18px 网格（inventory 内部；独立 slot 只要求整数） */
      if (e.type === 'inventory') {
        var step = num(e.step, 0);
        if (step !== 18) {
          add('error', '2', tag + ' 的 step 必须为 18（当前 ' + step + '）', i);
        }
        var rows = Array.isArray(e.rows) ? e.rows : null;
        if (!rows || !rows.length) {
          add('error', '2', tag + ' 的 rows 必须是非空数组', i);
        } else {
          if (rows.length !== 3) {
            add('warn', '2', tag + ' 应为 3 行（当前 ' + rows.length + ' 行）', i);
          }
          for (j = 1; j < rows.length; j++) {
            if (num(rows[j], 0) - num(rows[j - 1], 0) !== 18) {
              add('error', '2', tag + ' 的第 ' + (j + 1) + ' 行与上一行间距应为 18（当前 ' + (num(rows[j], 0) - num(rows[j - 1], 0)) + '）', i);
            }
          }
          if (e.hotbar != null) {
            var gap = num(e.hotbar, 0) - num(rows[rows.length - 1], 0);
            if (gap !== 18 && gap !== 22) {
              add('warn', '2', tag + ' 的快捷栏 y 与最后一行间距 ' + gap + '（规范：18 或 22）', i);
            } else if (gap !== 22) {
              add('warn', '2', tag + ' 的快捷栏 y 与最后一行间距 ' + gap + '（规范建议 22）', i);
            }
          }
        }
      } else if (e.type === 'slot') {
        if (!isInt(e.x) || !isInt(e.y)) {
          add('warn', '2', tag + ' 的坐标应为整数（当前 ' + e.x + ',' + e.y + '）', i);
        }
      }

      /* 规则 3：相行区不与其它元素重叠（服务端语义：phase_rows 外接矩形） */
      if (e.type === 'phase_rows') {
        var pr = dataRect(e);
        for (j = 0; j < els.length; j++) {
          if (j === i) { continue; }
          var other = els[j];
          if (other.type === 'title' || other.type === 'text') { continue; }
          if (other.type === 'inventory') { continue; } /* 见下方单独提示 */
          var or = dataRect(other);
          if (or.w > 0 && or.h > 0 && intersects(pr, or)) {
            add('error', '3', tag + ' 的行区与 #' + (j + 1) + ' ' + other.type + ' 重叠', i);
          }
        }
        var vis = Math.max(1, num(e.visible, PHASE_COUNT));
        if (vis > PHASE_COUNT) {
          add('warn', '3', tag + ' 的 visible=' + vis + ' 超过相数 ' + PHASE_COUNT + '，游戏只会绘制 ' + PHASE_COUNT + ' 行', i);
        }
        if (vis < PHASE_COUNT && !e.scrollbar) {
          add('warn', '3', tag + ' 的 visible < ' + PHASE_COUNT + ' 却没有 scrollbar，游戏不会出现滚动条', i);
        }
        if (e.scrollbar) {
          if (!(num(e.scrollbar.w, 0) > 0) || !(num(e.scrollbar.h, 0) > 0)) {
            add('warn', '1', tag + ' 的 scrollbar 宽/高必须 > 0', i);
          } else if (num(e.scrollbar.x, 0) < 0 || num(e.scrollbar.y, 0) < 0 ||
            num(e.scrollbar.x, 0) + num(e.scrollbar.w, 0) > lw ||
            num(e.scrollbar.y, 0) + num(e.scrollbar.h, 0) > lh) {
            add('error', '1', tag + ' 的 scrollbar 越界', i);
          }
          if (vis >= PHASE_COUNT) {
            add('info', '3', tag + ' visible≥' + PHASE_COUNT + '，游戏不画 scrollbar（编辑器选中时显示虚线参考框）', i);
          }
        }
        if (num(e.step, 11) <= num(e.h, 10)) {
          add('warn', '3', tag + ' 的 step(' + num(e.step, 11) + ') 应大于单行高 h(' + num(e.h, 10) + ')，否则各行会互相覆盖', i);
        }
      }

      if (e.type === 'bar_phase' && e.channel !== 'mixed') {
        add('warn', '3', tag + ' 的 channel 通常应为 "mixed"（当前 ' + e.channel + '）', i);
      }
      if (e.type === 'bar_energy' && e.hide_when) {
        add('warn', '3', tag + ' 是 bar_energy，hide_when 只用于 bar_phase', i);
      }
      if (e.type === 'spacer' && !e.role) {
        add('info', '3', tag + ' 建议填写 role（占位用途说明）', i);
      }
      if (e.type === 'icon_button' && ACTIONS.indexOf(e.action) < 0) {
        add('error', '4', tag + ' 的 action "' + e.action + '" 不在允许集合 {' + ACTIONS.join(', ') + '} 内', i);
      }
      if (e.type === 'slot' && SLOT_ROLES.indexOf(e.role) < 0) {
        add('error', '5', tag + ' 的 role "' + e.role + '" 不在允许集合 {' + SLOT_ROLES.join(', ') + '} 内', i);
      }
    }

    /* 规则 4：icon_button 的 action 唯一 */
    var actionSeen = {};
    els.forEach(function (item, index) {
      if (item.type !== 'icon_button' || !item.action) { return; }
      if (actionSeen[item.action] != null) {
        add('error', '4', '#' + (actionSeen[item.action] + 1) + ' 与 #' + (index + 1) + ' 的 action="' + item.action + '" 重复（每个 action 只能有一个按钮）', index);
      } else {
        actionSeen[item.action] = index;
      }
    });

    /* 规则 5：slot 的 role 唯一 */
    var roleSeen = {};
    els.forEach(function (item, index) {
      if (item.type !== 'slot' || !item.role) { return; }
      if (roleSeen[item.role] != null) {
        add('error', '5', '#' + (roleSeen[item.role] + 1) + ' 与 #' + (index + 1) + ' 的 slot role="' + item.role + '" 重复', index);
      } else {
        roleSeen[item.role] = index;
      }
    });

    /* 排序：error → warn → info */
    var rank = { error: 0, warn: 1, info: 2 };
    issues.sort(function (a, b) {
      if (rank[a.sev] !== rank[b.sev]) { return rank[a.sev] - rank[b.sev]; }
      return a.index - b.index;
    });

    state.issues = issues;
    state.issueMap = {};
    issues.forEach(function (item) {
      if (item.index < 0) { return; }
      var prev = state.issueMap[item.index];
      if (prev === 'error') { return; }
      if (item.sev === 'error' || item.sev === 'warn') {
        state.issueMap[item.index] = item.sev;
      }
    });
    return issues;
  }

  /* ═══════════════ 渲染：画布 ═══════════════ */

  function scheduleCanvas() {
    if (state.canvasRaf) { return; }
    state.canvasRaf = requestAnimationFrame(function () {
      state.canvasRaf = 0;
      renderCanvas();
    });
  }

  function renderCanvas() {
    var layout = state.layout;
    if (!layout) { return; }
    var z = state.zoom;
    var lw = layoutW();
    var lh = layoutH();
    var canvas = dom.canvas;
    var bw = Math.max(1, Math.round(lw * z));
    var bh = Math.max(1, Math.round(lh * z));
    if (canvas.width !== bw || canvas.height !== bh) {
      canvas.width = bw;
      canvas.height = bh;
    }
    var ctx = canvas.getContext('2d');
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.imageSmoothingEnabled = false;
    ctx.fillStyle = '#000000';
    ctx.fillRect(0, 0, bw, bh);
    ctx.setTransform(z, 0, 0, z, 0, 0);
    ctx.imageSmoothingEnabled = false;

    /* 背景 = GUI 面板本身（与游戏一致：整个界面是 176×224 的 panel） */
    R.panel(ctx, 0, 0, lw, lh);

    var els = layout.elements || [];
    for (var i = 0; i < els.length; i++) {
      try {
        paintElement(ctx, els[i], i);
      } catch (err) {
        console.warn('[gui-editor] 元素 #' + (i + 1) + ' 绘制失败', els[i], err);
        var fallbackRect = dataRect(els[i]);
        R.unknown(ctx, fallbackRect.x, fallbackRect.y, Math.max(8, fallbackRect.w), Math.max(8, fallbackRect.h), els[i].type);
      }
    }

    /* 编辑覆盖层：用设备像素画，保证任意缩放下 1px 清晰 */
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    drawGrid(ctx);
    drawOverlay(ctx);
  }

  function paintElement(ctx, e, index) {
    var x = num(e.x, 0);
    var y = num(e.y, 0);
    switch (e.type) {
      case 'title':
        R.title(ctx, x, y, demoTitleText(e), !!e.centered, layoutW());
        break;
      case 'text':
        R.label(ctx, x, y, demoTextFor(e), R.COLORS.LABEL_TEXT, 8);
        break;
      case 'phase_rows':
        paintPhaseRows(ctx, e);
        break;
      case 'bar_phase':
        R.phaseBarH(ctx, x, y, num(e.w, 0), num(e.h, 0), R.PHASE_BAR_RATIO, R.COLORS.MIXED_FILL, R.COLORS.MIXED_EMPTY);
        break;
      case 'bar_energy':
        R.barH(ctx, x, y, num(e.w, 0), num(e.h, 0), R.FE_RATIO, R.COLORS.ENERGY_FILL);
        R.drawCenteredText(ctx, R.FE_TEXT, x, y, num(e.w, 0), num(e.h, 0), '#ffffff', { px: 7, shadow: true });
        break;
      case 'slot':
        R.slot(ctx, x, y);
        break;
      case 'icon_button':
        var iconName = R.ICON_FOR_ACTION[e.action];
        R.iconButton(ctx, x, y, num(e.size, 20), iconName ? state.icons[iconName] : null, true);
        break;
      case 'inventory':
        var label = Array.isArray(e.label) ? e.label : [8, 128];
        var rows = (Array.isArray(e.rows) && e.rows.length) ? e.rows : [140, 158, 176];
        R.inventory(ctx, num(label[0], 8), num(label[1], 128), rows, e.hotbar != null ? num(e.hotbar) : null, num(e.step, 18), INVENTORY_LABEL);
        break;
      case 'spacer':
        R.spacer(ctx, x, y, num(e.w, 0), num(e.h, 0), e.role);
        break;
      default:
        R.unknown(ctx, x, y, num(e.w, 24), num(e.h, 12), e.type);
        break;
    }
  }

  function paintPhaseRows(ctx, e) {
    var x = num(e.x, 0);
    var y = num(e.y, 0);
    var w = num(e.w, 0);
    var h = num(e.h, 10);
    var step = num(e.step, 11);
    var visible = Math.max(1, Math.min(num(e.visible, 1), 12));
    for (var i = 0; i < visible; i++) {
      var phase = R.PHASES[i % PHASE_COUNT];
      var amount = R.FAKE_AMOUNTS[i % R.FAKE_AMOUNTS.length];
      R.phaseRow(ctx, x, y + i * step, w, h, phase.color, amount, i === R.SELECTED_ROW);
    }
    if (e.scrollbar && PHASE_COUNT > visible) {
      R.scrollbar(ctx, num(e.scrollbar.x, 0), num(e.scrollbar.y, 0), num(e.scrollbar.w, 0), num(e.scrollbar.h, 0), PHASE_COUNT, visible, 0);
    }
  }

  /* ═══════════════ 渲染：覆盖层（网格 / 悬停 / 选中 / 手柄） ═══════════════ */

  function drawGrid(ctx) {
    if (!state.showGrid) { return; }
    var snap = state.snap;
    if (!(snap > 0)) { return; }
    var z = state.zoom;
    if (snap === 2 && z < 2) { return; } /* 1× 下 2px 网格太密，跳过 */
    var lw = layoutW();
    var lh = layoutH();
    var major = snap === 18;
    ctx.strokeStyle = major ? 'rgba(115,251,253,0.16)' : 'rgba(115,251,253,0.07)';
    ctx.lineWidth = 1;
    var stepDev = snap * z;
    var x, y;
    ctx.beginPath();
    for (x = stepDev; x < lw * z; x += stepDev) {
      var px = Math.round(x) + 0.5;
      ctx.moveTo(px, 0);
      ctx.lineTo(px, lh * z);
    }
    for (y = stepDev; y < lh * z; y += stepDev) {
      var py = Math.round(y) + 0.5;
      ctx.moveTo(0, py);
      ctx.lineTo(lw * z, py);
    }
    ctx.stroke();
  }

  function drawOverlay(ctx) {
    var z = state.zoom;
    var hover = state.hoverEl;
    if (hover && hover !== state.selection) {
      strokeRectDevice(ctx, visualRect(hover), z, 'rgba(255,255,255,0.45)', [3, 3]);
    }
    var sel = state.selection;
    if (!sel) { return; }
    var rect = visualRect(sel);

    /* 主选中框 */
    strokeRectDevice(ctx, rect, z, '#73fbfd', null);

    /* phase_rows：标出整个行区与 scrollbar */
    if (sel.type === 'phase_rows') {
      if (sel.scrollbar) {
        strokeRectDevice(ctx, {
          x: num(sel.scrollbar.x, 0), y: num(sel.scrollbar.y, 0),
          w: num(sel.scrollbar.w, 0), h: num(sel.scrollbar.h, 0)
        }, z, 'rgba(115,251,253,0.85)', [2, 2]);
      }
      overlayLabel(ctx, '行区高 ' + Math.round(rect.h) + 'px（step ' + num(sel.step, 11) + ' × visible ' + num(sel.visible, 1) + '）',
        rect.x * z, rect.y * z - 16);
    } else if (sel.type === 'inventory') {
      overlayLabel(ctx, 'label ' + (sel.label ? sel.label.join(',') : '—') + ' · 9×18 网格',
        rect.x * z, rect.y * z - 16);
    }

    /* 尺寸手柄（仅可改尺寸的类型） */
    if (RESIZABLE[sel.type]) {
      var hx = Math.round((rect.x + rect.w) * z) - 9;
      var hy = Math.round((rect.y + rect.h) * z) - 9;
      ctx.fillStyle = '#73fbfd';
      ctx.fillRect(hx, hy, 8, 8);
      ctx.strokeStyle = '#101012';
      ctx.lineWidth = 1;
      ctx.strokeRect(hx + 0.5, hy + 0.5, 7, 7);
    }

    /* 元素标签 */
    var labelText = '#' + (selIndex() + 1) + ' ' + sel.type;
    if (RESIZABLE[sel.type] && sel.type !== 'icon_button') {
      labelText += ' ' + Math.round(rect.w) + '×' + Math.round(rect.h);
    } else if (sel.type === 'icon_button') {
      labelText += ' ' + num(sel.size, 20) + 'px';
    }
    overlayLabel(ctx, labelText, rect.x * z, rect.y * z - 32);
  }

  function strokeRectDevice(ctx, rect, z, color, dash) {
    if (!rect || rect.w <= 0 || rect.h <= 0) { return; }
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    if (dash) { ctx.setLineDash(dash); }
    ctx.strokeRect(Math.round(rect.x * z) + 0.5, Math.round(rect.y * z) + 0.5,
      Math.max(1, Math.round(rect.w * z) - 1), Math.max(1, Math.round(rect.h * z) - 1));
    ctx.restore();
  }

  function overlayLabel(ctx, text, dx, dy) {
    if (!text) { return; }
    var font = '10px "Consolas", "Courier New", monospace';
    ctx.save();
    ctx.font = font;
    var w = Math.ceil(ctx.measureText(text).width) + 8;
    var x = Math.max(2, Math.min(dx, dom.canvas.width - w - 2));
    var y = Math.max(2, dy);
    if (y > dom.canvas.height - 14) { y = Math.max(2, dom.canvas.height - 14); }
    ctx.fillStyle = 'rgba(0,0,0,0.74)';
    ctx.fillRect(x, y, w, 13);
    ctx.fillStyle = '#73fbfd';
    ctx.font = font;
    ctx.textBaseline = 'middle';
    ctx.fillText(text, x + 4, y + 7);
    ctx.restore();
  }

  /* ═══════════════ 渲染：元素树 ═══════════════ */

  function treeMeta(e) {
    switch (e.type) {
      case 'title':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · ' + (e.centered ? '居中' : '左对齐') + ' · ' + demoTitleText(e);
      case 'text':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · role=' + (e.role || '—');
      case 'phase_rows':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · ' + num(e.step, 11) + '×' + num(e.visible, 1) + ' · w' + num(e.w, 0);
      case 'bar_phase':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · ' + num(e.w, 0) + '×' + num(e.h, 0) + ' · ' + (e.channel || '—');
      case 'bar_energy':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · ' + num(e.w, 0) + '×' + num(e.h, 0);
      case 'slot':
        return 'role=' + (e.role || '—') + ' · (' + num(e.x, 0) + ',' + num(e.y, 0) + ')';
      case 'icon_button':
        return (e.action || '—') + ' · ' + num(e.size, 20) + 'px · (' + num(e.x, 0) + ',' + num(e.y, 0) + ')';
      case 'inventory':
        return 'label(' + (e.label ? e.label.join(',') : '—') + ') · ' + ((e.rows || []).length) + ' 行 · 快捷栏 ' + num(e.hotbar, 0);
      case 'spacer':
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ') · ' + num(e.w, 0) + '×' + num(e.h, 0) + ' · ' + (e.role || '—');
      default:
        return '(' + num(e.x, 0) + ',' + num(e.y, 0) + ')';
    }
  }

  function renderTree() {
    var list = dom.treeList;
    var scroller = list.parentElement;
    var scrollTop = scroller ? scroller.scrollTop : 0;
    var els = elements();
    dom.treeCount.textContent = els.length + ' 个';
    dom.treeEmpty.hidden = els.length > 0;
    list.innerHTML = '';
    els.forEach(function (e, i) {
      var li = document.createElement('li');
      li.className = 'tree-row';
      if (e === state.selection) { li.classList.add('is-selected'); }
      var sev = state.issueMap[i];
      if (sev === 'error') { li.classList.add('t-error'); } else if (sev === 'warn') { li.classList.add('t-warn'); }
      li.appendChild(el('span', 't-index', '#' + (i + 1)));
      li.appendChild(el('span', 't-type', e.type || '?'));
      li.appendChild(el('span', 't-meta', treeMeta(e)));
      li.title = treeMeta(e);
      li.addEventListener('click', function () { selectElement(e); });
      list.appendChild(li);
    });
    if (scroller) { scroller.scrollTop = scrollTop; }
  }

  /* ═══════════════ 选择 ═══════════════ */

  function selectElement(e) {
    if (state.selection === e) { return; }
    state.selection = e;
    state.drag = null;
    refreshAll();
  }

  /* ═══════════════ 渲染：属性栏 ═══════════════ */

  function propSection(title, badge) {
    var sec = el('div', 'prop-section');
    var head = el('div', 'prop-section-head');
    head.appendChild(el('span', null, title));
    if (badge) { head.appendChild(el('span', 'badge', badge)); }
    sec.appendChild(head);
    return sec;
  }

  function propFields(sec) {
    var fields = el('div', 'prop-fields');
    sec.appendChild(fields);
    return fields;
  }

  /** 数值输入：input 实时更新（不打断焦点），change（失焦/回车）提交一条历史。 */
  function addNumber(fields, labelText, getter, setter, opts) {
    opts = opts || {};
    var wrap = el('label', 'prop-field' + (opts.wide ? ' wide' : ''));
    wrap.appendChild(el('span', null, labelText));
    var input = document.createElement('input');
    input.type = 'number';
    input.step = opts.step != null ? opts.step : 1;
    if (opts.min != null) { input.min = opts.min; }
    if (opts.max != null) { input.max = opts.max; }
    input.value = String(getter());
    input.addEventListener('input', function () {
      var v = input.valueAsNumber;
      if (!isFinite(v)) { return; }
      setter(v);
      scheduleLive();
    });
    input.addEventListener('change', function () {
      var v = input.valueAsNumber;
      if (!isFinite(v)) { return; }
      setter(v);
      pushHistory();
      scheduleLive();
    });
    input.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter') { input.blur(); }
    });
    wrap.appendChild(input);
    fields.appendChild(wrap);
    return input;
  }

  function addText(fields, labelText, getter, setter, opts) {
    opts = opts || {};
    var wrap = el('label', 'prop-field' + (opts.wide ? ' wide' : ''));
    wrap.appendChild(el('span', null, labelText));
    var input = document.createElement('input');
    input.type = 'text';
    input.value = getter() == null ? '' : String(getter());
    if (opts.placeholder) { input.placeholder = opts.placeholder; }
    input.addEventListener('input', function () { setter(input.value); scheduleLive(); });
    input.addEventListener('change', function () { setter(input.value); pushHistory(); scheduleLive(); });
    input.addEventListener('keydown', function (ev) { if (ev.key === 'Enter') { input.blur(); } });
    wrap.appendChild(input);
    fields.appendChild(wrap);
    return input;
  }

  function addSelect(fields, labelText, options, getter, setter, opts) {
    opts = opts || {};
    var wrap = el('label', 'prop-field' + (opts.wide ? ' wide' : ''));
    wrap.appendChild(el('span', null, labelText));
    var select = document.createElement('select');
    options.forEach(function (opt) {
      var o = document.createElement('option');
      o.value = opt;
      o.textContent = opts.labels && opts.labels[opt] ? opts.labels[opt] : opt;
      select.appendChild(o);
    });
    select.value = getter() == null ? '' : String(getter());
    select.addEventListener('change', function () { setter(select.value); pushHistory(); scheduleLive(); });
    wrap.appendChild(select);
    fields.appendChild(wrap);
    return select;
  }

  function addCheck(fields, labelText, getter, setter) {
    var wrap = el('label', 'prop-field check-field');
    var input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = !!getter();
    input.addEventListener('change', function () { setter(input.checked); pushHistory(); scheduleLive(); });
    wrap.appendChild(input);
    wrap.appendChild(el('span', null, labelText));
    fields.appendChild(wrap);
    return input;
  }

  function addHint(sec, text, warn) {
    sec.appendChild(el('p', 'prop-hint' + (warn ? ' warn' : ''), text));
  }

  /** 只读字段：展示不可编辑的字段值（如 title 的文字来源），不参与编辑与历史。 */
  function addReadonly(fields, labelText, getter) {
    var wrap = el('div', 'prop-field wide');
    wrap.appendChild(el('span', null, labelText));
    var value = el('span', 'prop-value', getter() == null ? '—' : String(getter()));
    wrap.appendChild(value);
    fields.appendChild(wrap);
    return value;
  }

  function addActions(sec, actions) {
    var wrap = el('div', 'prop-actions');
    actions.forEach(function (action) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn tiny' + (action.danger ? ' danger' : '');
      btn.textContent = action.label;
      btn.addEventListener('click', action.run);
      wrap.appendChild(btn);
    });
    sec.appendChild(wrap);
  }

  function renderProps() {
    var body = dom.propsBody;
    body.innerHTML = '';
    var e = state.selection;
    if (!e) {
      dom.propsType.textContent = '—';
      var empty = el('div', 'props-empty');
      empty.appendChild(el('h3', null, '未选中元素'));
      empty.appendChild(el('p', null, '在画布中点选一个元素，或在左侧元素树里点击。'));
      empty.appendChild(el('p', null, '快捷键： Ctrl+S 保存 · Ctrl+Z/Y 撤销/重做 · Delete 删除 · 方向键微调 · Shift+方向键 10px'));
      body.appendChild(empty);
      return;
    }
    var index = selIndex();
    dom.propsType.textContent = '#' + (index + 1) + ' · ' + e.type;

    /* ── 基本：位置与尺寸 ── */
    var sec = propSection('位置与尺寸', TYPE_LABEL[e.type] || e.type);
    var fields = propFields(sec);
    if (e.type === 'inventory') {
      var label = Array.isArray(e.label) ? e.label : [8, 128];
      addNumber(fields, 'label x', function () { return num(label[0], 0); }, function (v) {
        if (!Array.isArray(e.label)) { e.label = [8, 128]; }
        e.label[0] = v;
      });
      addNumber(fields, 'label y', function () { return num(label[1], 0); }, function (v) {
        if (!Array.isArray(e.label)) { e.label = [8, 128]; }
        e.label[1] = v;
      });
      var rows = Array.isArray(e.rows) ? e.rows : (e.rows = [140, 158, 176]);
      rows.forEach(function (_, ri) {
        addNumber(fields, '行 ' + (ri + 1) + ' y', function () { return num(rows[ri], 0); }, function (v) {
          e.rows[ri] = v;
        });
      });
      addNumber(fields, '快捷栏 y', function () { return num(e.hotbar, 0); }, function (v) { e.hotbar = v; });
      addNumber(fields, 'step', function () { return num(e.step, 18); }, function (v) { e.step = v; }, { min: 1 });
    } else {
      addNumber(fields, 'x', function () { return num(e.x, 0); }, function (v) { e.x = v; });
      addNumber(fields, 'y', function () { return num(e.y, 0); }, function (v) { e.y = v; });
      if (e.type === 'icon_button') {
        addNumber(fields, 'size', function () { return num(e.size, 20); }, function (v) { e.size = v; }, { min: 8, max: 64 });
      } else if (RESIZABLE[e.type]) {
        addNumber(fields, 'w', function () { return num(e.w, 0); }, function (v) { e.w = v; }, { min: 1 });
        addNumber(fields, 'h', function () { return num(e.h, 0); }, function (v) { e.h = v; }, { min: 1 });
      }
    }
    body.appendChild(sec);

    /* ── 类型专属字段 ── */
    if (e.type === 'title') {
      var secTitle = propSection('标题', '文字由语言文件提供');
      var fTitle = propFields(secTitle);
      addCheck(fTitle, 'centered（水平居中）', function () { return e.centered; }, function (v) { e.centered = v; });
      addReadonly(fTitle, 'text（只读）', function () {
        return (e.text && e.text !== 'dynamic') ? String(e.text) : 'dynamic · 由语言文件提供';
      });
      addHint(secTitle, '标题文字来自语言文件（container.meteorosprotocol.<block>），JSON 里的 text 只是占位标记，编辑器不修改它。画布演示文字：intake →「粒子进气塔」，其它界面 →「热力发电机」（与 preview_gui_mock.py 一致）。centered=true 时按整宽居中（x 不参与渲染）。');
      body.appendChild(secTitle);
    } else if (e.type === 'text') {
      var secText = propSection('文字', 'role');
      var fText = propFields(secText);
      addText(fText, 'role', function () { return e.role || ''; }, function (v) { e.role = v; }, { wide: true, placeholder: 'status' });
      addHint(secText, 'role=status → 运行时显示「' + STATUS_TEXT + '」；其它 role 由 Screen 代码决定内容。');
      body.appendChild(secText);
    } else if (e.type === 'phase_rows') {
      var secRows = propSection('相行区', 'step × visible');
      var fRows = propFields(secRows);
      addNumber(fRows, 'step（行距）', function () { return num(e.step, 11); }, function (v) { e.step = v; }, { min: 2 });
      addNumber(fRows, 'visible（可见行）', function () { return num(e.visible, 1); }, function (v) { e.visible = v; }, { min: 1, max: 12 });
      addNumber(fRows, 'h（单行高）', function () { return num(e.h, 10); }, function (v) { e.h = v; }, { min: 2 });
      addNumber(fRows, 'w（行宽）', function () { return num(e.w, 0); }, function (v) { e.w = v; }, { min: 1 });
      body.appendChild(secRows);

      var secSb = propSection('滚动条', e.scrollbar ? '独立定位' : '未配置');
      var fSb = propFields(secSb);
      if (e.scrollbar) {
        addNumber(fSb, 'x', function () { return num(e.scrollbar.x, 0); }, function (v) { e.scrollbar.x = v; });
        addNumber(fSb, 'y', function () { return num(e.scrollbar.y, 0); }, function (v) { e.scrollbar.y = v; });
        addNumber(fSb, 'w', function () { return num(e.scrollbar.w, 0); }, function (v) { e.scrollbar.w = v; }, { min: 1 });
        addNumber(fSb, 'h', function () { return num(e.scrollbar.h, 0); }, function (v) { e.scrollbar.h = v; }, { min: 1 });
      }
      addHint(secSb, '行区总高 = step × visible = ' + (num(e.step, 11) * Math.max(1, num(e.visible, 1))) + ' px；h 只是单行高。scrollbar 独立定位，游戏只在 相数 > visible 时绘制。'
        + (num(e.visible, 1) >= PHASE_COUNT ? ' 当前 visible ≥ ' + PHASE_COUNT + '，游戏不画滚动条（选中时这里有虚线参考框）。' : ''));
      addActions(secSb, [
        {
          label: '滚动条贴行区右侧',
          run: function () {
            e.scrollbar = {
              x: num(e.x, 0) + num(e.w, 0) + 1,
              y: num(e.y, 0),
              w: 2,
              h: Math.max(1, (Math.max(1, num(e.visible, 1)) - 1) * num(e.step, 11) + num(e.h, 10))
            };
            pushHistory();
            refreshAll();
            log('已按行区重设 scrollbar', 'line-dim');
          }
        },
        {
          label: '删除滚动条',
          danger: true,
          run: function () {
            delete e.scrollbar;
            pushHistory();
            refreshAll();
          }
        }
      ]);
      body.appendChild(secSb);
    } else if (e.type === 'bar_phase') {
      var secBp = propSection('相/混合条', 'channel');
      var fBp = propFields(secBp);
      addSelect(fBp, 'channel', ['mixed'], function () { return e.channel || 'mixed'; }, function (v) { e.channel = v; });
      addText(fBp, 'hide_when（可选条件）', function () { return e.hide_when || ''; }, function (v) { e.hide_when = v; }, { wide: true, placeholder: 'hidesMixedBuffer' });
      addHint(secBp, '渲染：1px 边框 + 暗色内腔 + 从左按比例填充（phaseBarH）；带 hide_when 时由 Screen 条件隐藏。');
      body.appendChild(secBp);
    } else if (e.type === 'bar_energy') {
      var secBe = propSection('FE 条', 'barH');
      addHint(secBe, '渲染：整框 #373737 → 内轨 #8B8B8B → 红色按比例填充，条内白字居中（barHWithText）。');
      body.appendChild(secBe);
    } else if (e.type === 'slot') {
      var secSlot = propSection('物品槽', '18×18');
      var fSlot = propFields(secSlot);
      addSelect(fSlot, 'role', SLOT_ROLES, function () { return e.role || ''; }, function (v) { e.role = v; }, {
        labels: { filter: 'filter（滤芯槽）', canister: 'canister（罐槽）' }
      });
      addHint(secSlot, '槽体画在 (x-1, y-1) 的 18×18 框内，命中格 16×16；原版容器槽位不在 18 网格上，服务端只要求整数坐标。');
      body.appendChild(secSlot);
    } else if (e.type === 'icon_button') {
      var secBtn = propSection('图标按钮', '20×20');
      var fBtn = propFields(secBtn);
      addSelect(fBtn, 'action', ACTIONS, function () { return e.action || ''; }, function (v) { e.action = v; });
      addHint(secBtn, '图标 16×16 居中于按钮内：' + (R.ICON_FOR_ACTION[e.action] || '—') + '.png。每个 action 只能出现一次。');
      body.appendChild(secBtn);
    } else if (e.type === 'inventory') {
      var secInv = propSection('玩家背包', '9 × 18px');
      addHint(secInv, '形状由 label[2] + rows[3] + hotbar + step(18) 决定，拖动时会整体平移。规则：行距 = 18，快捷栏 = 最后一行 + 22。');
      body.appendChild(secInv);
    } else if (e.type === 'spacer') {
      var secSp = propSection('占位标记', '不绘制内容');
      var fSp = propFields(secSp);
      addText(fSp, 'role', function () { return e.role || ''; }, function (v) { e.role = v; }, { wide: true, placeholder: 'upgrade_band' });
      addHint(secSp, '游戏内 spacer 不画任何内容（升级槽预留带）；编辑器用青色虚线框标出，便于对齐。');
      body.appendChild(secSp);
    }

    /* ── 元素校验 ── */
    var mine = state.issues.filter(function (item) { return item.index === index; });
    var secCheck = propSection('此元素校验', mine.length ? mine.length + ' 条' : '通过');
    if (!mine.length) {
      addHint(secCheck, '✓ 没有本元素相关的错误或警告。');
    } else {
      mine.forEach(function (item) {
        addHint(secCheck, '[' + item.sev.toUpperCase() + '] 规则 ' + item.rule + ' · ' + item.msg, item.sev === 'error');
      });
    }
    addActions(secCheck, [
      { label: '↓ 下移一层', run: function () { moveSelection(1); } },
      { label: '↑ 上移一层', run: function () { moveSelection(-1); } },
      { label: '⧉ 复制', run: duplicateSelection },
      { label: '✕ 删除', danger: true, run: deleteSelection }
    ]);
    body.appendChild(secCheck);
  }

  /* ═══════════════ 渲染：底部校验面板 ═══════════════ */

  function renderValidation() {
    var list = dom.validationList;
    list.innerHTML = '';
    var issues = state.issues || [];
    var errors = 0;
    var warns = 0;
    issues.forEach(function (item) {
      if (item.sev === 'error') { errors++; }
      if (item.sev === 'warn') { warns++; }
    });
    dom.validationSummary.textContent = errors
      ? errors + ' 错误 · ' + warns + ' 警告'
      : (warns ? warns + ' 警告 · 0 错误' : '通过 · 0 错误');

    function addItem(sev, ruleText, message, index) {
      var li = document.createElement('li');
      li.className = 'validation-item sev-' + sev;
      if (index >= 0) {
        li.setAttribute('data-index', String(index));
        li.addEventListener('click', function () {
          var target = elements()[index];
          if (target) { selectElement(target); }
        });
      }
      li.appendChild(el('span', 'v-sev', sev === 'error' ? '错误' : sev === 'warn' ? '警告' : sev === 'info' ? '提示' : '通过'));
      li.appendChild(el('span', 'v-rule', ruleText));
      li.appendChild(el('span', 'v-msg', message));
      list.appendChild(li);
    }

    issues.forEach(function (item) {
      addItem(item.sev, '规则 ' + item.rule, item.msg, item.index);
    });

    if (!issues.length) {
      addItem('pass', '6/6', '边界、18px 网格、相行重叠、action 唯一、slot role 唯一 —— 全部通过。', -1);
    }

    if (state.serverErrors && state.serverErrors.length) {
      state.serverErrors.forEach(function (msg) {
        addItem('error', '服务端', String(msg), -1);
      });
    }

    addItem('info', '规则 6', '加载健壮性：未知 type 忽略并 warn、加载失败回退内置默认 —— 由 Java 加载器负责，编辑器已内置同一套回退。', -1);
    dom.validationSource = dom.validationSource; /* no-op 保持引用 */
  }

  /* ═══════════════ 读数 ═══════════════ */

  function renderReadouts() {
    var layout = state.layout;
    dom.canvasTitle.textContent = layout
      ? layout.id + ' · ' + layoutW() + '×' + layoutH() + ' · schema ' + layout.schema
      : '—';
    var e = state.selection;
    if (!e) {
      dom.selectionReadout.textContent = '未选中元素';
      return;
    }
    var r = e.type === 'slot' ? dataRect(e) : visualRect(e);
    var extra = '';
    if (e.type === 'phase_rows') {
      extra = ' · 行区 ' + Math.round(num(e.step, 11) * Math.max(1, num(e.visible, 1))) + 'px';
    } else if (e.type === 'inventory') {
      extra = ' · label ' + (e.label ? e.label.join(',') : '—');
    }
    dom.selectionReadout.textContent = '#' + (selIndex() + 1) + ' ' + e.type + ' · ' +
      r.x + ',' + r.y + ' · ' + Math.round(r.w) + '×' + Math.round(r.h) + extra;
  }

  /* ═══════════════ 实时刷新（输入期间不重建属性栏，避免丢焦点） ═══════════════ */

  function scheduleLive() {
    if (state.liveRaf) { return; }
    state.liveRaf = requestAnimationFrame(function () {
      state.liveRaf = 0;
      computeIssues();
      renderTree();
      renderValidation();
      renderCanvas();
      renderReadouts();
      updateToolbar();
    });
  }

  function refreshAll() {
    computeIssues();
    updateToolbar();
    renderTree();
    renderProps();
    renderValidation();
    renderCanvas();
    renderReadouts();
  }

  /* ═══════════════ 画布事件：拖动 / 改尺寸 ═══════════════ */

  function canvasPoint(ev) {
    var canvas = dom.canvas;
    var rect = canvas.getBoundingClientRect();
    var sx = rect.width ? canvas.width / rect.width : 1;
    var sy = rect.height ? canvas.height / rect.height : 1;
    var devX = (ev.clientX - rect.left) * sx;
    var devY = (ev.clientY - rect.top) * sy;
    return { x: devX / state.zoom, y: devY / state.zoom, devX: devX, devY: devY };
  }

  function pointInDevice(ev, rect) {
    var pt = canvasPoint(ev);
    return pt.devX >= rect.x && pt.devY >= rect.y && pt.devX < rect.x + rect.w && pt.devY < rect.y + rect.h;
  }

  function handleHitRect(target) {
    if (!target || !RESIZABLE[target.type]) { return null; }
    var r = visualRect(target);
    var z = state.zoom;
    var size = 10;
    return {
      x: Math.round((r.x + r.w) * z) - size,
      y: Math.round((r.y + r.h) * z) - size,
      w: size,
      h: size
    };
  }

  function snapValue(v) {
    var snap = state.snap;
    if (snap > 0) { return Math.round(v / snap) * snap; }
    return Math.round(v);
  }

  function onPointerDown(ev) {
    if (ev.button !== 0) { return; }
    dom.canvas.focus();
    var pt = canvasPoint(ev);

    /* 1) 先判断右下角尺寸手柄 */
    var sel = state.selection;
    if (sel) {
      var hr = handleHitRect(sel);
      if (hr && pointInDevice(ev, hr)) {
        startResize(sel, pt, ev);
        return;
      }
    }

    /* 2) 命中元素 */
    var hit = hitTest(Math.floor(pt.x), Math.floor(pt.y));
    if (!hit) {
      if (state.selection) { state.selection = null; refreshAll(); }
      return;
    }
    if (hit.el !== state.selection) {
      state.selection = hit.el;
      renderProps();
      renderTree();
      renderReadouts();
      renderCanvas();
    }
    if (hit.el.type === 'title' && hit.el.centered) {
      log('标题处于 centered 状态，x 不参与渲染；取消「居中」后才能拖动摆放', 'line-warn');
      return;
    }
    startMove(hit.el, hit.part, pt, ev);
  }

  function startMove(target, part, pt, ev) {
    state.drag = {
      mode: 'move',
      el: target,
      part: part,
      startX: pt.x,
      startY: pt.y,
      origin: captureOrigins(target, part),
      before: JSON.stringify(state.layout),
      moved: false
    };
    dom.canvas.setPointerCapture(ev.pointerId);
    dom.canvas.classList.add('is-dragging');
    ev.preventDefault();
  }

  function startResize(target, pt, ev) {
    state.drag = {
      mode: 'resize',
      el: target,
      part: 'handle',
      startX: pt.x,
      startY: pt.y,
      originRect: visualRect(target),
      before: JSON.stringify(state.layout),
      moved: false
    };
    dom.canvas.setPointerCapture(ev.pointerId);
    dom.canvas.classList.add('is-dragging');
    ev.preventDefault();
  }

  function captureOrigins(target, part) {
    if (target.type === 'inventory') {
      return {
        label: Array.isArray(target.label) ? target.label.slice() : [8, 128],
        rows: Array.isArray(target.rows) ? target.rows.slice() : [140, 158, 176],
        hotbar: num(target.hotbar, 198)
      };
    }
    if (part === 'scrollbar' && target.scrollbar) {
      return { sx: num(target.scrollbar.x, 0), sy: num(target.scrollbar.y, 0) };
    }
    return { x: num(target.x, 0), y: num(target.y, 0), w: num(target.w, 0), h: num(target.h, 0) };
  }

  function onPointerMove(ev) {
    var pt = canvasPoint(ev);
    updateCursorReadout(pt);

    if (state.drag) {
      applyDrag(pt);
      return;
    }

    /* 悬停光标与高亮 */
    var sel = state.selection;
    var hr = handleHitRect(sel);
    if (hr && pointInDevice(ev, hr)) {
      dom.canvas.style.cursor = 'nwse-resize';
      if (state.hoverEl) { state.hoverEl = null; scheduleCanvas(); }
      return;
    }
    var hit = hitTest(Math.floor(pt.x), Math.floor(pt.y));
    var hover = hit ? hit.el : null;
    if (hover !== state.hoverEl) {
      state.hoverEl = hover;
      scheduleCanvas();
    }
    dom.canvas.style.cursor = hit ? 'move' : 'crosshair';
  }

  function updateCursorReadout(pt) {
    var inside = pt.x >= 0 && pt.y >= 0 && pt.x < layoutW() && pt.y < layoutH();
    var x = Math.floor(pt.x);
    var y = Math.floor(pt.y);
    dom.cursorReadout.textContent = inside ? 'x ' + x + ' · y ' + y : 'x — · y —';
  }

  function applyDrag(pt) {
    var drag = state.drag;
    var dx = pt.x - drag.startX;
    var dy = pt.y - drag.startY;
    if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) { drag.moved = true; }
    var target = drag.el;

    if (drag.mode === 'move') {
      if (drag.part === 'scrollbar' && target.scrollbar) {
        var sb = target.scrollbar;
        sb.x = snapValue(drag.origin.sx + dx);
        sb.y = snapValue(drag.origin.sy + dy);
        clampScrollbar(target);
      } else if (target.type === 'inventory') {
        var dY = snapValue(drag.origin.label[1] + dy) - drag.origin.label[1];
        var dX = snapValue(drag.origin.label[0] + dx) - drag.origin.label[0];
        if (!Array.isArray(target.label)) { target.label = [8, 128]; }
        target.label[0] = drag.origin.label[0] + dX;
        target.label[1] = drag.origin.label[1] + dY;
        target.rows = drag.origin.rows.map(function (v) { return v + dY; });
        target.hotbar = drag.origin.hotbar + dY;
        clampInventory(target);
      } else {
        target.x = snapValue(drag.origin.x + dx);
        target.y = snapValue(drag.origin.y + dy);
        clampPosition(target);
      }
    } else {
      var r0 = drag.originRect;
      var nw = snapValue(r0.w + dx);
      var nh = snapValue(r0.h + dy);
      if (target.type === 'icon_button') {
        target.size = Math.max(8, Math.min(64, Math.max(1, Math.max(nw, nh))));
      } else if (target.type === 'phase_rows') {
        target.w = Math.max(8, nw);
        target.h = Math.max(4, nh);
      } else {
        target.w = Math.max(1, nw);
        target.h = Math.max(1, nh);
      }
      clampSize(target);
    }

    scheduleCanvas();
    updateToolbar();
    renderReadouts();
  }

  function clampPosition(target) {
    var r = dataRect(target);
    var lw = layoutW();
    var lh = layoutH();
    var maxX = Math.max(0, lw - r.w);
    var maxY = Math.max(0, lh - r.h);
    if (target.type === 'title' || target.type === 'text') {
      maxX = Math.max(0, lw - 1);
      maxY = Math.max(0, lh - 10);
    }
    target.x = Math.min(Math.max(0, num(target.x, 0)), maxX);
    target.y = Math.min(Math.max(0, num(target.y, 0)), maxY);
  }

  function clampSize(target) {
    var lw = layoutW();
    var lh = layoutH();
    var x = num(target.x, 0);
    var y = num(target.y, 0);
    var w = num(target.w, 1);
    var h = num(target.h, 1);
    if (target.type === 'icon_button') { return; }
    target.w = Math.max(1, Math.min(w, Math.max(1, lw - x)));
    target.h = Math.max(1, Math.min(h, Math.max(1, lh - y)));
  }

  function clampScrollbar(target) {
    var sb = target.scrollbar;
    if (!sb) { return; }
    sb.w = Math.max(1, num(sb.w, 1));
    sb.h = Math.max(1, num(sb.h, 1));
    sb.x = Math.min(Math.max(0, num(sb.x, 0)), Math.max(0, layoutW() - sb.w));
    sb.y = Math.min(Math.max(0, num(sb.y, 0)), Math.max(0, layoutH() - sb.h));
  }

  function clampInventory(target) {
    var step = Math.max(1, num(target.step, 18));
    var lw = layoutW();
    var lh = layoutH();
    var label = Array.isArray(target.label) ? target.label : (target.label = [8, 128]);
    var rows = Array.isArray(target.rows) ? target.rows : (target.rows = [140, 158, 176]);
    var maxLabelX = Math.max(1, lw - 9 * step - 1);
    label[0] = Math.min(Math.max(1, num(label[0], 8)), maxLabelX);
    var maxRow = 0;
    rows.forEach(function (v) { maxRow = Math.max(maxRow, num(v, 0)); });
    var hotbar = num(target.hotbar, maxRow + 22);
    var bottom = Math.max(hotbar, maxRow) + 18;
    var shift = 0;
    if (bottom > lh) { shift = lh - bottom; }
    var minTop = Math.min.apply(null, rows.map(function (v) { return num(v, 0); }));
    if (minTop + shift < 2) { shift = 2 - minTop; }
    if (shift !== 0) {
      label[1] = num(label[1], 128) + shift;
      target.rows = rows.map(function (v) { return num(v, 0) + shift; });
      target.hotbar = hotbar + shift;
    }
    target.label = label;
    target.step = step;
  }

  function finishDrag(ev) {
    var drag = state.drag;
    if (!drag) { return; }
    state.drag = null;
    dom.canvas.classList.remove('is-dragging');
    try { dom.canvas.releasePointerCapture(ev.pointerId); } catch (e) { /* ignore */ }
    var changed = JSON.stringify(state.layout) !== drag.before;
    if (changed) {
      pushHistory();
      refreshAll();
      log((drag.mode === 'resize' ? '调整尺寸' : '移动') + ' #' + (selIndex() + 1) + ' ' + drag.el.type, 'line-dim');
    } else {
      refreshAll();
    }
  }

  function onPointerUp(ev) { finishDrag(ev); }
  function onPointerCancel(ev) { finishDrag(ev); }
  function onPointerLeave() {
    if (state.drag) { return; }
    if (state.hoverEl) { state.hoverEl = null; scheduleCanvas(); }
    dom.canvas.style.cursor = 'crosshair';
    dom.cursorReadout.textContent = 'x — · y —';
  }

  /* ═══════════════ 元素操作 ═══════════════ */

  function selectByIndex(index) {
    var target = elements()[index];
    if (target) { selectElement(target); }
  }

  function addElement() {
    var type = dom.addType.value;
    var item = defaultElement(type);
    if (!item) { return; }
    elements().push(item);
    state.selection = item;
    pushHistory();
    refreshAll();
    log('新增 ' + type + '（#' + (selIndex() + 1) + '）', 'line-ok');
    dom.canvas.focus();
  }

  function defaultElement(type) {
    var lw = layoutW();
    var lh = layoutH();
    switch (type) {
      case 'title': return { type: 'title', x: 8, y: 6, text: 'dynamic', centered: true };
      case 'text': return { type: 'text', x: 8, y: Math.max(0, lh - 36), role: 'status' };
      case 'phase_rows': return { type: 'phase_rows', x: 8, y: 24, w: 96, h: 10, step: 11, visible: 5, scrollbar: { x: 106, y: 24, w: 2, h: 54 } };
      case 'bar_phase': return { type: 'bar_phase', x: 8, y: Math.max(0, lh - 56), w: 96, h: 6, channel: 'mixed' };
      case 'bar_energy': return { type: 'bar_energy', x: 8, y: Math.max(0, lh - 44), w: 96, h: 8 };
      case 'slot': return { type: 'slot', x: 16, y: 16, role: 'filter' };
      case 'icon_button': return { type: 'icon_button', x: Math.max(0, lw - 32), y: 16, size: 20, action: 'toggle' };
      case 'inventory': return { type: 'inventory', label: [8, Math.max(0, lh - 96)], rows: [Math.max(18, lh - 84), Math.max(36, lh - 66), Math.max(54, lh - 48)], hotbar: Math.max(72, lh - 26), step: 18 };
      case 'spacer': return { type: 'spacer', x: 8, y: 8, w: 24, h: 24, role: 'spacer' };
      default: return null;
    }
  }

  function moveSelection(delta) {
    var index = selIndex();
    if (index < 0) { return; }
    var els = elements();
    var to = index + delta;
    if (to < 0 || to >= els.length) { return; }
    var item = els[index];
    els.splice(index, 1);
    els.splice(to, 0, item);
    state.selection = item;
    pushHistory();
    refreshAll();
    log('移动 #' + (index + 1) + ' → #' + (to + 1), 'line-dim');
  }

  function duplicateSelection() {
    var index = selIndex();
    if (index < 0) { return; }
    var copy = clone(state.layout.elements[index]);
    if (copy.type === 'inventory') {
      copy.label = [num(copy.label && copy.label[0], 8) + 4, num(copy.label && copy.label[1], 128) + 4];
      copy.rows = (copy.rows || []).map(function (v) { return num(v, 0) + 4; });
      copy.hotbar = num(copy.hotbar, 198) + 4;
    } else {
      copy.x = num(copy.x, 0) + 4;
      copy.y = num(copy.y, 0) + 4;
    }
    state.layout.elements.splice(index + 1, 0, copy);
    state.selection = copy;
    pushHistory();
    refreshAll();
    log('复制 #' + (index + 1) + ' → #' + (index + 2), 'line-dim');
  }

  function deleteSelection() {
    var index = selIndex();
    if (index < 0) { return; }
    var removed = state.layout.elements[index];
    state.layout.elements.splice(index, 1);
    var next = Math.min(index, state.layout.elements.length - 1);
    state.selection = next >= 0 ? state.layout.elements[next] : null;
    pushHistory();
    refreshAll();
    log('删除 #' + (index + 1) + ' ' + removed.type, 'line-warn');
  }

  function nudge(dx, dy) {
    var e = state.selection;
    if (!e) { return; }
    if (e.type === 'title' && e.centered && dx !== 0) {
      log('centered 标题的 x 不参与渲染，方向键只调整 y；取消居中后可水平移动', 'line-dim');
      dx = 0;
    }
    if (e.type === 'inventory') {
      e.label = Array.isArray(e.label) ? e.label : [8, 128];
      e.label[0] = num(e.label[0], 8) + dx;
      e.label[1] = num(e.label[1], 128) + dy;
      e.rows = (Array.isArray(e.rows) ? e.rows : [140, 158, 176]).map(function (v) { return num(v, 0) + dy; });
      e.hotbar = num(e.hotbar, 198) + dy;
      clampInventory(e);
    } else {
      e.x = num(e.x, 0) + dx;
      e.y = num(e.y, 0) + dy;
      clampPosition(e);
    }
    pushHistory();
    scheduleLive();
  }

  /* ═══════════════ 保存 / 构建 ═══════════════ */

  async function saveLayout(quiet) {
    if (!state.layout) { return false; }
    state.layout.id = state.screen;
    dom.saveBtn.disabled = true;
    if (!quiet) { log('POST /api/layout/' + state.screen + ' …', 'line-accent'); }
    try {
      var r = await fetchJSON('/api/layout/' + encodeURIComponent(state.screen), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(state.layout)
      });
      var data = r.data || {};
      if (r.res.ok && data.ok) {
        state.savedJson = JSON.stringify(state.layout);
        state.fromFallback = false;
        state.serverErrors = [];
        state.serverOnline = true;
        setServerPill('online');
        updateToolbar();
        renderValidation();
        log('已保存 ' + state.screen + '.json（' + elements().length + ' 元素）', 'line-ok');
        return true;
      }
      var errs = (data && data.errors) || [];
      if (!errs.length) { errs = ['HTTP ' + r.res.status]; }
      state.serverErrors = errs;
      state.serverOnline = false;
      setServerPill('offline', '保存被拒绝');
      renderValidation();
      log('保存被拒绝：' + errs.join('；'), 'line-error');
      return false;
    } catch (err) {
      state.serverErrors = ['无法连接服务端：' + err.message];
      state.serverOnline = false;
      setServerPill('offline', err.message);
      renderValidation();
      log('保存失败：' + err.message, 'line-error');
      return false;
    } finally {
      dom.saveBtn.disabled = false;
    }
  }

  function classifyBuildLine(line) {
    if (/(error|failed|exception|caused by)/i.test(line)) { return 'line-error'; }
    if (/(warning|deprecated|deprecation)/i.test(line)) { return 'line-warn'; }
    if (/(BUILD SUCCESSFUL|ALL OK)/i.test(line)) { return 'line-ok'; }
    if (/^(> Task|> Configure project)/.test(line)) { return 'line-accent'; }
    if (/^(BUILD|FAILURE)/.test(line)) { return 'line-error'; }
    return '';
  }

  async function startBuild() {
    if (state.build.busy) { return; }
    if (state.layout && JSON.stringify(state.layout) !== state.savedJson) {
      log('检测到未保存修改，先保存再构建 …', 'line-dim');
      var ok = await saveLayout(true);
      if (!ok) {
        log('构建已取消：保存失败', 'line-error');
        return;
      }
    }
    state.build.busy = true;
    state.build.since = 0;
    dom.buildBtn.disabled = true;
    setBuildResult('构建中…', false);
    log('POST /api/build …', 'line-accent');
    try {
      var r = await fetchJSON('/api/build', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
      var data = r.data || {};
      if (!r.res.ok || !data.ok) {
        throw new Error((data && data.error) || ('HTTP ' + r.res.status));
      }
      state.build.job = data.job;
      log('构建任务已启动：' + data.job + '（轮询日志中）', 'line-accent');
      pollBuildOnce();
    } catch (err) {
      state.build.busy = false;
      dom.buildBtn.disabled = false;
      setBuildResult('✗ 无法启动构建', true);
      log('启动构建失败：' + err.message, 'line-error');
    }
  }

  async function pollBuildOnce() {
    if (!state.build.busy || !state.build.job) { return; }
    if (state.build.timer) { clearTimeout(state.build.timer); state.build.timer = null; }
    var job = state.build.job;
    try {
      var r = await fetchJSON('/api/build/log?since=' + state.build.since + '&job=' + encodeURIComponent(job));
      var data = r.data || {};
      if (!r.res.ok || !data.ok) {
        throw new Error((data && data.error) || ('HTTP ' + r.res.status));
      }
      (data.lines || []).forEach(function (line) { log(line, classifyBuildLine(line)); });
      if (typeof data.next === 'number') { state.build.since = data.next; }
      else { state.build.since += (data.lines || []).length; }
      if (data.done) {
        state.build.busy = false;
        state.build.job = null;
        dom.buildBtn.disabled = false;
        if (data.result) {
          var res = data.result;
          setBuildResult('✓ ' + res.jar + ' · ' + fmtBytes(res.jarBytes) + ' · test ' + res.testJar, false);
          log('BUILD OK · ' + res.jar + '（' + res.jarBytes + ' B, ' + res.jarTime + '）· entries=' + res.entries + ' · testJar=' + res.testJar + '（' + res.testJarBytes + ' B）', 'line-ok');
        } else {
          setBuildResult('✗ 构建失败（见日志）', true);
          log('构建失败（result=null）', 'line-error');
        }
        return;
      }
      state.build.timer = setTimeout(pollBuildOnce, 500);
    } catch (err) {
      state.build.busy = false;
      state.build.job = null;
      dom.buildBtn.disabled = false;
      setBuildResult('✗ 轮询构建日志失败', true);
      log('轮询构建日志失败：' + err.message, 'line-error');
    }
  }

  /* ═══════════════ 顶栏 / 屏幕切换 ═══════════════ */

  /* 服务端对照图是 4× 预览 PNG；显示时缩放到与画布当前缩放一致，方便并排比对。 */
  function syncCompareScale() {
    if (dom.compareFrame.hidden) { return; }
    dom.compareImg.style.width = (layoutW() * state.zoom) + 'px';
    dom.compareImg.style.height = (layoutH() * state.zoom) + 'px';
  }

  function setZoom(zoom) {
    state.zoom = zoom;
    Array.prototype.forEach.call(dom.zoomGroup.querySelectorAll('.seg'), function (btn) {
      btn.classList.toggle('is-active', Number(btn.getAttribute('data-zoom')) === zoom);
    });
    renderCanvas();
    renderReadouts();
    syncCompareScale();
  }

  async function onScreenChange() {
    var next = dom.screenSelect.value;
    if (next === state.screen) { return; }
    if (state.layout && JSON.stringify(state.layout) !== state.savedJson) {
      var ok = window.confirm('当前屏幕有未保存修改，切换会丢弃。继续？');
      if (!ok) { dom.screenSelect.value = state.screen; return; }
    }
    await loadLayout(next);
  }

  async function onReload() {
    if (state.layout && JSON.stringify(state.layout) !== state.savedJson) {
      var ok = window.confirm('重新加载会丢弃未保存修改。继续？');
      if (!ok) { return; }
    }
    await loadLayout(state.screen);
  }

  /* ═══════════════ 键盘 ═══════════════ */

  function onKeyDown(ev) {
    var tag = (ev.target && ev.target.tagName ? ev.target.tagName : '').toLowerCase();
    var inField = tag === 'input' || tag === 'select' || tag === 'textarea';
    var mod = ev.ctrlKey || ev.metaKey;

    if (mod && (ev.key === 's' || ev.key === 'S')) {
      ev.preventDefault();
      saveLayout(false);
      return;
    }
    if (inField) {
      if (ev.key === 'Escape') { ev.target.blur(); }
      return;
    }
    if (mod && (ev.key === 'z' || ev.key === 'Z')) {
      ev.preventDefault();
      if (ev.shiftKey) { redo(); } else { undo(); }
      return;
    }
    if (mod && (ev.key === 'y' || ev.key === 'Y')) {
      ev.preventDefault();
      redo();
      return;
    }
    if (mod && (ev.key === 'd' || ev.key === 'D')) {
      ev.preventDefault();
      duplicateSelection();
      return;
    }
    if (ev.key === 'Delete' || ev.key === 'Backspace') {
      if (state.selection) {
        ev.preventDefault();
        deleteSelection();
      }
      return;
    }
    if (ev.key === 'Escape') {
      state.selection = null;
      refreshAll();
      return;
    }
    var step = ev.shiftKey ? 10 : 1;
    var handled = true;
    if (ev.key === 'ArrowLeft') { nudge(-step, 0); }
    else if (ev.key === 'ArrowRight') { nudge(step, 0); }
    else if (ev.key === 'ArrowUp') { nudge(0, -step); }
    else if (ev.key === 'ArrowDown') { nudge(0, step); }
    else { handled = false; }
    if (handled) { ev.preventDefault(); }
  }

  /* ═══════════════ 事件绑定 / 初始化 ═══════════════ */

  function bindEvents() {
    dom.screenSelect.addEventListener('change', onScreenChange);
    Array.prototype.forEach.call(dom.zoomGroup.querySelectorAll('.seg'), function (btn) {
      btn.addEventListener('click', function () { setZoom(Number(btn.getAttribute('data-zoom')) || 1); });
    });
    dom.snapSelect.addEventListener('change', function () {
      state.snap = Number(dom.snapSelect.value) || 0;
      renderCanvas();
      log('吸附 = ' + (state.snap ? state.snap + ' px' : '关闭'), 'line-dim');
    });
    dom.gridToggle.addEventListener('change', function () {
      state.showGrid = dom.gridToggle.checked;
      renderCanvas();
    });
    dom.reloadBtn.addEventListener('click', onReload);
    dom.undoBtn.addEventListener('click', undo);
    dom.redoBtn.addEventListener('click', redo);
    dom.saveBtn.addEventListener('click', function () { saveLayout(false); });
    dom.buildBtn.addEventListener('click', startBuild);
    dom.addBtn.addEventListener('click', addElement);
    dom.treeOps.addEventListener('click', function (ev) {
      var btn = ev.target && ev.target.closest ? ev.target.closest('button[data-op]') : null;
      if (!btn) { return; }
      var op = btn.getAttribute('data-op');
      if (op === 'up') { moveSelection(-1); }
      else if (op === 'down') { moveSelection(1); }
      else if (op === 'duplicate') { duplicateSelection(); }
      else if (op === 'delete') { deleteSelection(); }
    });
    dom.retryBtn.addEventListener('click', function () { loadLayout(state.screen); });
    dom.clearLog.addEventListener('click', function () { dom.log.innerHTML = ''; setBuildResult('', false); });
    dom.compareToggle.addEventListener('change', function () {
      if (!state.compare.available) {
        dom.compareToggle.checked = false;
        return;
      }
      var on = dom.compareToggle.checked;
      dom.compareFrame.hidden = !on;
      if (on) {
        dom.compareImg.src = '/assets/preview/' + encodeURIComponent(state.screen) + '.png';
        syncCompareScale();
      }
    });

    var canvas = dom.canvas;
    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerup', onPointerUp);
    canvas.addEventListener('pointercancel', onPointerCancel);
    canvas.addEventListener('pointerleave', onPointerLeave);
    canvas.addEventListener('contextmenu', function (ev) { ev.preventDefault(); });
    document.addEventListener('keydown', onKeyDown);
  }

  function syncToolbar() {
    dom.zoomGroup.querySelectorAll('.seg').forEach(function (btn) {
      btn.classList.toggle('is-active', Number(btn.getAttribute('data-zoom')) === state.zoom);
    });
    dom.snapSelect.value = String(state.snap);
    dom.gridToggle.checked = state.showGrid;
  }

  async function init() {
    cacheDom();
    bindEvents();
    syncToolbar();
    loadIcons();
    await initServer();
    await loadLayout(state.screen);
    log('编辑器就绪 · 拖动移动 · 右下角手柄改尺寸 · Ctrl+S 保存 · Ctrl+Z/Y 撤销重做', 'line-ok');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  /* 供调试 / 自动化测试使用（不参与正常 UI） */
  window.MeteorosEditor = {
    state: state,
    loadLayout: loadLayout,
    saveLayout: saveLayout,
    startBuild: startBuild,
    refreshAll: refreshAll,
    computeIssues: computeIssues,
    setZoom: setZoom,
    selectByIndex: selectByIndex,
    addElement: addElement,
    undo: undo,
    redo: redo,
    log: log
  };
})();
