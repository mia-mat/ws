/* battlecode.mia.ws: renders the team's status.json (schema 1, see the PR that added this file).
 *
 * The feed URL comes from #app[data-feed] (property battlecode.feed). It is fetched from the browser every
 * two minutes; the team's machine pushes it every ~5 minutes. Everything in the feed is treated as untrusted
 * text: it is inserted with textContent, and links are only built from validated parts (owner/repo, numbers).
 * The only browser storage is the light/dark choice.
 */
(() => {
    'use strict';

    const REFRESH_MS = 2 * 60 * 1000;
    const TICK_MS = 30 * 1000;
    const STALE_WARN_MIN = 15;
    const STALE_BAD_MIN = 60;
    const SVG_NS = 'http://www.w3.org/2000/svg';
    const GITHUB = 'https://github.com/';

    const app = document.getElementById('app');
    const statusEl = document.getElementById('status');
    const footEl = document.getElementById('foot');
    const themeBtn = document.getElementById('theme');
    const feedUrl = ((app && app.dataset.feed) || '').trim();

    let data = null;
    let lastError = '';
    const openDetails = new Set();
    let observers = [];

    // ---------- DOM helpers ----------

    function h(tag, attrs, ...kids) {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(attrs || {})) {
            if (value == null || value === false) continue;
            if (key === 'class') node.className = value;
            else if (key === 'text') node.textContent = value;
            else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
            else node.setAttribute(key, value === true ? '' : String(value));
        }
        append(node, kids);
        return node;
    }

    function append(node, kids) {
        for (const kid of kids.flat(Infinity)) {
            if (kid == null || kid === false || kid === '') continue;
            node.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
        }
        return node;
    }

    // Replace a node's children, skipping null/false/'' (replaceChildren would print them as text).
    function fill(node, ...kids) {
        node.replaceChildren();
        return append(node, kids);
    }

    function svg(tag, attrs) {
        const node = document.createElementNS(SVG_NS, tag);
        for (const [key, value] of Object.entries(attrs || {})) if (value != null) node.setAttribute(key, String(value));
        return node;
    }

    function body(id) {
        return document.querySelector('#' + id + ' .body');
    }

    function link(href, text) {
        return href ? h('a', {href, rel: 'noopener'}, text) : document.createTextNode(text);
    }

    // A <details> that stays open across re-renders.
    function details(key, summary, content, openByDefault) {
        const node = h('details', {'data-key': key}, h('summary', null, summary), content);
        node.open = openDetails.has(key) || (openByDefault && !openDetails.has('closed:' + key));
        node.addEventListener('toggle', () => {
            openDetails.delete(key);
            openDetails.delete('closed:' + key);
            openDetails.add(node.open ? key : 'closed:' + key);
        });
        return node;
    }

    // ---------- formatting ----------

    const toMs = (iso) => {
        const ms = typeof iso === 'string' ? Date.parse(iso) : NaN;
        return Number.isFinite(ms) ? ms : null;
    };
    const isNum = (v) => typeof v === 'number' && Number.isFinite(v);
    const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
    const pct = (v) => isNum(v) ? Math.round(v) + '%' : '–';
    const usd = (v) => isNum(v) ? '$' + v.toFixed(2) : '–';

    const clockFmt = new Intl.DateTimeFormat('en-GB', {hour: '2-digit', minute: '2-digit'});
    const dayClockFmt = new Intl.DateTimeFormat('en-GB', {weekday: 'short', hour: '2-digit', minute: '2-digit'});
    const dayFmt = new Intl.DateTimeFormat('en-GB', {weekday: 'short', day: 'numeric'});
    const fullFmt = new Intl.DateTimeFormat('en-GB', {weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'});

    function sameDay(a, b) {
        const x = new Date(a), y = new Date(b);
        return x.getFullYear() === y.getFullYear() && x.getMonth() === y.getMonth() && x.getDate() === y.getDate();
    }

    // "07:20" today, "Wed 18:00" on other days
    function clock(ms) {
        return sameDay(ms, Date.now()) ? clockFmt.format(ms) : dayClockFmt.format(ms);
    }

    function span(minutes) {
        const m = Math.round(Math.abs(minutes));
        if (m < 60) return m + ' min';
        const hours = Math.floor(m / 60), rest = m % 60;
        if (hours < 10 && rest) return hours + ' h ' + rest + ' min';
        if (hours < 48) return hours + ' h';
        return Math.floor(hours / 24) + ' d ' + (hours % 24) + ' h';
    }

    function ago(ms) {
        const minutes = (Date.now() - ms) / 60000;
        if (minutes < 1) return 'just now';
        return minutes < 0 ? 'in ' + span(minutes) : span(minutes) + ' ago';
    }

    function until(ms) {
        const minutes = (ms - Date.now()) / 60000;
        if (Math.abs(minutes) < 1) return 'now';
        return minutes > 0 ? 'in ' + span(minutes) : span(minutes) + ' ago';
    }

    // A <time> element that the 30-second tick keeps current.
    function when(iso, mode) {
        const ms = toMs(iso);
        if (ms == null) return h('span', {class: 'muted', text: 'unknown'});
        const node = h('time', {datetime: new Date(ms).toISOString(), 'data-mode': mode, title: fullFmt.format(ms)});
        node.textContent = timeText(ms, mode);
        return node;
    }

    function timeText(ms, mode) {
        if (mode === 'ago') return ago(ms);
        if (mode === 'until') return until(ms);
        if (mode === 'at-until') return clock(ms) + ' (' + until(ms) + ')';
        if (mode === 'at-ago') return clock(ms) + ' (' + ago(ms) + ')';
        return clock(ms);
    }

    function tick() {
        for (const node of document.querySelectorAll('time[data-mode]')) {
            const ms = Date.parse(node.getAttribute('datetime'));
            if (Number.isFinite(ms)) node.textContent = timeText(ms, node.dataset.mode);
        }
        renderStatus();
    }

    // ---------- links (only from validated parts) ----------

    function repoOf(d) {
        return typeof d.repo === 'string' && /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(d.repo) ? d.repo : null;
    }

    function issueUrl(d, number) {
        const repo = repoOf(d);
        return repo && Number.isInteger(number) && number > 0 ? GITHUB + repo + '/issues/' + number : null;
    }

    function argonEditUrl(d, member) {
        const repo = repoOf(d);
        const ref = typeof d.argon.ref === 'string' && /^[A-Za-z0-9_.\/-]+$/.test(d.argon.ref) ? d.argon.ref : null;
        const path = typeof member.path === 'string' && /^[A-Za-z0-9_.\/-]+$/.test(member.path) ? member.path : null;
        if (!repo || !ref || !path) return null;
        return member.exists
            ? GITHUB + repo + '/edit/' + ref + '/' + path
            : GITHUB + repo + '/new/' + ref + '?filename=' + encodeURIComponent(path);
    }

    // ---------- small figures ----------

    function tile(label, value, ...sub) {
        return h('div', {class: 'tile'},
            h('div', {class: 'label', text: label}),
            h('div', {class: 'value', text: value}),
            h('div', {class: 'sub'}, sub));
    }

    // Value 24 h ago from a history table, for deltas.
    function dayAgo(history, column) {
        const rows = rowsOf(history);
        const i = history && history.columns ? history.columns.indexOf(column) : -1;
        if (i < 0 || rows.length < 2) return null;
        const target = Date.now() - 24 * 3600 * 1000;
        let best = null;
        for (const row of rows) {
            const t = toMs(row[0]);
            if (t == null || !isNum(row[i])) continue;
            if (t <= target + 30 * 60 * 1000) best = row[i];
        }
        return best;
    }

    function delta(diff, upIsGood, unit, digits) {
        if (!isNum(diff) || Math.abs(diff) < Math.pow(10, -(digits || 0)) / 2) return h('span', null, 'no change in 24 h');
        const good = upIsGood ? diff > 0 : diff < 0;
        const text = (diff > 0 ? '↑' : '↓') + Math.abs(diff).toFixed(digits || 0) + (unit || '') + ' in 24 h';
        return h('span', {class: 'delta ' + (good ? 'good' : 'bad'), text});
    }

    // A used-% meter. pace = fraction of the window already elapsed (a tick on the bar), or null.
    function meter(name, used, sub, pace, valueText) {
        const value = isNum(used) ? clamp(used, 0, 100) : null;
        const flag = value == null ? null
            : value >= 90 ? h('span', {class: 'flag bad', text: 'near limit'})
            : value >= 75 ? h('span', {class: 'flag warn', text: 'high'}) : null;
        const bar = h('div', {class: 'bar', role: 'img', 'aria-label': name + ': ' + (value == null ? 'no data' : value + '% used')});
        if (value != null) bar.append(h('div', {class: 'fill' + (value >= 90 ? ' bad' : ''), style: 'width:' + value + '%'}));
        if (isNum(pace)) bar.append(h('div', {class: 'pace', title: 'time elapsed in this window', style: 'left:' + clamp(pace * 100, 0, 100) + '%'}));
        return h('div', {class: 'meter'},
            h('div', {class: 'name'}, name, flag),
            h('div', {class: 'val num', text: valueText || (value == null ? '–' : Math.round(value) + '% used')}),
            bar,
            sub ? h('div', {class: 'sub'}, sub) : null);
    }

    // ---------- line chart ----------
    // One y-axis, 2px lines, end labels, legend for 2+ series, crosshair + tooltip (pointer and keyboard),
    // and a table twin. o = {title, history, columns:[{key, name, color}], yMin, yMax, yTicks, fmt, reversed}

    function rowsOf(history) {
        return history && Array.isArray(history.rows) ? history.rows.filter((row) => Array.isArray(row) && toMs(row[0]) != null) : [];
    }

    function lineChart(o) {
        const rows = rowsOf(o.history);
        const index = (key) => (o.history && Array.isArray(o.history.columns) ? o.history.columns.indexOf(key) : -1);
        const series = o.columns.map((c) => ({...c, i: index(c.key)})).filter((c) => c.i > 0);
        const fig = h('figure', {class: 'chart'}, h('figcaption', {text: o.title}));
        const points = rows.filter((row) => series.some((c) => isNum(row[c.i])));
        if (points.length < 2 || !series.length) {
            fig.append(h('p', {class: 'empty', text: 'Not enough history yet; the chart fills in as the feed keeps running.'}));
            return fig;
        }
        if (series.length > 1) {
            fig.append(h('div', {class: 'legend'}, series.map((c) =>
                h('span', null, h('span', {class: 'key', style: 'background:' + c.color}), c.name))));
        }
        const plot = h('div', {class: 'plot'});
        const tip = h('div', {class: 'tip', hidden: true});
        plot.append(tip);
        fig.append(plot, tableTwin(o.title, points, series, o.fmt));
        // Draw at the plot's real width, and again whenever that width changes (rotation, fonts, layout).
        const observer = new ResizeObserver(() => drawLines(plot, tip, points, series, o));
        observer.observe(plot);
        observers.push(observer);
        return fig;
    }

    function niceTicks(lo, hi, count) {
        const range = hi - lo || 1;
        const raw = range / Math.max(1, count);
        const mag = Math.pow(10, Math.floor(Math.log10(raw)));
        const step = [1, 2, 2.5, 5, 10].map((f) => f * mag).find((s) => s >= raw) || raw;
        const ticks = [];   // from below the data to above it, so nothing is clamped to the plot edge
        for (let v = Math.floor(lo / step) * step; v <= Math.ceil(hi / step) * step + step * 1e-9; v += step) ticks.push(+v.toFixed(10));
        return ticks;
    }

    function timeTicks(t0, t1, width) {
        const HOUR = 3600 * 1000;
        const maxTicks = Math.max(2, Math.floor(width / 64));
        const stepH = [1, 2, 3, 6, 12, 24, 48, 72, 96, 120, 168].find((s) => (t1 - t0) / (s * HOUR) <= maxTicks) || 168;
        const ticks = [];
        const d = new Date(t0);
        d.setMinutes(0, 0, 0);
        if (stepH >= 24) {
            d.setHours(0);
            for (let k = 0; d.getTime() <= t1; k++, d.setDate(d.getDate() + 1)) {
                if (d.getTime() >= t0 && k % (stepH / 24) === 0) ticks.push(d.getTime());
            }
        } else {
            for (; d.getTime() <= t1; d.setHours(d.getHours() + 1)) {
                if (d.getTime() >= t0 && d.getHours() % stepH === 0) ticks.push(d.getTime());
            }
        }
        return {ticks, daily: stepH >= 24};
    }

    function drawLines(plot, tip, rows, series, o) {
        if (!plot.isConnected || plot.clientWidth === 0) return;
        const width = Math.max(260, Math.floor(plot.clientWidth));
        if (plot._width === width && plot.querySelector('svg')) return;
        plot._width = width;
        const old = plot.querySelector('svg');
        if (old) old.remove();

        const height = 190;
        const m = {top: 10, right: series.length > 1 ? 92 : 52, bottom: 24, left: 46};
        const pw = width - m.left - m.right;
        const ph = height - m.top - m.bottom;
        const times = rows.map((row) => toMs(row[0]));
        const t0 = times[0], t1 = times[times.length - 1], tspan = Math.max(1, t1 - t0);
        const x = (t) => m.left + ((t - t0) / tspan) * pw;
        const y = (v) => {
            let f = (v - o.yMin) / ((o.yMax - o.yMin) || 1);
            if (o.reversed) f = 1 - f;
            return m.top + (1 - clamp(f, 0, 1)) * ph;
        };

        const root = svg('svg', {width, height, viewBox: '0 0 ' + width + ' ' + height, role: 'img', tabindex: 0,
            'aria-label': o.title + ' chart; use the arrow keys for values, or open the table below'});

        for (const v of o.yTicks) {
            root.append(svg('line', {class: 'grid', x1: m.left, x2: m.left + pw, y1: y(v), y2: y(v)}));
            const label = svg('text', {class: 'axis', x: m.left - 6, y: y(v) + 4, 'text-anchor': 'end'});
            label.textContent = o.fmt(v);
            root.append(label);
        }
        root.append(svg('line', {class: 'baseline', x1: m.left, x2: m.left + pw, y1: m.top + ph, y2: m.top + ph}));
        const {ticks, daily} = timeTicks(t0, t1, pw);
        for (const t of ticks) {
            const label = svg('text', {class: 'axis', x: x(t), y: height - 6, 'text-anchor': 'middle'});
            const d = new Date(t);
            label.textContent = daily || (d.getHours() === 0 && d.getMinutes() === 0) ? dayFmt.format(t) : clockFmt.format(t);
            root.append(label);
        }

        // Lines (a gap in the data breaks the line), then end dots and end labels.
        const ends = [];
        for (const c of series) {
            let path = '', pen = false, last = null;
            rows.forEach((row, k) => {
                const v = row[c.i];
                if (!isNum(v)) { pen = false; return; }
                path += (pen ? 'L' : 'M') + x(times[k]).toFixed(1) + ' ' + y(v).toFixed(1);
                pen = true;
                last = {t: times[k], v};
            });
            root.append(svg('path', {class: 'series', d: path, stroke: c.color}));
            if (last) ends.push({c, x: x(last.t), y: y(last.v), v: last.v});
        }
        ends.sort((a, b) => a.y - b.y);
        let floor = -Infinity;
        for (const e of ends) floor = e.ly = Math.max(e.y, floor + 13);
        const overflow = ends.length ? ends[ends.length - 1].ly - (m.top + ph) : 0;
        for (const e of ends) {
            const ly = overflow > 0 ? e.ly - overflow : e.ly;
            if (Math.abs(ly - e.y) > 2) root.append(svg('line', {class: 'leader', x1: e.x + 5, y1: e.y, x2: m.left + pw + 6, y2: ly}));
            root.append(svg('circle', {class: 'dot', cx: e.x, cy: e.y, r: 4, fill: e.c.color}));
            const label = svg('text', {class: 'endlabel', x: m.left + pw + 8, y: ly + 4});
            label.textContent = (series.length > 1 ? e.c.short || e.c.name : '') + (series.length > 1 ? ' ' : '') + o.fmt(e.v);
            root.append(label);
        }

        // Crosshair layer.
        const cross = svg('line', {class: 'cross', y1: m.top, y2: m.top + ph, visibility: 'hidden'});
        const marks = series.map((c) => svg('circle', {class: 'dot', r: 4, fill: c.color, visibility: 'hidden'}));
        root.append(cross, ...marks);
        const hit = svg('rect', {x: m.left, y: m.top, width: pw, height: ph, fill: 'transparent'});
        root.append(hit);

        let current = -1;
        const show = (k) => {
            current = clamp(k, 0, rows.length - 1);
            const cx = x(times[current]);
            cross.setAttribute('x1', cx);
            cross.setAttribute('x2', cx);
            cross.setAttribute('visibility', 'visible');
            fill(tip, h('div', {class: 'when', text: fullFmt.format(times[current])}));
            series.forEach((c, j) => {
                const v = rows[current][c.i];
                if (isNum(v)) {
                    marks[j].setAttribute('cx', cx);
                    marks[j].setAttribute('cy', y(v));
                    marks[j].setAttribute('visibility', 'visible');
                } else marks[j].setAttribute('visibility', 'hidden');
                tip.append(h('div', {class: 'row'}, h('span', {class: 'key', style: 'background:' + c.color}),
                    h('strong', {text: isNum(v) ? o.fmt(v) : '–'}), h('span', {text: c.name})));
            });
            tip.hidden = false;
            const tw = tip.offsetWidth;
            tip.style.left = (cx + 12 + tw > width ? Math.max(0, cx - 12 - tw) : cx + 12) + 'px';
            tip.style.top = m.top + 'px';
        };
        const hide = () => {
            current = -1;
            cross.setAttribute('visibility', 'hidden');
            marks.forEach((mk) => mk.setAttribute('visibility', 'hidden'));
            tip.hidden = true;
        };
        const nearest = (px) => {
            const t = t0 + ((px - m.left) / pw) * tspan;
            let best = 0;
            times.forEach((ti, k) => { if (Math.abs(ti - t) < Math.abs(times[best] - t)) best = k; });
            return best;
        };
        const fromPointer = (ev) => {   // CSS pixels -> SVG units, in case the svg is scaled down
            const box = root.getBoundingClientRect();
            show(nearest((ev.clientX - box.left) * (width / (box.width || width))));
        };
        root.addEventListener('pointerdown', fromPointer);
        root.addEventListener('pointermove', fromPointer);
        root.addEventListener('pointerleave', hide);
        root.addEventListener('focus', () => show(current < 0 ? rows.length - 1 : current));
        root.addEventListener('blur', hide);
        root.addEventListener('keydown', (ev) => {
            if (ev.key === 'ArrowLeft' || ev.key === 'ArrowRight') {
                ev.preventDefault();
                show((current < 0 ? rows.length - 1 : current) + (ev.key === 'ArrowLeft' ? -1 : 1));
            } else if (ev.key === 'Home' || ev.key === 'End') {
                ev.preventDefault();
                show(ev.key === 'Home' ? 0 : rows.length - 1);
            } else if (ev.key === 'Escape') hide();
        });
        plot.prepend(root);
    }

    // The accessible twin of a chart: every value, newest first, built when opened.
    function tableTwin(title, rows, series, fmt) {
        const holder = h('div', {class: 'scroll'});
        const node = details('table:' + title, 'Table view', holder, false);
        node.classList.add('tableview');
        const build = () => {
            if (!node.open || holder.firstChild) return;
            const head = h('tr', null, h('th', {text: 'time'}), series.map((c) => h('th', {text: c.name})));
            const lines = rows.slice().reverse().map((row) => h('tr', null,
                h('td', {text: fullFmt.format(toMs(row[0]))}),
                series.map((c) => h('td', {text: isNum(row[c.i]) ? fmt(row[c.i]) : '–'}))));
            holder.append(h('table', null, h('caption', {class: 'muted small', text: title}), h('thead', null, head), h('tbody', null, lines)));
        };
        node.addEventListener('toggle', build);
        requestAnimationFrame(build);
        return node;
    }

    // ---------- sections ----------

    function renderKpis(d) {
        const node = document.getElementById('kpis');
        const tiles = [];
        const ladder = d.ladder || {};
        const rankThen = dayAgo(ladder.history, 'rank');
        const ratingThen = dayAgo(ladder.history, 'rating');
        tiles.push(tile('Ladder rank', isNum(ladder.rank) ? '#' + ladder.rank : '–',
            isNum(ladder.teams) ? 'of ' + ladder.teams + ' · ' : '',
            isNum(rankThen) && isNum(ladder.rank) ? delta(rankThen - ladder.rank, true, '', 0) : 'no 24 h history yet'));
        tiles.push(tile('Rating', isNum(ladder.rating) ? ladder.rating.toFixed(1) : '–',
            isNum(ratingThen) && isNum(ladder.rating) ? delta(ladder.rating - ratingThen, true, '', 1) : 'no 24 h history yet'));

        const items = (d.agents && Array.isArray(d.agents.items)) ? d.agents.items : [];
        const count = (state) => items.filter((a) => a.state === state).length;
        tiles.push(tile('Agents running', String(count('running')),
            count('failed') + ' failed · ' + count('finished') + ' finished'));

        const scrims = d.scrims || {};
        tiles.push(tile('Scrims today', isNum(scrims.used) && isNum(scrims.limit) ? scrims.used + '/' + scrims.limit : '–',
            'resets ', when(scrims.resets_at, 'at-until')));

        const windows = (d.claude && Array.isArray(d.claude.windows)) ? d.claude.windows : [];
        for (const key of ['week', 'session']) {
            const w = windows.find((x) => x.key === key);
            if (w) tiles.push(tile(key === 'week' ? 'Claude week' : 'Claude session', pct(w.used_pct), 'resets ', when(w.resets_at, 'at-until')));
        }
        fill(node, ...tiles);
    }

    function agentCard(d, a) {
        const title = h('span', {class: 'title'},
            link(issueUrl(d, a.issue), '#' + a.issue), ' ', a.title || '(no title)');
        const meta = [];
        if (a.state === 'running') {
            meta.push(h('span', null, 'running since ', when(a.started_at, 'at-ago')));
            if (a.runs > 1) meta.push(h('span', {text: 'run ' + a.runs}));
        } else {
            if (a.started_at) meta.push(h('span', null, 'started ', when(a.started_at, 'at')));
            if (a.ended_at) meta.push(h('span', null, 'ended ', when(a.ended_at, 'at-ago')));
            if (a.runs > 1) meta.push(h('span', {text: a.runs + ' runs'}));
        }
        if (a.model) meta.push(h('span', {text: a.model}));
        let cost;
        if (a.state === 'running') {
            cost = (isNum(a.cost_usd) && a.cost_usd > 0 ? usd(a.cost_usd) + ' so far + this run' : 'cost shows when this run ends')
                + (isNum(a.budget_usd) ? ' (cap ' + usd(a.budget_usd) + ')' : '');
        } else {
            cost = usd(a.cost_usd);
        }
        meta.push(h('span', {text: cost}));

        const kids = [h('div', {class: 'head'}, title, h('span', {class: 'chip ' + a.state, text: a.state}))];
        kids.push(h('div', {class: 'meta'}, meta));
        if (a.last_line) {
            const prefix = a.state === 'running' && a.last_line_run ? 'run ' + a.last_line_run + ' ended: ' : '';
            const last = h('div', {class: 'last', title: 'click to expand', text: prefix + a.last_line});
            last.addEventListener('click', () => last.classList.toggle('open'));
            kids.push(last);
        }
        return h('li', {class: 'agent'}, kids);
    }

    function renderAgents(d) {
        const node = body('agents');
        const items = (d.agents && Array.isArray(d.agents.items)) ? d.agents.items.slice() : [];
        if (!items.length) {
            fill(node, h('p', {class: 'empty', text: 'No agent units or results found on cumin.'}));
            return;
        }
        const t = (a) => toMs(a.ended_at) || toMs(a.started_at) || 0;
        const running = items.filter((a) => a.state === 'running').sort((a, b) => (toMs(b.started_at) || 0) - (toMs(a.started_at) || 0));
        const rest = items.filter((a) => a.state !== 'running').sort((a, b) => t(b) - t(a));
        const latest = rest.filter((a) => t(a) >= Date.now() - 12 * 3600 * 1000).slice(0, 4);
        const older = rest.filter((a) => !latest.includes(a));
        const list = (agents) => h('ul', {class: 'agent-list cols'}, agents.map((a) => agentCard(d, a)));
        const spent = items.reduce((sum, a) => sum + (isNum(a.cost_usd) ? a.cost_usd : 0), 0);
        const counts = ['running', 'finished', 'failed', 'stopped']
            .map((s) => [s, items.filter((a) => a.state === s).length]).filter(([, n]) => n).map(([s, n]) => n + ' ' + s);
        fill(node,
            h('p', {class: 'note'}, counts.join(' · ') + ' · ' + usd(spent) + ' on finished runs · as of ', when(d.agents.at, 'ago')),
            h('h3', {text: 'Running (' + running.length + ')'}),
            running.length ? list(running) : h('p', {class: 'empty', text: 'Nothing running right now.'}),
            latest.length ? h('h3', {text: 'Latest results'}) : null,
            latest.length ? list(latest) : null,
            older.length ? details('agents-older', 'Older (' + older.length + ')', list(older), false) : null);
    }

    function paceOf(w) {
        const reset = toMs(w.resets_at);
        if (reset == null || !isNum(w.period_hours) || w.period_hours <= 0) return null;
        return 1 - (reset - Date.now()) / (w.period_hours * 3600 * 1000);
    }

    function renderClaude(d) {
        const node = body('claude');
        const c = d.claude || {};
        const windows = Array.isArray(c.windows) ? c.windows : [];
        const meters = windows.map((w) => meter(w.label || w.key, w.used_pct,
            w.resets_at ? h('span', null, 'resets ', when(w.resets_at, 'at-until')) : null, paceOf(w)));
        const colors = {session: 'var(--series-1)', week: 'var(--series-2)', week_fable: 'var(--series-3)'};
        const names = Object.fromEntries(windows.map((w) => [w.key, w.label || w.key]));
        const shorts = {session: 'session', week: 'week', week_fable: 'Fable'};
        const columns = ['session', 'week', 'week_fable'].map((key) => ({key, name: names[key] || key, short: shorts[key], color: colors[key]}));
        fill(node,
            windows.length ? h('div', {class: 'meters'}, meters) : h('p', {class: 'empty', text: 'No reading yet.'}),
            h('p', {class: 'note'}, 'The tick on each bar is how much of that window has passed. Reading taken ', when(c.at, 'ago'), '.'),
            lineChart({title: '% used over time', history: c.history, columns, yMin: 0, yMax: 100,
                yTicks: [0, 25, 50, 75, 100], fmt: (v) => Math.round(v) + '%'}));
    }

    function renderArgon(d) {
        const node = body('argon');
        const a = d.argon || {};
        const members = Array.isArray(a.members) ? a.members : [];
        const rows = members.map((m) => {
            const reported = toMs(m.reported_at);
            const stale = reported != null && Date.now() - reported > 24 * 3600 * 1000;
            const editUrl = argonEditUrl(d, m);
            const sub = [];
            if (reported != null) sub.push('reported ', when(m.reported_at, 'ago'), stale ? ' (old)' : '');
            else sub.push('no report yet');
            if (m.note) sub.push(' · ', m.note);
            if (editUrl) sub.push(' · ', link(editUrl, reported != null ? 'update' : 'report'));
            return meter(m.name, reported != null ? m.used_pct : null, h('span', null, sub), null);
        });
        fill(node,
            members.length ? h('div', {class: 'meters'}, rows) : h('p', {class: 'empty', text: 'No members configured.'}),
            h('p', {class: 'note', text: 'Argon has no usage API, so each of us reports by hand: follow your link, put the % used on the first line (a short note after it is fine) and commit. The age is the commit time.'}));
    }

    function renderLadder(d) {
        const node = body('ladder');
        const l = d.ladder || {};
        const rows = rowsOf(l.history);
        const col = (key) => (l.history && Array.isArray(l.history.columns) ? l.history.columns.indexOf(key) : -1);
        const values = (key) => rows.map((row) => row[col(key)]).filter(isNum);
        const ratings = values('rating'), ranks = values('rank');
        const kids = [h('p', {class: 'note'},
            isNum(l.rank) ? 'Rank #' + l.rank + (isNum(l.teams) ? ' of ' + l.teams : '') : 'No rank yet',
            isNum(l.rating) ? ' · rating ' + l.rating.toFixed(1) : '',
            ' · as of ', when(l.at, 'ago'))];
        if (ratings.length) {
            const lo = Math.min(...ratings), hi = Math.max(...ratings), pad = Math.max(5, (hi - lo) * 0.15);
            const ticks = niceTicks(lo - pad, hi + pad, 4);
            kids.push(lineChart({title: 'Rating', history: l.history, columns: [{key: 'rating', name: 'rating', color: 'var(--series-1)'}],
                yMin: ticks[0], yMax: ticks[ticks.length - 1], yTicks: ticks, fmt: (v) => v.toFixed(0)}));
        }
        if (ranks.length) {
            const lo = Math.min(...ranks), hi = Math.max(...ranks), pad = Math.max(3, (hi - lo) * 0.15);
            const ticks = niceTicks(Math.max(1, lo - pad), hi + pad, 4).filter((v) => v >= 1);
            kids.push(lineChart({title: 'Rank (1 is top, so the axis is flipped: up is better)', history: l.history,
                columns: [{key: 'rank', name: 'rank', color: 'var(--series-1)'}],
                yMin: Math.min(ticks[0], lo), yMax: ticks[ticks.length - 1], yTicks: ticks, reversed: true, fmt: (v) => '#' + Math.round(v)}));
        }
        fill(node, ...kids);
    }

    function renderPanel(d) {
        const node = body('panel');
        const s = d.scrims || {};
        const p = d.panel || {};
        const bots = Array.isArray(p.bots) ? p.bots : [];
        const used = isNum(s.used) && isNum(s.limit) && s.limit > 0 ? (100 * s.used) / s.limit : null;
        fill(node,
            h('div', {class: 'meters'}, meter(isNum(s.limit) ? 'Scrim games (' + s.limit + ' a day)' : 'Scrim games', used,
                h('span', null, 'resets ', when(s.resets_at, 'at-until')), null,
                isNum(s.used) && isNum(s.limit) ? s.used + '/' + s.limit + ' used' : null)),
            h('h3', null, 'Panel at ', when(p.runs_at, 'at-until')),
            bots.length
                ? h('ol', {class: 'panel-list'}, bots.map((b) => h('li', null, b.bot, b.why ? h('span', {class: 'why'}, ' · ', b.why) : null)))
                : h('p', {class: 'empty', text: 'Nothing queued for the panel yet.'}),
            p.source ? h('p', {class: 'note', text: 'List from ' + p.source + '.'}) : null);
    }

    function renderIssues(d) {
        const node = body('issues');
        const i = d.issues || {};
        const open = Array.isArray(i.open) ? i.open.slice().sort((a, b) => b.number - a.number) : [];
        const ready = Array.isArray(i.ready_for_panel) ? i.ready_for_panel : [];
        const boardUrl = issueUrl(d, d.board_issue);
        fill(node,
            h('h3', null, 'Board ', link(boardUrl, '#' + (d.board_issue || '?')), ': READY FOR PANEL'),
            ready.length
                ? h('ul', {class: 'plain'}, ready.map((r) => h('li', null, h('strong', {text: r.bot}), r.part ? h('span', {class: 'muted'}, ' · ' + r.part) : null)))
                : h('p', {class: 'empty', text: 'No READY FOR PANEL lines on the board.'}),
            details('issues-open', 'Open issues (' + open.length + ')', open.length
                ? h('ul', {class: 'plain'}, open.map((x) => h('li', {class: 'issue'},
                    h('span', {class: 'n'}, link(issueUrl(d, x.number), '#' + x.number)),
                    h('span', {text: x.title || ''}),
                    h('span', {class: 'meta'},
                        (Array.isArray(x.labels) ? x.labels : []).map((name) => h('span', {class: 'label-tag', text: name})),
                        'updated ', when(x.updated_at, 'ago')))))
                : h('p', {class: 'empty', text: 'No open issues.'}), true),
            h('p', {class: 'note'}, 'As of ', when(i.at, 'ago'), '.'));
    }

    const SECTIONS = [['kpis', renderKpis], ['agents', renderAgents], ['claude', renderClaude], ['argon', renderArgon],
        ['ladder', renderLadder], ['panel', renderPanel], ['issues', renderIssues]];

    function render() {
        observers.forEach((observer) => observer.disconnect());
        observers = [];
        for (const [id, fn] of SECTIONS) {
            const section = document.getElementById(id);
            try {
                fn(data);
                section.classList.remove('loading');
            } catch (err) {
                const target = body(id) || section;
                fill(target, h('p', {class: 'empty', text: 'Could not show this part of the feed (' + (err && err.message || err) + ').'}));
            }
        }
        renderFoot();
        renderStatus();
    }

    function renderStatus() {
        statusEl.className = 'status';
        if (!feedUrl) {
            statusEl.textContent = 'No feed configured: set battlecode.feed to the URL of status.json.';
            statusEl.classList.add('error');
            return;
        }
        if (!data) {
            statusEl.textContent = lastError ? 'Could not load the feed: ' + lastError : 'loading…';
            if (lastError) statusEl.classList.add('error');
            return;
        }
        const generated = toMs(data.generated_at);
        const minutes = generated == null ? Infinity : (Date.now() - generated) / 60000;
        statusEl.textContent = (generated == null ? 'updated at an unknown time' : 'updated ' + ago(generated))
            + (lastError ? ' · last refresh failed (' + lastError + ')' : ' · refreshes every 2 min');
        if (minutes > STALE_BAD_MIN) statusEl.classList.add('bad');
        else if (minutes > STALE_WARN_MIN) statusEl.classList.add('warn');
    }

    function renderFoot() {
        const safeFeed = /^https:\/\//.test(feedUrl) ? feedUrl : null;
        fill(footEl,
            'Feed: ', link(safeFeed, 'status.json'), ' (schema ', String(data && data.schema || '?'),
            '), pushed every few minutes by tools/dashboard_push.py on the team machine. Times are in your time zone. Page source: ',
            link('https://github.com/mia-mat/ws', 'mia-mat/ws'), '.');
    }

    // ---------- fetching ----------

    async function refresh() {
        if (!feedUrl) { renderStatus(); return; }
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 20000);
        try {
            // A per-minute query string lets viewers share a cached copy without holding it for long.
            const url = feedUrl + (feedUrl.includes('?') ? '&' : '?') + 't=' + Math.floor(Date.now() / 60000);
            const res = await fetch(url, {cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer', signal: controller.signal});
            if (!res.ok) throw new Error('HTTP ' + res.status);
            const next = await res.json().catch(() => { throw new Error('the feed is not valid JSON'); });
            if (!next || typeof next !== 'object' || !next.generated_at) throw new Error('not a status feed');
            data = next;
            lastError = '';
            render();
        } catch (err) {
            lastError = err && err.name === 'AbortError' ? 'timed out' : String(err && err.message || err);
            renderStatus();
        } finally {
            clearTimeout(timer);
        }
    }

    // ---------- theme ----------

    function setupTheme() {
        const modes = ['auto', 'light', 'dark'];
        let mode = document.documentElement.dataset.theme || 'auto';
        const apply = () => {
            if (mode === 'auto') delete document.documentElement.dataset.theme;
            else document.documentElement.dataset.theme = mode;
            themeBtn.textContent = 'theme: ' + mode;
            try {
                if (mode === 'auto') localStorage.removeItem('bc-theme'); else localStorage.setItem('bc-theme', mode);
            } catch (e) { /* storage blocked: the choice lasts for this visit */ }
        };
        themeBtn.addEventListener('click', () => {
            mode = modes[(modes.indexOf(mode) + 1) % modes.length];
            apply();
        });
        apply();
    }

    // ---------- start ----------

    setupTheme();
    for (const [id] of SECTIONS) document.getElementById(id).classList.add('loading');
    refresh();
    setInterval(refresh, REFRESH_MS);
    setInterval(tick, TICK_MS);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
})();
