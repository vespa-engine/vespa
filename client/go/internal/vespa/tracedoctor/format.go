package tracedoctor

import (
	"strings"
	"unicode/utf8"
)

func word(n int, singular, plural string) string {
	if n == 1 {
		return singular
	}
	return plural
}

func suffix(n int, s string) string {
	return word(n, "", s)
}

type table struct {
	headers []string
	rows    [][]string
}

func newTable(headers ...string) *table {
	return &table{headers: headers}
}

func (t *table) hasHeaders() bool {
	for _, h := range t.headers {
		if h != "" {
			return true
		}
	}
	return false
}

func (t *table) addRow(row ...string) {
	t.rows = append(t.rows, row)
}

func (t *table) render(out *output) {
	columnWidths := make([]int, len(t.headers))
	for i, header := range t.headers {
		columnWidths[i] = utf8.RuneCountInString(header)
	}
	for _, row := range t.rows {
		for i, cell := range row {
			if i < len(columnWidths) && utf8.RuneCountInString(cell) > columnWidths[i] {
				columnWidths[i] = utf8.RuneCountInString(cell)
			}
		}
	}
	renderLine := func(out *output, left, mid, right, horizontal string) {
		out.fmt("%s", left)
		for i, width := range columnWidths {
			out.fmt("%s", strings.Repeat(horizontal, width+2))
			if i < len(columnWidths)-1 {
				out.fmt("%s", mid)
			}
		}
		out.fmt("%s\n", right)
	}
	renderRow := func(out *output, cells []string) {
		out.fmt("│")
		for i, cell := range cells {
			if i < len(columnWidths) {
				// right-align numbers and left-align text
				if len(cell) > 0 && cell[0] >= '0' && cell[0] <= '9' {
					out.fmt(" %*s │", columnWidths[i], cell)
				} else {
					out.fmt(" %-*s │", columnWidths[i], cell)
				}
			}
		}
		out.fmt("\n")
	}
	renderLine(out, "┌", "┬", "┐", "─")
	if t.hasHeaders() {
		renderRow(out, t.headers)
		renderLine(out, "├", "┼", "┤", "─")
	}
	for _, row := range t.rows {
		renderRow(out, row)
	}
	renderLine(out, "└", "┴", "┘", "─")
}
