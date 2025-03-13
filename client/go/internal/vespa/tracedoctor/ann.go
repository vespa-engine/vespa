package tracedoctor

import "github.com/vespa-engine/vespa/client/go/internal/vespa/slime"

type annNode struct {
	root slime.Value
}

func (n annNode) render(out *output) {
	out.fmt("ANN query node:\n")
	out.fmt("    attribute_tensor: %s\n", n.root.Field("attribute_tensor").AsString())
	out.fmt("    query_tensor: %s\n", n.root.Field("query_tensor").AsString())
	out.fmt("    target_hits: %d\n", n.root.Field("target_hits").AsLong())
	if n.root.Field("adjusted_target_hits").AsLong() > n.root.Field("target_hits").AsLong() {
		out.fmt("    adjusted_target_hits: %d\n", n.root.Field("adjusted_target_hits").AsLong())
	}
	out.fmt("    explore_additional_hits: %d\n", n.root.Field("explore_additional_hits").AsLong())
	out.fmt("    algorithm: %s\n", n.root.Field("algorithm").AsString())
	if calculated := n.root.Field("global_filter").Field("calculated"); calculated.Valid() && !calculated.AsBool() {
		out.fmt("    global_filter: not calculated\n")
	} else if hit_ratio := n.root.Field("global_filter").Field("hit_ratio"); hit_ratio.Valid() {
		out.fmt("    global_filter: %.3f hit ratio\n", hit_ratio.AsDouble())
	}
	if top_k_hits := n.root.Field("top_k_hits"); top_k_hits.Valid() {
		out.fmt("    found hits: %d\n", top_k_hits.AsLong())
	}
}

func (p protonTrace) findAnnNodes() []annNode {
	var res []annNode
	slime.Select(p.root, hasTag("query_execution_plan"), func(p *slime.Path, v slime.Value) {
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
	out.fmt("\nANN during query setup: %.3f ms\n", p.annTime)
	for _, node := range p.nodes {
		node.render(out)
	}
}

func newAnnProbe(trace protonTrace) *annProbe {
	res := &annProbe{trace: trace}
	res.analyze()
	return res
}
