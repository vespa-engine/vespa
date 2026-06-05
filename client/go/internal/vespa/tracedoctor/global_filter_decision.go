// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type globalFilterDecision struct {
	root  slime.Value
	trace protonTrace
}

func (d *globalFilterDecision) makeRows(tab *table) {
	if decision := d.root.Field("decision"); decision.Valid() {
		tab.str("decision").str(decision.AsString()).commit().line()
	}
	// Parameters that led to the decision
	var parameters = d.root.Field("parameters")
	if estimated_hit_ratio := parameters.Field("estimated_hit_ratio"); estimated_hit_ratio.Valid() {
		tab.str("estimated hit ratio").str(fmt.Sprintf("%.3f", estimated_hit_ratio.AsDouble())).commit()
	}
	if lower_limit := parameters.Field("lower_limit"); lower_limit.Valid() {
		tab.str("lower limit").str(fmt.Sprintf("%.3f", lower_limit.AsDouble())).commit()
	}
	if upper_limit := parameters.Field("upper_limit"); upper_limit.Valid() {
		tab.str("upper limit").str(fmt.Sprintf("%.3f", upper_limit.AsDouble())).commit()
	}
}

func (d *globalFilterDecision) analyze() {
	d.root = d.trace.findValue("global_filter_decision")
}

func (d *globalFilterDecision) useful() bool {
	return d.root.Valid()
}

func (d *globalFilterDecision) render(out *output) {
	out.fmt("global filter decision\n")
	tab := newTable()
	d.makeRows(tab)
	tab.render(out)
}

func newGlobalFilterDecision(trace protonTrace) *globalFilterDecision {
	res := &globalFilterDecision{root: slime.Invalid, trace: trace}
	res.analyze()
	return res
}
