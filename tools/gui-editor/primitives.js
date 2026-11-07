/* =============================================================================
 * Meteoros Protocol · GUI 渲染原语（canvas 2D）
 *
 * 与游戏 1:1 对齐的画法来源：
 *   src/main/java/com/spectrum255/meteorosprotocol/client/gui/MeteorosGui.java
 *     panel() / slot() / barH() / phaseBarH() / barHWithText() / centeredText()
 *   .../PhaseRowWidget.java   （相行：1px 边框 + 暗色内腔 + 从左原色填充）
 *   .../IconButton.java       （按钮底 + 16x16 图标居中）
 *   tools/preview_gui_mock.py （4x 参考图生成器，坐标/颜色基准）
 *
 * 约定：
 *   * 所有坐标是“布局坐标”（逻辑像素），调用方负责 ctx.scale(zoom)。
 *   * 所有绘制只用整数矩形（fillRect），保证任意整数缩放下都锐利。
 *   * 文字使用系统字体近似 Minecraft 字体（无外部字体依赖），在 README 里注明。
 * ========================================================================== */
(function (root) {
  'use strict';

  /* ── 调色板（= MeteorosGui 常量） ─────────────────────────────────────── */
  var COLORS = {
    BLACK: '#000000',
    PANEL_BODY: '#c6c6c6',       // 0xFFC6C6C6
    PANEL_HIGHLIGHT: '#ffffff',  // 0xFFFFFFFF
    PANEL_SHADOW: '#555555',     // 0xFF555555
    SLOT_DARK: '#373737',        // 0xFF373737
    SLOT_LIGHT: '#ffffff',       // 0xFFFFFFFF
    SLOT_BODY: '#8b8b8b',        // 0xFF8B8B8B
    SLOT_HOVER: 'rgba(255,255,255,0.32)',
    LABEL_TEXT: '#404040',       // 0xFF404040
    TEXT_DIM: '#808080',         // 0xFF808080
    ROW_HIGHLIGHT: 'rgba(255,255,255,0.125)', // 0x20FFFFFF
    MIXED_FILL: '#8b8b8b',
    MIXED_EMPTY: '#555555',
    ENERGY_FILL: '#ff0000'
  };

  /* ── 注册表知识：当前 7 个相（与 Phase.java 一致） ────────────────────── */
  var PHASES = [
    { id: 'aetheron', short: 'Ae', color: '#ffffff' },
    { id: 'thermion', short: 'T', color: '#ff4a3f' },
    { id: 'cryon', short: 'C', color: '#5fe8ff' },
    { id: 'voltix_pos', short: 'V+', color: '#ffe14a' },
    { id: 'voltix_neg', short: 'V-', color: '#9a5cff' },
    { id: 'aquantum', short: 'Aq', color: '#3a54e0' },
    { id: 'impure_aetheron', short: 'Ae*', color: '#b9bdc0' }
  ];

  /* 相色的暗色版（= Phase.darkRgb：每通道 x0.35，下限 8） */
  function darkColor(hex) {
    var n = parseInt(String(hex).replace('#', ''), 16);
    var r = (n >> 16) & 255;
    var g = (n >> 8) & 255;
    var b = n & 255;
    return 'rgb(' + Math.max(8, Math.floor(r * 0.35)) + ',' +
      Math.max(8, Math.floor(g * 0.35)) + ',' +
      Math.max(8, Math.floor(b * 0.35)) + ')';
  }

  /* 演示用填充率 / 能量比例（= 参考图 preview_gui_mock.py 的常量） */
  var FAKE_AMOUNTS = [0.62, 0.38, 0.30, 0.22, 0.16, 0.11, 0.05];
  var FE_RATIO = 0.64;
  var FE_TEXT = '640 kFE / 1 MFE';
  var PHASE_BAR_RATIO = 0.34;
  var SELECTED_ROW = 0;

  /* action → 图标文件（art/gui/icons/，由服务端 /assets/icons/ 提供） */
  var ICON_FOR_ACTION = {
    toggle: 'icon_power',
    redstone: 'icon_redstone_high',
    extract: 'icon_extract',
    auto_io: 'icon_autoio',
    side_config: 'icon_side'
  };
  var ICON_NAMES = [
    'icon_power', 'icon_power_off',
    'icon_redstone', 'icon_redstone_high', 'icon_redstone_low', 'icon_redstone_always',
    'icon_extract', 'icon_side', 'icon_autoio', 'icon_autoio_off'
  ];

  /* ── 字体 ─────────────────────────────────────────────────────────────── */
  /* 参考图（preview_gui_mock.py）用微软雅黑渲染，这里保持一致；缺失时逐级回退。 */
  var FONT_STACK = '"Microsoft YaHei UI","Microsoft YaHei","PingFang SC","Segoe UI",sans-serif';

  function fontOf(px) { return px + 'px ' + FONT_STACK; }

  function measure(ctx, text, px) {
    px = px || 8;
    ctx.save();
    ctx.font = fontOf(px);
    ctx.textBaseline = 'alphabetic';
    var m = ctx.measureText(text == null ? '' : String(text));
    var left = m.actualBoundingBoxLeft || 0;
    var right = m.actualBoundingBoxRight || 0;
    var ascent = m.actualBoundingBoxAscent || 0;
    var descent = m.actualBoundingBoxDescent || 0;
    ctx.restore();
    return {
      width: m.width || 0,
      inkW: left + right || m.width || 0,
      inkH: ascent + descent || px,
      ascent: ascent || px * 0.78,
      descent: descent || px * 0.22,
      left: left
    };
  }

  /** 文字墨迹宽度（用于居中/边界）。 */
  function textWidth(ctx, text, px) {
    return measure(ctx, text, px).inkW;
  }

  /**
   * 以“墨迹左上角”为 (x, y) 画一行文字。
   * opts: { px, shadow, alpha }
   */
  function drawText(ctx, text, x, y, color, opts) {
    if (text == null || text === '') { return; }
    opts = opts || {};
    var px = opts.px || 8;
    var m = measure(ctx, text, px);
    var drawX = Math.round(x + m.left);
    var drawY = Math.round(y + m.ascent);
    ctx.save();
    ctx.font = fontOf(px);
    ctx.textBaseline = 'alphabetic';
    if (opts.alpha != null) { ctx.globalAlpha *= opts.alpha; }
    if (opts.shadow) {
      ctx.fillStyle = 'rgba(0,0,0,0.55)';
      ctx.fillText(String(text), drawX + 1, drawY + 1);
    }
    ctx.fillStyle = color;
    ctx.fillText(String(text), drawX, drawY);
    ctx.restore();
  }

  /** 在 (x,y,w,h) 矩形内水平+垂直居中画文字，超宽时整体缩小（= barHWithText）。 */
  function drawCenteredText(ctx, text, x, y, w, h, color, opts) {
    if (text == null || text === '') { return; }
    opts = opts || {};
    var px = opts.px || 7;
    var m = measure(ctx, text, px);
    if (m.inkW <= 0) { return; }
    var scale = Math.min(1, (w - 4) / m.inkW);
    var sx = x + w / 2 - (m.inkW * scale) / 2;
    var sy = y + h / 2 - (m.inkH * scale) / 2;
    ctx.save();
    ctx.translate(sx, sy);
    ctx.scale(scale, scale);
    drawText(ctx, text, 0, 0, color, { px: px, shadow: opts.shadow });
    ctx.restore();
  }

  /* ── 基础矩形 ─────────────────────────────────────────────────────────── */
  function rect(ctx, x, y, w, h, color) {
    if (w <= 0 || h <= 0) { return; }
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  }

  /* ── 原语：面板（黑框 + #C6C6C6 + 上/左白高光 + 下/右深阴影） ─────────── */
  function panel(ctx, x, y, w, h) {
    if (w <= 0 || h <= 0) { return; }
    rect(ctx, x, y, w, h, COLORS.BLACK);
    if (w <= 2 || h <= 2) { return; }
    rect(ctx, x + 1, y + 1, w - 2, h - 2, COLORS.PANEL_BODY);
    rect(ctx, x + 1, y + 1, w - 2, 1, COLORS.PANEL_HIGHLIGHT);
    rect(ctx, x + 1, y + 1, 1, h - 2, COLORS.PANEL_HIGHLIGHT);
    rect(ctx, x + 1, y + h - 2, w - 2, 1, COLORS.PANEL_SHADOW);
    rect(ctx, x + w - 2, y + 1, 1, h - 2, COLORS.PANEL_SHADOW);
  }

  /* ── 原语：物品槽（18x18 凹陷框画在 x-1, y-1，命中格 16x16） ──────────── */
  function slot(ctx, x, y, hovered) {
    var sx = x - 1;
    var sy = y - 1;
    rect(ctx, sx, sy, 18, 18, COLORS.SLOT_BODY);
    rect(ctx, sx, sy, 18, 1, COLORS.SLOT_DARK);
    rect(ctx, sx, sy, 1, 18, COLORS.SLOT_DARK);
    rect(ctx, sx, sy + 17, 18, 1, COLORS.SLOT_LIGHT);
    rect(ctx, sx + 17, sy, 1, 18, COLORS.SLOT_LIGHT);
    if (hovered) { rect(ctx, x, y, 16, 16, COLORS.SLOT_HOVER); }
  }

  /* ── 原语：相行（暗色内腔 + 从左原色填充 + 1px 暗边/白下右 + 选中白框） ── */
  function phaseRow(ctx, x, y, w, h, color, ratio, selected) {
    if (w <= 0 || h <= 0) { return; }
    var innerW = Math.max(0, w - 2);
    var innerH = Math.max(0, h - 2);
    rect(ctx, x, y, w, h, darkColor(color));
    var fill = Math.round(Math.max(0, Math.min(1, ratio)) * innerW);
    if (fill > 0 && innerH > 0) {
      rect(ctx, x + 1, y + 1, fill, innerH, color);
    }
    rect(ctx, x, y, w, 1, COLORS.SLOT_DARK);
    rect(ctx, x, y, 1, h, COLORS.SLOT_DARK);
    rect(ctx, x, y + h - 1, w, 1, COLORS.SLOT_LIGHT);
    rect(ctx, x + w - 1, y, 1, h, COLORS.SLOT_LIGHT);
    if (selected) {
      rect(ctx, x, y, w, 1, COLORS.SLOT_LIGHT);
      rect(ctx, x, y, 1, h, COLORS.SLOT_LIGHT);
      rect(ctx, x, y + h - 1, w, 1, COLORS.SLOT_LIGHT);
      rect(ctx, x + w - 1, y, 1, h, COLORS.SLOT_LIGHT);
    }
  }

  /* ── 原语：实心描边条（barH：整框 #373737 + 内轨 #8B8B8B + 填充） ─────── */
  function barH(ctx, x, y, w, h, ratio, fillColor) {
    if (w <= 0 || h <= 0) { return; }
    rect(ctx, x, y, w, h, COLORS.SLOT_DARK);
    if (w <= 2 || h <= 2) { return; }
    rect(ctx, x + 1, y + 1, w - 2, h - 2, COLORS.SLOT_BODY);
    var fill = Math.round(Math.max(0, Math.min(1, ratio)) * (w - 2));
    if (fill > 0) { rect(ctx, x + 1, y + 1, fill, h - 2, fillColor); }
  }

  /* ── 原语：空心条（phaseBarH：整块空色 + 1px 上/左暗、下/右白 + 填充） ── */
  function phaseBarH(ctx, x, y, w, h, ratio, fillColor, emptyColor) {
    if (w <= 0 || h <= 0) { return; }
    rect(ctx, x, y, w, h, emptyColor || COLORS.MIXED_EMPTY);
    if (w <= 2 || h <= 2) { return; }
    rect(ctx, x, y, w, 1, COLORS.SLOT_DARK);
    rect(ctx, x, y, 1, h, COLORS.SLOT_DARK);
    rect(ctx, x, y + h - 1, w, 1, COLORS.SLOT_LIGHT);
    rect(ctx, x + w - 1, y, 1, h, COLORS.SLOT_LIGHT);
    var fill = Math.round(Math.max(0, Math.min(1, ratio)) * (w - 2));
    if (fill > 0) { rect(ctx, x + 1, y + 1, fill, h - 2, fillColor); }
  }

  /* ── 原语：FE 条（phaseBarH + 条内居中白字 + 阴影） ───────────────────── */
  function barHWithText(ctx, x, y, w, h, ratio, fillColor, emptyColor, text) {
    phaseBarH(ctx, x, y, w, h, ratio, fillColor, emptyColor);
    drawCenteredText(ctx, text, x, y, w, h, '#ffffff', { px: 7, shadow: true });
  }

  /* ── 原语：滚动条（暗底 #373737 + #8B8B8B thumb；= IntakeScreen 画法） ── */
  function scrollbar(ctx, x, y, w, h, total, visible, offset) {
    total = Math.max(1, total | 0);
    visible = Math.max(1, Math.min(visible | 0, total));
    offset = Math.max(0, Math.min(offset | 0, total - visible));
    rect(ctx, x, y, w, h, COLORS.SLOT_DARK);
    var thumb = Math.max(6, Math.round((h * visible) / total));
    thumb = Math.min(thumb, h);
    var span = total - visible;
    var ty = span > 0 ? y + Math.round(((h - thumb) * offset) / span) : y;
    rect(ctx, x, ty, w, thumb, COLORS.SLOT_BODY);
  }

  /* ── 原语：图标按钮（20x20 原版底 + 16x16 图标居中） ───────────────────── */
  function iconButton(ctx, x, y, size, image, enabled) {
    panel(ctx, x, y, size, size);
    var inner = 16;
    var ix = x + Math.floor((size - inner) / 2);
    var iy = y + Math.floor((size - inner) / 2);
    if (image && image.complete && image.naturalWidth > 0) {
      ctx.save();
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(image, ix, iy, inner, inner);
      if (enabled === false) {
        ctx.fillStyle = 'rgba(128,128,128,0.5)';
        ctx.fillRect(ix, iy, inner, inner);
      }
      ctx.restore();
    } else {
      /* 图标未加载：画一个暗色占位，避免留白看起来像 bug */
      rect(ctx, ix, iy, inner, inner, '#6f6f6f');
      rect(ctx, ix, iy, inner, 1, '#5a5a5a');
      rect(ctx, ix, iy, 1, inner, '#5a5a5a');
      rect(ctx, ix, iy + inner - 1, inner, 1, '#8a8a8a');
      rect(ctx, ix + inner - 1, iy, 1, inner, '#8a8a8a');
    }
  }

  /* ── 原语：标题（centered 时按整宽居中） ──────────────────────────────── */
  function title(ctx, x, y, text, centered, layoutWidth, color) {
    var drawX = x;
    if (centered) {
      var w = textWidth(ctx, text, 8);
      drawX = Math.round((layoutWidth - w) / 2);
    }
    drawText(ctx, text, drawX, y, color || COLORS.LABEL_TEXT, { px: 8 });
  }

  /* ── 原语：单行标签文字 ───────────────────────────────────────────────── */
  function label(ctx, x, y, text, color, px) {
    drawText(ctx, text, x, y, color || COLORS.LABEL_TEXT, { px: px || 8 });
  }

  /* ── 原语：玩家背包（标签 + 3 行 + 快捷栏，每格 18px） ─────────────────── */
  function inventory(ctx, labelX, labelY, rows, hotbar, step, labelText) {
    step = step || 18;
    label(ctx, labelX, labelY, labelText || '玩家背包', COLORS.LABEL_TEXT, 8);
    for (var r = 0; r < rows.length; r++) {
      for (var col = 0; col < 9; col++) {
        slot(ctx, labelX + col * step, rows[r]);
      }
    }
    if (hotbar != null) {
      for (var c2 = 0; c2 < 9; c2++) {
        slot(ctx, labelX + c2 * step, hotbar);
      }
    }
  }

  /* ── 原语：占位标记（编辑器专用虚线框 + 标签；游戏内 spacer 不绘制内容） ── */
  function spacer(ctx, x, y, w, h, role) {
    if (w <= 0 || h <= 0) { return; }
    var color = 'rgba(115,251,253,0.42)';
    var step = 3;
    var i;
    for (i = 0; i < w; i += step * 2) {
      rect(ctx, x + i, y, Math.min(step, w - i), 1, color);
      rect(ctx, x + i, y + h - 1, Math.min(step, w - i), 1, color);
    }
    for (i = 0; i < h; i += step * 2) {
      rect(ctx, x, y + i, 1, Math.min(step, h - i), color);
      rect(ctx, x + w - 1, y + i, 1, Math.min(step, h - i), color);
    }
    if (role) {
      drawText(ctx, role, x + 2, y + 2, 'rgba(115,251,253,0.75)', { px: 6 });
    }
  }

  /* ── 原语：未知类型（黄框 + 类型名，提示“加载器会 warn 并跳过”） ──────── */
  function unknown(ctx, x, y, w, h, type) {
    w = w > 0 ? w : 40;
    h = h > 0 ? h : 12;
    var step = 4;
    var color = 'rgba(232,178,60,0.75)';
    for (var i = 0; i < w; i += step * 2) {
      rect(ctx, x + i, y, Math.min(step, w - i), 1, color);
      rect(ctx, x + i, y + h - 1, Math.min(step, w - i), 1, color);
    }
    for (var j = 0; j < h; j += step * 2) {
      rect(ctx, x, y + j, 1, Math.min(step, h - j), color);
      rect(ctx, x + w - 1, y + j, 1, Math.min(step, h - j), color);
    }
    drawText(ctx, String(type || '?'), x + 2, y + 1, '#e8b23c', { px: 7 });
  }

  /* ── 导出 ─────────────────────────────────────────────────────────────── */
  root.GuiRender = {
    COLORS: COLORS,
    PHASES: PHASES,
    FAKE_AMOUNTS: FAKE_AMOUNTS,
    FE_RATIO: FE_RATIO,
    FE_TEXT: FE_TEXT,
    PHASE_BAR_RATIO: PHASE_BAR_RATIO,
    SELECTED_ROW: SELECTED_ROW,
    ICON_FOR_ACTION: ICON_FOR_ACTION,
    ICON_NAMES: ICON_NAMES,
    FONT_STACK: FONT_STACK,
    fontOf: fontOf,
    measure: measure,
    textWidth: textWidth,
    drawText: drawText,
    drawCenteredText: drawCenteredText,
    rect: rect,
    panel: panel,
    slot: slot,
    phaseRow: phaseRow,
    barH: barH,
    phaseBarH: phaseBarH,
    barHWithText: barHWithText,
    scrollbar: scrollbar,
    iconButton: iconButton,
    title: title,
    label: label,
    inventory: inventory,
    spacer: spacer,
    unknown: unknown
  };
})(window);
