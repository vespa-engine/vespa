package tracedoctor

import "github.com/mattn/go-runewidth"

func splitText(text string, w int) []string {
	type split struct {
		bytePos int
		pos     int
		skip    int
		score   int64
		parent  *split
		prev    *split
	}
	findSkip := func(i int) int {
		skip := 0
		for ; i < len(text) && rune(text[i]) == ' '; i++ {
			skip++
		}
		return skip
	}
	dist := func(parent *split, pos int) int {
		return pos - (parent.pos + parent.skip)
	}
	calcScore := func(parent *split, pos int, last bool) int64 {
		if last {
			return parent.score
		}
		err := int64(w - dist(parent, pos))
		return parent.score + err*err
	}
	list := &split{bytePos: 0, pos: 0, skip: 0, score: 0, parent: nil, prev: nil}
	addSplit := func(bytePos, pos int) int {
		skip := findSkip(bytePos)
		last := bytePos+skip == len(text)
		s := &split{bytePos: bytePos, pos: pos, skip: skip, score: calcScore(list, pos, last), parent: list, prev: list}
		for j := list.prev; j != nil && dist(j, pos) <= w; j = j.prev {
			if score := calcScore(j, pos, last); score < s.score {
				s.score = score
				s.parent = j
			}
		}
		list = s
		return skip
	}
	pos := 0
	skip := 0
	splitAt := -1
	for bytePos, r := range text {
		runeSize := runewidth.RuneWidth(r)
		if skip > 0 {
			skip--
			pos += runeSize
			continue
		}
		if r == ' ' {
			splitAt = pos
		}
		if dist(list, pos) >= w {
			splitAt = pos
		}
		if splitAt == pos {
			skip = addSplit(bytePos, pos)
		}
		if r == ',' {
			splitAt = pos + 1
		}
		pos += runeSize
	}
	if list.bytePos+list.skip < len(text) || list.prev == nil {
		addSplit(len(text), pos)
	}
	var splits []*split
	for s := list; s != nil; s = s.parent {
		splits = append(splits, s)
	}
	var lines []string
	for i := len(splits) - 2; i >= 0; i-- {
		start := splits[i+1].bytePos + splits[i+1].skip
		end := splits[i].bytePos
		lines = append(lines, text[start:end])
	}
	return lines
}

type multiLineTextCell struct {
	lines []string
}

func makeMultiLineTextCell(text string, width int) *multiLineTextCell {
	lines := splitText(text, width)
	return &multiLineTextCell{lines: lines}
}

func (c *multiLineTextCell) needSize() (int, int) {
	width := 0
	height := len(c.lines)
	for _, line := range c.lines {
		width = max(width, runewidth.StringWidth(line))
	}
	if width == 0 || height == 0 {
		return 0, 0
	}
	return width + 2, height
}

func (c *multiLineTextCell) renderInto(rb *renderBuffer, x, y, w, h int) {
	for i, line := range c.lines {
		rb.writeText(x+1, y+i, line)
	}
}
