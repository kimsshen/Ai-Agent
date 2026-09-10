const {test} = require('node:test');
const assert = require('node:assert/strict');
const charts = require('../../main/resources/static/charts.js');

test('all supported types render SVG and an accessible data table', () => {
  for (const type of ['bar', 'horizontalBar', 'line', 'area', 'pie', 'doughnut', 'scatter']) {
    const data = type === 'scatter' ? [{x: 1, y: 5}, {x: 2, y: 8}] : [{label: 'A', value: 5}, {label: 'B', value: 8}];
    const html = charts.render({type, data});
    assert.ok(html.includes('<svg'), type);
    assert.ok(html.includes('<table>'), type);
    assert.ok(!html.includes('NaN') && !html.includes('Infinity'), type);
  }
});

test('multi-series charts retain category alignment, units, negatives and zeroes', () => {
  for (const type of ['bar', 'horizontalBar', 'line', 'area']) {
    const chart = {type, unit: '℃', labels: ['早班', '晚班'], series: [{name: 'A', values: [-4, 0]}, {name: 'B', values: [8, 3]}]};
    const html = charts.render(chart);
    assert.ok(html.includes('A（℃）') && html.includes('B（℃）'));
    assert.ok(html.includes('<td>早班</td><td>-4</td><td>8</td>'));
    assert.ok(!html.includes('NaN') && !html.includes('Infinity'));
  }
  const zero = charts.render({type: 'bar', data: [{label: '零', value: 0}]});
  assert.match(zero, /height="0"/);
});

test('zero-valued and single nonzero slice charts are handled correctly', () => {
  assert.match(charts.render({type: 'pie', data: [{label: 'A', value: 0}]}), /chart-error/);
  const single = charts.render({type: 'doughnut', data: [{label: 'A', value: 5}, {label: 'B', value: 0}]});
  assert.match(single, /<circle/);
  assert.match(single, /100.0%/);
});

test('invalid or oversized data is rejected without silently dropping points', () => {
  const invalid = [
    {type: 'bar', data: [{label: 'A', value: '3'}]},
    {type: 'bar', data: [{label: 'A', value: null}]},
    {type: 'bar', data: [{label: 'A', value: Infinity}]},
    {type: 'line', labels: ['A', 'B'], series: [{values: [1]}]},
    {type: 'pie', data: [{label: 'A', value: -3}]},
    {type: 'scatter', data: [{x: 1, y: null}]},
    {type: 'bar', data: Array.from({length: 367}, () => ({label: 'A', value: 1}))},
    {type: 'constructor', data: []}
  ];
  invalid.forEach(chart => assert.match(charts.render(chart), /chart-error/));
  assert.match(charts.render('{bad json'), /chart-error/);
});

test('model-supplied labels and titles cannot inject markup', () => {
  const hostile = '<img src=x onerror="alert(1)">';
  const html = charts.render({type: 'bar', title: hostile, unit: hostile, data: [{label: hostile, value: 1}]});
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
});

test('constant scatter coordinates and large data remain finite', () => {
  const html = charts.render({type: 'scatter', data: [{x: 0, y: 0}, {x: 0, y: 0}]});
  assert.match(html, /<svg/);
  assert.ok(!html.includes('NaN') && !html.includes('Infinity'));
  assert.match(charts.render({type: 'line', data: [{label: 'A', value: -1e308}, {label: 'B', value: 1e308}]}), /chart-error/);
});
