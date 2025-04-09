// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
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
	tab := newTable("count", "self_ms", "component")
	for _, entry := range sortedEntries {
		tab.addRow(fmt.Sprintf("%d", entry.count), fmt.Sprintf("%.3f", entry.selfTimeMs), entry.name)
	}
	tab.render(out)
}
