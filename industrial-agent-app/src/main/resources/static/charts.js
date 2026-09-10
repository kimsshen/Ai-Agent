/* Declarative charts only: model output is data, never executable HTML or JavaScript. */
(function (root) {
  'use strict';
  const colors = ['#356df3', '#21a58b', '#edaa35', '#9865d8', '#e76b84', '#37a8cf', '#79859d'];
  const types = {bar: '柱状图', horizontalBar: '条形图', line: '折线图', area: '面积图', pie: '饼图', doughnut: '环形图', scatter: '散点图'};
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const format = value => value.toLocaleString('zh-CN', {maximumFractionDigits: 2});
  const color = index => colors[index % colors.length];
  const number = value => typeof value === 'number' && Number.isFinite(value);
  const text = (x, y, value, extra = '') => `<text x="${x}" y="${y}" ${extra}>${escape(value)}</text>`;

  function normalize(input) {
    if (!input || !Object.hasOwn(types, input.type)) throw new Error('暂不支持该图表类型');
    const chart = {type: input.type, title: String(input.title || types[input.type]), unit: String(input.unit || ''), xLabel: String(input.xLabel || ''), yLabel: String(input.yLabel || '')};
    if (chart.type === 'scatter') {
      if (!Array.isArray(input.data) || !input.data.length || input.data.length > 1000) throw new Error('散点数据应包含 1–1000 个点');
      chart.points = input.data.map(p => {
        if (!p || !number(p.x) || !number(p.y)) throw new Error('散点坐标必须是有效数字');
        return {x: p.x, y: p.y, label: String(p.label || '')};
      });
      return chart;
    }
    if (Array.isArray(input.data)) {
      chart.labels = input.data.map(p => String(p?.label ?? ''));
      chart.series = [{name: String(input.seriesName || '数值'), values: input.data.map(p => p?.value)}];
    } else {
      chart.labels = Array.isArray(input.labels) ? input.labels.map(String) : [];
      chart.series = Array.isArray(input.series) ? input.series.map(s => ({name: String(s?.name || '数值'), values: s?.values})) : [];
    }
    if (!chart.labels.length || chart.labels.length > 366 || chart.labels.some(label => !label.trim())) throw new Error('请提供 1–366 个有效分类');
    if (!chart.series.length || chart.series.length > 8) throw new Error('请提供 1–8 组数据');
    chart.series.forEach(s => {
      if (!Array.isArray(s.values) || s.values.length !== chart.labels.length || s.values.some(v => !number(v))) throw new Error('各组数值必须与分类一一对应，且全部为有效数字');
    });
    if (chart.type === 'pie' || chart.type === 'doughnut') {
      if (chart.series.length !== 1 || chart.labels.length > 30) throw new Error('占比图需要一组数据，最多 30 个分类');
      if (chart.series[0].values.some(v => v < 0) || chart.series[0].values.every(v => v === 0)) throw new Error('占比数据不能为负数，且合计必须大于 0');
    }
    return chart;
  }

  function legend(items) {
    return `<div class="chart-legend">${items.map((item, i) => `<span><i style="background:${color(i)}"></i>${escape(item)}</span>`).join('')}</div>`;
  }

  function circular(chart) {
    const values = chart.series[0].values;
    const total = values.reduce((a, b) => a + b, 0);
    if (!Number.isFinite(total)) throw new Error('图表数值超出支持范围');
    const radius = 108, cx = 200, cy = 140;
    let angle = -Math.PI / 2, shapes = '';
    values.forEach((value, i) => {
      if (value === 0) return;
      const next = angle + value / total * Math.PI * 2;
      const title = `<title>${escape(chart.labels[i])}：${format(value)}${escape(chart.unit)} (${(value / total * 100).toFixed(1)}%)</title>`;
      if (value === total) shapes += `<circle cx="${cx}" cy="${cy}" r="${radius}" fill="${color(i)}">${title}</circle>`;
      else shapes += `<path d="M ${cx} ${cy} L ${cx + radius * Math.cos(angle)} ${cy + radius * Math.sin(angle)} A ${radius} ${radius} 0 ${next - angle > Math.PI ? 1 : 0} 1 ${cx + radius * Math.cos(next)} ${cy + radius * Math.sin(next)} Z" fill="${color(i)}" stroke="white">${title}</path>`;
      angle = next;
    });
    if (chart.type === 'doughnut') shapes += `<circle cx="${cx}" cy="${cy}" r="66" fill="white"/>${text(cx, cy - 5, '合计', 'text-anchor="middle"')}${text(cx, cy + 20, format(total), 'text-anchor="middle" font-size="19"')}`;
    return `<svg viewBox="0 0 400 280" class="chart-circle" role="img" aria-label="${escape(chart.title)}">${shapes}</svg>${legend(chart.labels.map((label, i) => `${label} · ${format(values[i])}${chart.unit} · ${(values[i] / total * 100).toFixed(1)}%`))}`;
  }

  function cartesian(chart) {
    const scatter = chart.type === 'scatter', horizontal = chart.type === 'horizontalBar';
    const count = scatter ? 0 : chart.labels.length;
    const width = horizontal || scatter ? 680 : Math.max(680, count * Math.max(45, chart.series.length * 16));
    const height = horizontal ? Math.max(340, count * (chart.series.length * 18 + 18) + 90) : 360;
    const left = horizontal ? 155 : 70, right = width - 30, top = 28, bottom = height - (horizontal ? 55 : 88);
    const values = scatter ? chart.points.map(p => p.y) : chart.series.flatMap(s => s.values);
    let minimum = Math.min(0, ...values), maximum = Math.max(0, ...values);
    if (maximum === minimum) maximum = minimum + 1;
    if (!Number.isFinite(maximum - minimum)) throw new Error('图表数值超出支持范围');
    const scale = value => horizontal ? left + (value - minimum) / (maximum - minimum) * (right - left) : bottom - (value - minimum) / (maximum - minimum) * (bottom - top);
    let shapes = '';
    for (let i = 0; i <= 4; i++) {
      const value = minimum + (maximum - minimum) * i / 4, position = scale(value);
      shapes += horizontal
        ? `<path d="M ${position} ${top} V ${bottom}" class="chart-grid"/>${text(position, bottom + 22, format(value), 'text-anchor="middle"')}`
        : `<path d="M ${left} ${position} H ${right}" class="chart-grid"/>${text(left - 8, position + 4, format(value), 'text-anchor="end"')}`;
    }
    if (scatter) {
      let minX = Math.min(...chart.points.map(p => p.x)), maxX = Math.max(...chart.points.map(p => p.x));
      if (minX === maxX) { minX -= 1; maxX += 1; }
      if (!Number.isFinite(maxX - minX) || maxX === minX) throw new Error('散点坐标超出支持范围');
      const scaleX = value => left + (value - minX) / (maxX - minX) * (right - left);
      for (let i = 0; i <= 4; i++) {
        const x = minX + (maxX - minX) * i / 4;
        shapes += text(scaleX(x), bottom + 24, format(x), 'text-anchor="middle"');
      }
      shapes += chart.points.map(p => `<circle cx="${scaleX(p.x)}" cy="${scale(p.y)}" r="5" fill="${color(0)}" opacity=".75"><title>${escape(p.label)} (${format(p.x)}, ${format(p.y)})</title></circle>`).join('');
    } else {
      const step = (horizontal ? bottom - top : right - left) / count;
      const position = i => (horizontal ? top : left) + step * (i + .5);
      chart.labels.forEach((label, i) => {
        const short = label.length > 18 ? label.slice(0, 17) + '…' : label;
        shapes += horizontal
          ? text(left - 10, position(i) + 4, short, 'text-anchor="end"')
          : text(position(i), bottom + 18, short, `text-anchor="end" transform="rotate(-35 ${position(i)} ${bottom + 18})"`);
      });
      chart.series.forEach((series, s) => {
        const coordinates = series.values.map((value, i) => [position(i), scale(value)]);
        if (chart.type === 'line' || chart.type === 'area') {
          const path = coordinates.map(([x, y], i) => `${i ? 'L' : 'M'} ${x} ${y}`).join(' ');
          if (chart.type === 'area') shapes += `<path d="${path} L ${position(count - 1)} ${scale(0)} L ${position(0)} ${scale(0)} Z" fill="${color(s)}" opacity=".14"/>`;
          shapes += `<path d="${path}" fill="none" stroke="${color(s)}" stroke-width="2.5"/>`;
        }
        series.values.forEach((value, i) => {
          const title = `<title>${escape(series.name)} · ${escape(chart.labels[i])}：${format(value)}${escape(chart.unit)}</title>`;
          if (chart.type === 'line' || chart.type === 'area') shapes += `<circle cx="${position(i)}" cy="${scale(value)}" r="3.5" fill="${color(s)}">${title}</circle>`;
          else {
            const thickness = step * .72 / chart.series.length;
            const start = position(i) - step * .36 + s * thickness;
            const baseline = Math.min(scale(0), scale(value)), size = Math.abs(scale(value) - scale(0));
            shapes += horizontal
              ? `<rect x="${baseline}" y="${start}" width="${size}" height="${thickness * .9}" fill="${color(s)}">${title}</rect>`
              : `<rect x="${start}" y="${baseline}" width="${thickness * .9}" height="${size}" fill="${color(s)}">${title}</rect>`;
          }
        });
      });
    }
    shapes += text(left, 16, chart.yLabel || chart.unit);
    shapes += text(right, height - 6, chart.xLabel, 'text-anchor="end"');
    return `<div class="chart-scroll"><svg viewBox="0 0 ${width} ${height}" style="min-width:${width}px" role="img" aria-label="${escape(chart.title)}">${shapes}</svg></div>${scatter ? '' : legend(chart.series.map(s => s.name))}`;
  }

  function table(chart) {
    const headers = chart.type === 'scatter' ? ['名称', chart.xLabel || 'X', chart.yLabel || 'Y'] : ['分类', ...chart.series.map(s => s.name + (chart.unit ? `（${chart.unit}）` : ''))];
    const rows = chart.type === 'scatter' ? chart.points.map(p => [p.label, p.x, p.y]) : chart.labels.map((label, i) => [label, ...chart.series.map(s => s.values[i])]);
    return `<details class="chart-data"><summary>查看数据</summary><div class="chart-scroll"><table><thead><tr>${headers.map(h => `<th>${escape(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(row => `<tr>${row.map(v => `<td>${escape(v)}</td>`).join('')}</tr>`).join('')}</tbody></table></div></details>`;
  }

  function render(raw) {
    try {
      const chart = normalize(typeof raw === 'string' ? JSON.parse(raw) : raw);
      const body = chart.type === 'pie' || chart.type === 'doughnut' ? circular(chart) : cartesian(chart);
      return `<section class="chart-card"><div class="chart-head"><div class="chart-title">${escape(chart.title)}</div><span>${types[chart.type]}</span></div>${body}${table(chart)}</section>`;
    } catch (error) {
      return `<div class="chart-error">图表无法显示：${escape(error.message)}。请尝试重新生成。</div>`;
    }
  }
  // Find an entire JSON object without treating braces inside a label as delimiters.
  function objectEnd(source, start) {
    let depth = 0, quoted = false, escaped = false;
    for (let i = start; i < source.length; i++) {
      const c = source[i];
      if (quoted) {
        if (escaped) escaped = false;
        else if (c === '\\') escaped = true;
        else if (c === '"') quoted = false;
      } else if (c === '"') quoted = true;
      else if (c === '{') depth++;
      else if (c === '}' && --depth === 0) return i + 1;
    }
    return -1;
  }

  function plainText(value) {
    return escape(value).split(/\n{2,}/).filter(Boolean)
      .map(block => `<p>${block.replace(/\n/g, '<br>')}</p>`).join('');
  }

  // Models sometimes omit fences or use a json fence. Accept only chart-shaped
  // objects; ordinary JSON and code examples remain text. Never evaluate output.
  function renderMessage(value, {streaming = false} = {}) {
    const source = String(value ?? '');
    let html = '', consumed = 0, cursor = 0;
    const pending = () => streaming
      ? '<div class="chart-loading">正在生成图表…</div>'
      : '<div class="chart-error">图表数据未完整返回，请重新生成。</div>';
    while (cursor < source.length) {
      const start = source.indexOf('{', cursor);
      if (start < 0) break;
      const before = source.slice(consumed, start);
      const wrapper = before.match(/(?:```[ \t]*(chart|json)?|(?<![a-z0-9_])(chart)\b)(?:\s|\\[nr]?|[:：])*$/i);
      const explicitChart = wrapper && (wrapper[1] || wrapper[2] || '').toLowerCase() === 'chart';
      const end = objectEnd(source, start);
      const raw = source.slice(start, end < 0 ? undefined : end);
      const chartHead = /^\{\s*"type"\s*:\s*"(?:bar|horizontalBar|line|area|pie|doughnut|scatter)"/i.test(raw);
      let parsed;
      if (end >= 0) {
        try { parsed = JSON.parse(raw); } catch (_) { /* render shows a safe error for chart output */ }
      }
      const chartShape = parsed && typeof parsed.type === 'string'
        && (Array.isArray(parsed.data) || Array.isArray(parsed.series));
      if (!explicitChart && !chartHead && !chartShape) {
        cursor = end < 0 ? source.length : end;
        continue;
      }
      const begin = wrapper ? consumed + wrapper.index : start;
      html += plainText(source.slice(consumed, begin));
      if (end < 0) return html + pending();
      html += render(parsed || raw);
      const closingFence = source.slice(end).match(/^(?:\s|\\[nr]?)*```/);
      consumed = end + (closingFence ? closingFence[0].length : 0);
      cursor = consumed;
    }
    const tail = source.slice(consumed);
    // Hold a trailing marker while its JSON arrives, including a split code fence.
    const marker = tail.match(/(?:```[ \t]*(?:chart|json)?|(?<![a-z0-9_])chart\b)(?:\s|\\[nr]?|[:：])*$/i);
    if (marker) return html + plainText(tail.slice(0, marker.index)) + pending();
    return html + plainText(tail);
  }

  root.IndustrialCharts = {render, normalize, renderMessage, plainText};
  if (typeof module !== 'undefined') module.exports = root.IndustrialCharts;
})(globalThis);
