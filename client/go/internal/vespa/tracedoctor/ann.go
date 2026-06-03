// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type annNode struct {
	root slime.Value
}

func (n annNode) makeRows(tab *table, showDetails bool) {
	// Inputs/parameters to the ANN search
	tab.str("attribute tensor").str(n.root.Field("attribute_tensor").AsString()).commit()
	tab.str("query tensor").str(n.root.Field("query_tensor").AsString()).commit()
	if distance_threshold := n.root.Field("distance_threshold"); distance_threshold.Valid() && showDetails {
		tab.str("distance threshold").str(fmt.Sprintf("%e", distance_threshold.AsDouble())).commit()
	}
	tab.str("target hits").str(fmt.Sprintf("%d", n.root.Field("target_hits").AsLong())).commit()
	if n.root.Field("adjusted_target_hits").AsLong() > n.root.Field("target_hits").AsLong() {
		tab.str("adjusted target hits").str(fmt.Sprintf("%d", n.root.Field("adjusted_target_hits").AsLong())).commit()
	}
	if target_hits_max_adjustment_factor := n.root.Field("target_hits_max_adjustment_factor"); target_hits_max_adjustment_factor.Valid() && showDetails {
		tab.str("max. adjustment factor").str(fmt.Sprintf("%.3f", target_hits_max_adjustment_factor.AsDouble())).commit()
	}
	tab.str("explore additional hits").str(fmt.Sprintf("%d", n.root.Field("explore_additional_hits").AsLong())).commit()
	if exploration_slack := n.root.Field("exploration_slack"); exploration_slack.Valid() && showDetails {
		tab.str("exploration slack").str(fmt.Sprintf("%.3f", exploration_slack.AsDouble())).commit()
	}
	if filter_first_exploration := n.root.Field("filter_first_exploration"); filter_first_exploration.Valid() && showDetails {
		tab.str("filter-first exploration").str(fmt.Sprintf("%.3f", filter_first_exploration.AsDouble())).commit()
	}
	if prefetch_tensors := n.root.Field("prefetch_tensors"); prefetch_tensors.Valid() && showDetails {
		if prefetch_tensors.AsBool() {
			tab.str("tensor prefetching").str("enabled").commit()
		} else {
			tab.str("tensor prefetching").str("disabled").commit()
		}
	}
	if global_filter_lower_limit := n.root.Field("global_filter").Field("lower_limit"); global_filter_lower_limit.Valid() && showDetails {
		tab.str("global filter lower limit").str(fmt.Sprintf("%.3f", global_filter_lower_limit.AsDouble())).commit()
	}
	if filter_first_upper_limit := n.root.Field("filter_first_upper_limit"); filter_first_upper_limit.Valid() && showDetails {
		tab.str("filter-first upper limit").str(fmt.Sprintf("%.3f", filter_first_upper_limit.AsDouble())).commit()
	}
	if global_filter_upper_limit := n.root.Field("global_filter").Field("upper_limit"); global_filter_upper_limit.Valid() && showDetails {
		tab.str("global filter upper limit").str(fmt.Sprintf("%.3f", global_filter_upper_limit.AsDouble())).commit()
	}

	// Results/reactions by Vespa to the inputs
	tab.str("algorithm").str(n.root.Field("algorithm").AsString()).commit()
	if filter_first_heuristic := n.root.Field("filter_first_heuristic_used"); filter_first_heuristic.Valid() {
		if filter_first_heuristic.AsBool() {
			tab.str("filter-first heuristic").str("used").commit()
		} else {
			tab.str("filter-first heuristic").str("not used").commit()
		}
	}
	if calculated := n.root.Field("global_filter").Field("calculated"); calculated.Valid() && !calculated.AsBool() {
		tab.str("global filter").str("not calculated").commit()
	} else if hit_ratio := n.root.Field("global_filter").Field("hit_ratio"); hit_ratio.Valid() {
		tab.str("global filter").str(fmt.Sprintf("%.3f hit ratio", hit_ratio.AsDouble())).commit()
	}
	if constructed := n.root.Field("lazy_filter").Field("constructed"); constructed.Valid() && !constructed.AsBool() && showDetails {
		tab.str("lazy filter").str("not constructed").commit()
	} else if hit_ratio := n.root.Field("lazy_filter").Field("hit_ratio"); hit_ratio.Valid() {
		tab.str("lazy filter").str(fmt.Sprintf("%.3f hit ratio", hit_ratio.AsDouble())).commit()
	}
	if nodes_visited := n.root.Field("nodes_visited"); nodes_visited.Valid() && showDetails {
		tab.str("visited nodes").str(fmt.Sprintf("%d", nodes_visited.AsLong())).commit()
	}
	if distances_computed := n.root.Field("distances_computed"); distances_computed.Valid() && showDetails {
		tab.str("computed distances").str(fmt.Sprintf("%d", distances_computed.AsLong())).commit()
	}
	if time_allocated := n.root.Field("time_allocated"); time_allocated.Valid() && showDetails {
		tab.str("allocated time").str(fmt.Sprintf("%.3f ms", time_allocated.AsDouble())).commit()
	}
	if time_used := n.root.Field("time_used"); time_used.Valid() {
		tab.str("used time").str(fmt.Sprintf("%.3f ms", time_used.AsDouble())).commit()
	}
	if terminated_early := n.root.Field("terminated_early"); terminated_early.Valid() && showDetails {
		if terminated_early.AsBool() {
			tab.str("terminated early").str("yes").commit()
		} else {
			tab.str("terminated early").str("no").commit()
		}
	}
	if timeout_hit := n.root.Field("timeout_hit"); timeout_hit.Valid() && showDetails {
		if timeout_hit.AsBool() {
			tab.str("hit timeout").str("yes").commit()
		} else {
			tab.str("hit timeout").str("no").commit()
		}
	}
	if top_k_hits := n.root.Field("top_k_hits"); top_k_hits.Valid() {
		tab.str("found hits").str(fmt.Sprintf("%d", top_k_hits.AsLong())).commit()
	}
}

