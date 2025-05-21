// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"github.com/mattn/go-runewidth"
	"strings"
)

type edgeMask uint8

const (
	edgeTop edgeMask = 1 << iota
	edgeBottom
	edgeLeft
	edgeRight
)

type renderBuffer struct {
	width, height int
	grid          []rune
}

func newRenderBuffer(w, h int) *renderBuffer {
	rb := &renderBuffer{width: w, height: h, grid: make([]rune, w*h)}
	for i := range rb.grid {
		rb.grid[i] = ' '
	}
	return rb
}

func renderCell(cell cell) *renderBuffer {
	buf := newRenderBuffer(cell.needSize())
	cell.renderInto(buf, 0, 0, buf.width, buf.height)
	return buf
}

var edgesToRune = map[edgeMask]rune{
	0:                                 ' ',
	edgeTop:                           '╵',
	edgeBottom:                        '╷',
	edgeLeft:                          '╴',
	edgeRight:                         '╶',
	edgeTop | edgeBottom:              '│',
	edgeLeft | edgeRight:              '─',
	edgeBottom | edgeRight:            '┌',
	edgeBottom | edgeLeft:             '┐',
	edgeTop | edgeRight:               '└',
	edgeTop | edgeLeft:                '┘',
	edgeTop | edgeBottom | edgeRight:  '├',
	edgeTop | edgeBottom | edgeLeft:   '┤',
	edgeBottom | edgeLeft | edgeRight: '┬',
	edgeTop | edgeLeft | edgeRight:    '┴',
	edgeTop | edgeBottom | edgeLeft | edgeRight: '┼',
}

var runeToEdges = map[rune]edgeMask{
	' ': 0,
	'╵': edgeTop,
	'╷': edgeBottom,
	'╴': edgeLeft,
	'╶': edgeRight,
	'│': edgeTop | edgeBottom,
	'─': edgeLeft | edgeRight,
	'┌': edgeBottom | edgeRight,
	'┐': edgeBottom | edgeLeft,
	'└': edgeTop | edgeRight,
	'┘': edgeTop | edgeLeft,
	'├': edgeTop | edgeBottom | edgeRight,
	'┤': edgeTop | edgeBottom | edgeLeft,
	'┬': edgeBottom | edgeLeft | edgeRight,
	'┴': edgeTop | edgeLeft | edgeRight,
	'┼': edgeTop | edgeBottom | edgeLeft | edgeRight,
}

func (rb *renderBuffer) addEdges(x, y int, edges edgeMask) {
	rb.addEdgesIf(x, y, edges, 0)
}

func (rb *renderBuffer) addEdgesIf(x, y int, edges, require edgeMask) {
	if x < 0 || y < 0 || x >= rb.width || y >= rb.height {
		return
	}
	idx := y*rb.width + x
	existing := runeToEdges[rb.grid[idx]]
	if existing&require != require {
		return
	}
	rb.grid[idx] = edgesToRune[existing|edges]
}

func (rb *renderBuffer) addRune(x, y int, r rune) {
	if x < 0 || y < 0 || x >= rb.width || y >= rb.height {
		return
	}
	idx := y*rb.width + x
	rb.grid[idx] = r
}

func (rb *renderBuffer) writeText(x, y int, s string) {
	offset := 0
	for _, r := range s {
		size := runewidth.RuneWidth(r)
		if size > 0 {
			rb.addRune(x+offset, y, r)
			offset++
			for ; size > 1; size-- {
				rb.addRune(x+offset, y, 0)
				offset++
			}
		}
	}
}

func (rb *renderBuffer) drawBox(x1, y1, x2, y2 int) {
	for i := 1; i < x2; i++ {
		rb.addEdges(x1+i, y1, edgeLeft|edgeRight)
		rb.addEdges(x1+i, y2, edgeLeft|edgeRight)
	}
	for i := 1; i < y2; i++ {
		rb.addEdges(x1, y1+i, edgeTop|edgeBottom)
		rb.addEdges(x2, y1+i, edgeTop|edgeBottom)
	}
	rb.addEdges(x1, y1, edgeBottom|edgeRight)
	rb.addEdges(x2, y1, edgeBottom|edgeLeft)
	rb.addEdges(x1, y2, edgeTop|edgeRight)
	rb.addEdges(x2, y2, edgeTop|edgeLeft)
}

func (rb *renderBuffer) vline(x, y1, y2 int) {
	for y := y1; y <= y2; y++ {
		rb.addEdges(x, y, edgeTop|edgeBottom)
	}
	rb.addEdgesIf(x, y1-1, edgeBottom, edgeLeft|edgeRight)
	rb.addEdgesIf(x, y2+1, edgeTop, edgeLeft|edgeRight)
}

func (rb *renderBuffer) hline(y, x1, x2 int) {
	for x := x1; x <= x2; x++ {
		rb.addEdges(x, y, edgeLeft|edgeRight)
	}
	rb.addEdgesIf(x1-1, y, edgeRight, edgeTop|edgeBottom)
	rb.addEdgesIf(x2+1, y, edgeLeft, edgeTop|edgeBottom)
}

