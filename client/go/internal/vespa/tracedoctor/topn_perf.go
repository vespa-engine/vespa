package tracedoctor

import (
	"sort"
)

type topNPerfEntry struct {
	name       string
	count      int64
	selfTimeMs float64
}

type topNPerf struct {
	totalTimeMs float64
	entries     map[string]*topNPerfEntry
}

func (tp *topNPerf) impact() float64 {
	return tp.totalTimeMs
}

func newTopNPerf() *topNPerf {
	return &topNPerf{
		entries: make(map[string]*topNPerfEntry),
	}
}

func (tp *topNPerf) addSample(name string, count int64, selfTimeMs float64) {
	tp.totalTimeMs += selfTimeMs
	if entry, exists := tp.entries[name]; exists {
		entry.count += count
		entry.selfTimeMs += selfTimeMs
	} else {
		tp.entries[name] = &topNPerfEntry{name: name, count: count, selfTimeMs: selfTimeMs}
	}
}

func (tp *topNPerf) topN(n int) []topNPerfEntry {
	entries := make([]topNPerfEntry, 0, len(tp.entries))
	for _, entry := range tp.entries {
		entries = append(entries, *entry)
	}

	sort.Slice(entries, func(i, j int) bool {
		return entries[i].selfTimeMs > entries[j].selfTimeMs
	})

	if n > len(entries) {
		n = len(entries)
	}
	return entries[:n]
}

func (tp *topNPerf) render(out *output) {
	sortedEntries := tp.topN(len(tp.entries))
	out.fmt("+-----------+-----------+\n")
	out.fmt("|     count |   self_ms |\n")
	out.fmt("+-----------+-----------+\n")
	for _, entry := range sortedEntries {
		out.fmt("|%10d |%10.3f | %s\n", entry.count, entry.selfTimeMs, entry.name)
	}
	out.fmt("+-----------+-----------+\n")
}
