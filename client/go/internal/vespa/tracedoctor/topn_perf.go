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

func (tp *topNPerf) hasSelfTimeAbove(limit float64) bool {
	for _, entry := range tp.entries {
		if entry.selfTimeMs > limit {
			return true
		}
	}
	return false
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
	tp.renderSelfTimeAbove(out, -1)
}

func (tp *topNPerf) renderSelfTimeAbove(out *output, limit float64) {
	sortedEntries := tp.topN(len(tp.entries))
	tab := newTable().str("count").str("self_ms").str("component").commit().line()
	for _, entry := range sortedEntries {
		if !(entry.selfTimeMs > limit) {
			break
		}
		tab.str(fmt.Sprintf("%d", entry.count)).str(fmt.Sprintf("%.3f", entry.selfTimeMs)).str(entry.name).commit()
	}
	tab.render(out)
}
