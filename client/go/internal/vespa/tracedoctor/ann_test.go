// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

func simpleProtonTrace() protonTrace {
	var source = slime.MakeObject(func(obj slime.Value) {
		obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup"))
				obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
					arr.Add(slime.MakeObject(func(obj slime.Value) {
						obj.Set("tag", slime.String("global_filter_decision"))
						obj.Set("parameters", slime.MakeObject(func(obj slime.Value) {
							obj.Set("estimated_hit_ratio", slime.Double(0.5))
						}))
					}))
				}))
			}))
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup_stats"))
				obj.Set("stats", slime.MakeObject(func(obj slime.Value) {
					obj.Set("approximate_nns_distances_computed", slime.Long(123))
				}))
			}))
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_execution_stats"))
				obj.Set("stats", slime.MakeObject(func(obj slime.Value) {
					obj.Set("exact_nns_distances_computed", slime.Long(456))
				}))
			}))
		}))
	})
	return protonTrace{source, nil}
}

func emptyProtonTrace() protonTrace {
	var source = slime.MakeObject(func(obj slime.Value) {
		obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("tag", slime.String("query_setup"))
				obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
				}))
			}))
		}))
	})
	return protonTrace{source, nil}
}

func TestStatsAnalysis(t *testing.T) {
	var stats nnsStats
	stats.analyze(new(simpleProtonTrace()))
	assert.True(t, stats.useful())
	assert.True(t, stats.approximateStatsUseful())
	assert.True(t, stats.exactStatsUseful())
	assert.True(t, stats.setupStats.Field("approximate_nns_distances_computed").Valid())
	assert.True(t, stats.evalStats.Field("exact_nns_distances_computed").Valid())
}

func TestStatsAnalysisWhenNotPresent(t *testing.T) {
	var stats nnsStats
	stats.analyze(new(emptyProtonTrace()))
	assert.False(t, stats.useful())
	assert.False(t, stats.approximateStatsUseful())
	assert.False(t, stats.exactStatsUseful())
}

func TestGlobalFilterAnalysis(t *testing.T) {
	var decision globalFilterDecision
	decision.analyze(new(simpleProtonTrace()))
	assert.True(t, decision.useful())
	assert.True(t, decision.root.Field("estimated_hit_ratio").Valid())
}

func TestGlobalFilterAnalysisWhenNotPresent(t *testing.T) {
	var decision globalFilterDecision
	decision.analyze(new(emptyProtonTrace()))
	assert.False(t, decision.useful())
}
