// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type approximateNnsStats struct {
	setupStats slime.Value
	trace      protonTrace
}

func (n *approximateNnsStats) makeRows(tab *table) {
	if approximate_nns_searches_performed := n.setupStats.Field("approximate_nns_searches_performed"); approximate_nns_searches_performed.Valid() {
		tab.str("performed searches").str(fmt.Sprintf("%d", approximate_nns_searches_performed.AsLong())).commit()
	}
	if approximate_nns_time_used_ms := n.setupStats.Field("approximate_nns_time_used_ms"); approximate_nns_time_used_ms.Valid() {
		tab.str("used time").str(fmt.Sprintf("%.3f ms", approximate_nns_time_used_ms.AsDouble())).commit()
	}
	if approximate_nns_distances_computed := n.setupStats.Field("approximate_nns_distances_computed"); approximate_nns_distances_computed.Valid() {
		tab.str("computed distances").str(fmt.Sprintf("%d", approximate_nns_distances_computed.AsLong())).commit()
	}
	if approximate_nns_nodes_visited := n.setupStats.Field("approximate_nns_nodes_visited"); approximate_nns_nodes_visited.Valid() {
		tab.str("visited nodes").str(fmt.Sprintf("%d", approximate_nns_nodes_visited.AsLong())).commit()
	}
	if approximate_nns_timeouts_hit := n.setupStats.Field("approximate_nns_timeouts_hit"); approximate_nns_timeouts_hit.Valid() {
		tab.str("hit timeouts").str(fmt.Sprintf("%d", approximate_nns_timeouts_hit.AsLong())).commit()
	}
}

func (s *approximateNnsStats) analyze() {
	s.setupStats = s.trace.findValueByTag("query_setup_stats").Field("stats")
}

func (s *approximateNnsStats) useful() bool {
	return s.setupStats.Valid()
}

func (s *approximateNnsStats) render(out *output) {
	out.fmt("approx. nns stats\n")
	tab := newTable().str("stat").str("value").commit().line()
	s.makeRows(tab)
	tab.render(out)
}

func newApproximateNnsStats(trace protonTrace) *approximateNnsStats {
	res := &approximateNnsStats{setupStats: slime.Invalid, trace: trace}
	res.analyze()
	return res
}

type exactNnsStats struct {
	evalStats slime.Value
	trace     protonTrace
}

func (n *exactNnsStats) makeRows(tab *table) {
	if exact_nns_distances_computed := n.evalStats.Field("exact_nns_distances_computed"); exact_nns_distances_computed.Valid() {
		tab.str("computed distances").str(fmt.Sprintf("%d", exact_nns_distances_computed.AsLong())).commit()
	}
}

func (s *exactNnsStats) analyze() {
	s.evalStats = s.trace.findValueByTag("query_execution_stats").Field("stats")
}

func (s *exactNnsStats) useful() bool {
	return s.evalStats.Valid()
}

func (s *exactNnsStats) render(out *output) {
	out.fmt("exact nns stats\n")
	tab := newTable().str("stat").str("value").commit().line()
	s.makeRows(tab)
	tab.render(out)
}

func newExactNnsStats(trace protonTrace) *exactNnsStats {
	res := &exactNnsStats{evalStats: slime.Invalid, trace: trace}
	res.analyze()
	return res
}
