// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type annNode struct {
	root slime.Value
}

func (n annNode) makeRows(tab *table) {
	tab.str("attribute tensor").str(n.root.Field("attribute_tensor").AsString()).commit()
	tab.str("query tensor").str(n.root.Field("query_tensor").AsString()).commit()
	tab.str("target hits").str(fmt.Sprintf("%d", n.root.Field("target_hits").AsLong())).commit()
	if n.root.Field("adjusted_target_hits").AsLong() > n.root.Field("target_hits").AsLong() {
		tab.str("adjusted target hits").str(fmt.Sprintf("%d", n.root.Field("adjusted_target_hits").AsLong())).commit()
	}
	tab.str("explore additional hits").str(fmt.Sprintf("%d", n.root.Field("explore_additional_hits").AsLong())).commit()
	tab.str("algorithm").str(n.root.Field("algorithm").AsString()).commit()
	if calculated := n.root.Field("global_filter").Field("calculated"); calculated.Valid() && !calculated.AsBool() {
		tab.str("global filter").str("not calculated").commit()
	} else if hit_ratio := n.root.Field("global_filter").Field("hit_ratio"); hit_ratio.Valid() {
		tab.str("global filter").str(fmt.Sprintf("%.3f hit ratio", hit_ratio.AsDouble())).commit()
	}
	if top_k_hits := n.root.Field("top_k_hits"); top_k_hits.Valid() {
		tab.str("found hits").str(fmt.Sprintf("%d", top_k_hits.AsLong())).commit()
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
	annTime float64
	nodes   []annNode
	trace   protonTrace
}

func (p *annProbe) analyze() {
	p.annTime = p.trace.timeline().durationOf("Handle global filter in query execution plan")
	p.nodes = p.trace.findAnnNodes()
}

func (p *annProbe) impact() float64 {
	if len(p.nodes) > 0 {
		return p.annTime
	}
	return 0.0
}

func (p *annProbe) render(out *output) {
	out.fmt("ann query details (total setup time was %.3f ms)\n", p.annTime)
	tab := newTable().str("property").str("details").commit().line()
	for i, node := range p.nodes {
		if i > 0 {
			tab.line()
		}
		node.makeRows(tab)
	}
	tab.render(out)
}

func newAnnProbe(trace protonTrace) *annProbe {
	res := &annProbe{trace: trace}
	res.analyze()
	return res
}
