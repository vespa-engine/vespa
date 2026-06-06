// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

type costAnalysis struct {
	queryTree    *queryTree
	maxTimeMs    float64
	maxCost      float64
	foundSamples int64
	usedSamples  int64
}

func newCostAnalysis(p protonTrace) *costAnalysis {
	q := p.extractQuery()
	c := &costAnalysis{queryTree: q}
	applySample := func(sample perfSample) {
		if q.applySample(sample) {
			c.usedSamples += sample.count()
		}
	}
	for _, thread := range p.findThreadTraces() {
		slime.Select(thread.source, hasTag("match_profiling"), func(_ *slime.Path, v slime.Value) {
			eachSample(v, func(sample perfSample) {
				c.foundSamples += sample.count()
			})
			walkSamples(v, func(sample perfSample, inSeek bool) bool {
				if sample.isUnpackSample() && !inSeek {
					// Cost estimate covers unpack only as part of seek.
					return false
				}
				applySample(sample)
				return true
			})
		})
	}
	slime.Select(p.source, hasTag("setup_profiling"), func(_ *slime.Path, v slime.Value) {
		eachSample(v, func(sample perfSample) {
			c.foundSamples += sample.count()
		})
		walkSamples(v, func(sample perfSample, inSeek bool) bool {
			if !sample.isFetchPostingsSample() || sample.count() != 1 {
				// Repeated fetch-postings samples mix multiple optimization iterations;
				// the latest setup cost cannot be isolated reliably.
				return false
			}
			applySample(sample)
			return true
		})
	})
	c.maxTimeMs = q.root.totalTimeMs
	c.maxCost = q.root.absCost
	return c
}

func (c *costAnalysis) useful() bool {
	return c.maxCost != 0
}

func (c *costAnalysis) analyze(n *queryNode) string {
	if c.maxTimeMs == 0 || c.maxCost == 0 {
		return ""
	}
	relTime := n.totalTimeMs / c.maxTimeMs
	relCost := n.absCost / c.maxCost
	// + means more expensive than expected, - means less; more symbols = bigger deviation.
	diff := relTime - relCost
	switch {
	case diff > 0.20:
		return "+++"
	case diff > 0.10:
		return "++"
	case diff > 0.05:
		return "+"
	case diff < -0.20:
		return "---"
	case diff < -0.10:
		return "--"
	case diff < -0.05:
		return "-"
	}
	return ""
}

func (c *costAnalysis) render(output *output) {
	c.queryTree.makeTable([]queryColumn{
		{"cost %", func(n *queryNode) string {
			if c.maxCost == 0 {
				return "-"
			}
			return fmt.Sprintf("%.3f", 100.0*n.absCost/c.maxCost)
		}},
		{"time %", func(n *queryNode) string {
			if c.maxTimeMs == 0 {
				return "-"
			}
			return fmt.Sprintf("%.3f", 100.0*n.totalTimeMs/c.maxTimeMs)
		}},
		{"ms/cost", func(n *queryNode) string {
			if n.absCost == 0 {
				return "-"
			}
			return fmt.Sprintf("%.3f", n.totalTimeMs/n.absCost)
		}},
		{"eval", func(n *queryNode) string { return n.evalMode() }},
		{"diff", func(n *queryNode) string { return c.analyze(n) }},
	}).render(output)
}