func (rb *renderBuffer) render(out *output) {
	for y := 0; y < rb.height; y++ {
		var b strings.Builder
		pos := y * rb.width
		end := pos + rb.width
		for ; pos < end; pos++ {
			r := rb.grid[pos]
			if r != 0 {
				b.WriteRune(r)
			}
		}
		b.WriteByte('\n')
		out.fmt("%s", b.String())
	}
}

type cell interface {
	needSize() (w, h int)
	renderInto(rb *renderBuffer, x, y, w, h int)
}

type cellFrame struct {
	cell cell
}

func (f *cellFrame) needSize() (int, int) {
	w, h := f.cell.needSize()
	return w + 2, h + 2
}

func (f *cellFrame) renderInto(rb *renderBuffer, x, y, w, h int) {
	rb.drawBox(x, y, x+w-1, y+h-1)
	f.cell.renderInto(rb, x+1, y+1, w-2, h-2)
}

type textCell struct {
	text       string
	alignRight bool
}

func (c *textCell) needSize() (int, int) {
	return runewidth.StringWidth(c.text) + 2, 1
}

func (c *textCell) renderInto(rb *renderBuffer, x, y, w, h int) {
	needWidth, _ := c.needSize()
	extra := w - needWidth
	if extra > 0 && c.alignRight {
		x += extra
	}
	rb.writeText(x+1, y, c.text)
}

type table struct {
	cells      [][]cell
	row        []cell
	colWidths  []int
	needWidth  int
	rowHeights []int
	needHeight int
	lines      map[int]bool
}

func newTable() *table {
	return &table{lines: make(map[int]bool)}
}

func makeTable(fill func(t *table)) *table {
	t := newTable()
	fill(t)
	return t
}

func (t *table) str(s string) *table {
	right := len(s) > 0 && s[0] >= '0' && s[0] <= '9'
	t.row = append(t.row, &textCell{text: s, alignRight: right})
	return t
}

func (t *table) tab(inner *table) *table {
	t.row = append(t.row, inner)
	t.lines[len(t.cells)] = true
	t.lines[len(t.cells)+1] = true
	return t
}

func (t *table) commit() *table {
	t.cells = append(t.cells, t.row)
	t.row = nil
	return t
}

func (t *table) line() *table {
	t.lines[len(t.cells)] = true
	return t
}

func (t *table) needSize() (int, int) {
	numCols := 0
	for _, row := range t.cells {
		if len(row) > numCols {
			numCols = len(row)
		}
	}
	if numCols == 0 {
		return 0, 0
	}
	t.colWidths = make([]int, numCols)
	for i := range t.colWidths {
		t.colWidths[i] = 1
	}
	t.rowHeights = make([]int, len(t.cells))
	for i := range t.rowHeights {
		t.rowHeights[i] = 1
	}
	for y, row := range t.cells {
		for x, c := range row {
			cw, ch := c.needSize()
			if cw > t.colWidths[x] {
				t.colWidths[x] = cw
			}
			if ch > t.rowHeights[y] {
				t.rowHeights[y] = ch
			}
		}
	}
	t.needWidth = len(t.colWidths) - 1
	for _, cw := range t.colWidths {
		t.needWidth += cw
	}
	t.needHeight = 0
	for y, rh := range t.rowHeights {
		if y > 0 && t.lines[y] {
			t.needHeight += 1
		}
		t.needHeight += rh
	}
	return t.needWidth, t.needHeight
}

func (t *table) renderInto(rb *renderBuffer, x, y, w, h int) {
	colCnt := len(t.colWidths)
	rowCnt := len(t.rowHeights)
	if colCnt == 0 || rowCnt == 0 {
		return
	}
	x1, y1, x2, y2 := x, y, x+w-1, y+h-1
	perCol, lastCol := 0, 0
	perRow, lastRow := 0, 0
	if w > t.needWidth {
		perCol = (w - t.needWidth) / colCnt
		lastCol = (w - t.needWidth) % colCnt
	}
	if h > t.needHeight {
		perRow = (h - t.needHeight) / rowCnt
		lastRow = (h - t.needHeight) % rowCnt
	}
	var later []func()
	renderLater := func(c cell, x, y, w, h int) {
		later = append(later, func() {
			c.renderInto(rb, x, y, w, h)
		})
	}
	for j, row := range t.cells {
		ch := t.rowHeights[j] + perRow
		if j < lastRow {
			ch++
		}
		if j > 0 && t.lines[j] {
			rb.hline(y, x1, x2)
			y++
		}
		for i, cw := range t.colWidths {
			cw += perCol
			if i < lastCol {
				cw++
			}
			if i > 0 {
				rb.vline(x, y1, y2)
				x++
			}
			if len(row) > i {
				renderLater(row[i], x, y, cw, ch)
			}
			x += cw
		}
		x = x1
		y += ch
	}
	for _, f := range later {
		f()
	}
}

func (t *table) render(out *output) {
	buf := renderCell(&cellFrame{cell: t})
	buf.render(out)
}
