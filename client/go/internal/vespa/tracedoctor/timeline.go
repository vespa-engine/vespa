package tracedoctor

import "strings"

type timeline struct {
	list []timelineEntry
}

type timelineEntry struct {
	when float64
	what string
}

func (t *timeline) add(when float64, what string) {
	t.list = append(t.list, timelineEntry{when, what})
}

func (t *timeline) addComment(what string) {
	t.add(-1.0, what)
}

func (t *timeline) render(out *output) {
	for _, entry := range t.list {
		if entry.when < 0.0 {
			out.fmt("%s%s\n", strings.Repeat(" ", 15), entry.what)
		} else {
			out.fmt("%10.3f ms: %s\n", entry.when, entry.what)
		}
	}
}
