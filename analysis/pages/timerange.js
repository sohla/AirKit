// Two-ended date range control shared by the timeline pages.
//
//   const range = TimeRange(element, {
//       dates:   [Date, ...],             // one per counted thing - drawn as a histogram
//       summary: ([d0, d1]) => '42 commits · 7 p-files',
//       onChange: ([d0, d1]) => { ... },  // fires when a drag ends
//   });
//   range.extent()   -> [d0, d1]          // current selection, full span by default
//   range.set([d0, d1])
//
// Needs d3 v7 on the page. Drag either edge to move min or max, drag the
// middle to slide the window, click the histogram outside the selection or
// press "all" to reset.

function TimeRange(element, options) {
    const dates = options.dates.filter(d => d instanceof Date && !isNaN(d)).sort(d3.ascending);
    const summary = options.summary || (([d0, d1]) => `${dates.filter(d => d >= d0 && d <= d1).length} in range`);
    const onChange = options.onChange || (() => {});
    const HEIGHT = 60, AXIS = 18, PAD = 8;
    const fmt = d3.timeFormat('%Y-%m-%d');

    const full = [d3.timeDay.floor(dates[0] || new Date()), d3.timeDay.offset(d3.timeDay.floor(dates[dates.length - 1] || new Date()), 1)];
    let selection = full.slice();

    const root = d3.select(element).classed('timerange', true).html('');
    const head = root.append('div').attr('class', 'tr-head');
    const label = head.append('span').attr('class', 'tr-dates');
    const count = head.append('span').attr('class', 'tr-count');
    head.append('button').attr('class', 'tr-reset').text('all').on('click', () => set(full, true));
    const svg = root.append('svg').attr('height', HEIGHT);

    const x = d3.scaleTime().domain(full);
    const brush = d3.brushX().on('brush', brushing).on('end', brushed);
    let bars, brushG;

    function draw() {
        const width = Math.max(200, element.clientWidth);
        svg.attr('width', width).selectAll('*').remove();
        x.range([PAD, width - PAD]);

        const spanDays = (full[1] - full[0]) / 864e5;
        const interval = spanDays > 540 ? d3.timeMonth : spanDays > 90 ? d3.timeWeek : d3.timeDay;
        const bins = d3.bin().domain(full).thresholds(interval.range(...full)).value(d => d)(dates);
        const y = d3.scaleLinear([0, d3.max(bins, b => b.length) || 1], [0, HEIGHT - AXIS - 4]);

        bars = svg.append('g').selectAll('rect').data(bins).join('rect')
            .attr('x', b => x(b.x0) + 0.5)
            .attr('width', b => Math.max(1, x(b.x1) - x(b.x0) - 1))
            .attr('y', b => HEIGHT - AXIS - y(b.length))
            .attr('height', b => y(b.length))
            .attr('rx', 1);

        svg.append('g').attr('class', 'tr-axis').attr('transform', `translate(0,${HEIGHT - AXIS})`)
            .call(d3.axisBottom(x).ticks(Math.floor(width / 90)).tickSizeOuter(0));

        brush.extent([[PAD, 0], [width - PAD, HEIGHT - AXIS]]);
        brushG = svg.append('g').attr('class', 'tr-brush').call(brush);
        brushG.call(brush.move, selection.map(x));
        paint();
    }

    function paint() {
        label.text(`${fmt(selection[0])}  →  ${fmt(d3.timeDay.offset(selection[1], -1))}`);
        count.text(summary(selection));
        bars.attr('class', b => b.x1 > selection[0] && b.x0 < selection[1] ? 'in' : 'out');
    }

    function fromPixels(px) {
        const d0 = d3.timeDay.round(x.invert(px[0]));
        let d1 = d3.timeDay.round(x.invert(px[1]));
        if (d1 <= d0) d1 = d3.timeDay.offset(d0, 1);
        return [d0, d1];
    }

    function brushing(event) {
        if (!event.sourceEvent || !event.selection) return;
        selection = fromPixels(event.selection);
        paint();
    }

    function brushed(event) {
        if (!event.sourceEvent) return;
        selection = event.selection ? fromPixels(event.selection) : full.slice();
        if (!event.selection) brushG.call(brush.move, selection.map(x));
        paint();
        onChange(selection.slice());
    }

    function set(extent, notify) {
        selection = [d3.max([extent[0], full[0]]), d3.min([extent[1], full[1]])];
        brushG.call(brush.move, selection.map(x));
        paint();
        if (notify) onChange(selection.slice());
    }

    if (!document.getElementById('timerange-style')) {
        d3.select('head').append('style').attr('id', 'timerange-style').text(`
            .timerange { width: 100%; font-family: var(--font, sans-serif); }
            .timerange .tr-head { display: flex; gap: 14px; align-items: baseline; font-size: 13px; margin-bottom: 2px; }
            .timerange .tr-dates { color: var(--ink, #1d1d1b); font-family: var(--mono, monospace); }
            .timerange .tr-count { color: var(--c-1, #d14a2b); }
            .timerange .tr-reset { margin-left: auto; padding: 0 10px; font-size: 11px; }
            .timerange rect.in { fill: var(--ink, #1d1d1b); }
            .timerange rect.out { fill: var(--ink-3, #8a847a); opacity: .35; }
            .timerange .tr-axis path, .timerange .tr-axis line { stroke: var(--ink, #1d1d1b); stroke-opacity: .5; }
            .timerange .tr-axis text { fill: var(--ink-3, #8a847a); font-size: 10px; font-family: var(--mono, monospace); }
            .timerange .tr-brush .selection { fill: var(--c-1, #d14a2b); fill-opacity: .08; stroke: var(--c-1, #d14a2b); stroke-opacity: .8; }
            .timerange .tr-brush .handle { fill: var(--c-1, #d14a2b); }
        `);
    }

    draw();
    window.addEventListener('resize', () => draw());

    return { extent: () => selection.slice(), set: extent => set(extent, false) };
}