func (p protonTrace) findValue(tag string) slime.Value {
	var value = slime.Invalid
	slime.Select(p.source, hasTag(tag), func(p *slime.Path, v slime.Value) {
		value = v
	})
	return value
}

type globalFilterDecision struct {
	root slime.Value
}

func (d *globalFilterDecision) makeRows(tab *table) {
	if estimated_hit_ratio := d.root.Field("estimated_hit_ratio"); estimated_hit_ratio.Valid() {
		tab.str("estimated hit ratio").str(fmt.Sprintf("%.3f", estimated_hit_ratio.AsDouble())).commit()
	}
	if lower_limit := d.root.Field("lower_limit"); lower_limit.Valid() {
		tab.str("lower limit").str(fmt.Sprintf("%.3f", lower_limit.AsDouble())).commit()
	}
	if upper_limit := d.root.Field("upper_limit"); upper_limit.Valid() {
		tab.str("upper limit").str(fmt.Sprintf("%.3f", upper_limit.AsDouble())).commit()
	}
}

func (d *globalFilterDecision) analyze(trace *protonTrace) {
	d.root = trace.findValue("global_filter_decision").Field("parameters")
}

func (d *globalFilterDecision) useful() bool {
	return d.root.Valid()
}

func (d *globalFilterDecision) render(out *output) {
	if d.useful() {
		out.fmt("global filter decision\n")
		tab := newTable()
		d.makeRows(tab)
		tab.render(out)
	}
}

type nnsStats struct {
	setupStats slime.Value
	evalStats  slime.Value
}

func (n *nnsStats) makeApproximateRows(tab *table) {
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

func (n *nnsStats) makeExactRows(tab *table) {
	if exact_nns_distances_computed := n.evalStats.Field("exact_nns_distances_computed"); exact_nns_distances_computed.Valid() {
		tab.str("computed distances").str(fmt.Sprintf("%d", exact_nns_distances_computed.AsLong())).commit()
	}
}

func (s *nnsStats) analyze(trace *protonTrace) {
	s.setupStats = trace.findValue("query_setup_stats").Field("stats")
	s.evalStats = trace.findValue("query_execution_stats").Field("stats")
}

func (s *nnsStats) approximateStatsUseful() bool {
	return s.setupStats.Valid()
}

func (s *nnsStats) exactStatsUseful() bool {
	return s.evalStats.Valid()
}

func (s *nnsStats) useful() bool {
	return s.approximateStatsUseful() || s.exactStatsUseful()
}

func (s *nnsStats) render(out *output) {
	if s.useful() {
		out.fmt("nns stats\n")
	}
	if s.approximateStatsUseful() {
		tab := newTable().str("approx. nns stat").str("value").commit().line()
		s.makeApproximateRows(tab)
		tab.render(out)
	}
	if s.exactStatsUseful() {
		tab := newTable().str("exact nns stat").str("value").commit().line()
		s.makeExactRows(tab)
		tab.render(out)
	}
}

func (p protonTrace) findAnnNodes() []annNode {
	var res []annNode
	slime.Select(p.source, hasTag("query_execution_plan"), func(p *slime.Path, v slime.Value) {
		slime.Select(v.Field("optimized"), hasType("search::queryeval::NearestNeighborBlueprint"), func(p *slime.Path, v slime.Value) {
			res = append(res, annNode{v})
		})
	})
	return res
}

type annProbe struct {
	annTime        float64
	nodes          []annNode
	filterDecision globalFilterDecision
	stats          nnsStats
	trace          protonTrace
}

func (p *annProbe) analyze() {
	p.annTime = p.trace.timeline().durationBetween(
		"Handle global filter in query execution plan",
		"Optimize query execution plan to account for global filter")
	p.nodes = p.trace.findAnnNodes()
	p.filterDecision.analyze(&p.trace)
	p.stats.analyze(&p.trace)
}

func (p *annProbe) useful() bool {
	return len(p.nodes) > 0
}

func (p *annProbe) impact() float64 {
	if p.useful() {
		return p.annTime
	}
	return 0
}

func (p *annProbe) render(out *output, showDetails bool) {
	out.fmt("ann query details (total setup time was %.3f ms)\n", p.annTime)
	tab := newTable().str("property").str("details").commit().line()
	for i, node := range p.nodes {
		if i > 0 {
			tab.line()
		}
		node.makeRows(tab, showDetails)
	}
	tab.render(out)

	if showDetails {
		p.stats.render(out)
		p.filterDecision.render(out)
	}
}

func newAnnProbe(trace protonTrace) *annProbe {
	res := &annProbe{trace: trace}
	res.analyze()
	return res
}
