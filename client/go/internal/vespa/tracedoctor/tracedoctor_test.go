// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
)

func addExampleTiming(obj slime.Value) {
	timing := obj.Set("timing", slime.Object())
	timing.Set("querytime", slime.Double(0.50))
	timing.Set("summaryfetchtime", slime.Double(0.25))
	timing.Set("searchtime", slime.Double(1.0))
}

func makeExampleResult() slime.Value {
	root := slime.Object()
	addExampleTiming(root)
	return root
}

func TestExtractTiming(t *testing.T) {
	timing := extractTiming(makeExampleResult())
	assert.Equal(t, 500.0, timing.queryMs)
	assert.Equal(t, 250.0, timing.summaryMs)
	assert.Equal(t, 1000.0, timing.totalMs)
}

func makeProfileSummaryResult() slime.Value {
	root := makeExampleResult()
	root.Set("trace", slime.MakeObject(func(obj slime.Value) {
		obj.Set("children", slime.MakeArray(func(arr slime.Value) {
			arr.Add(slime.MakeObject(func(obj slime.Value) {
				obj.Set("document-type", slime.String("music"))
				obj.Set("distribution-key", slime.Long(7))
				obj.Set("duration_ms", slime.Double(123.0))
				obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
					arr.Add(slime.MakeObject(func(obj slime.Value) {
						obj.Set("tag", slime.String("query_execution"))
						obj.Set("threads", slime.MakeArray(func(arr slime.Value) {
							arr.Add(slime.MakeObject(func(obj slime.Value) {
								obj.Set("traces", slime.MakeArray(func(arr slime.Value) {
									arr.Add(slime.MakeObject(func(obj slime.Value) {
										obj.Set("tag", slime.String("match_profiling"))
										obj.Set("total_time_ms", slime.Double(11.0))
									}))
									arr.Add(slime.MakeObject(func(obj slime.Value) {
										obj.Set("tag", slime.String("first_phase_profiling"))
										obj.Set("total_time_ms", slime.Double(22.0))
										obj.Set("roots", slime.MakeArray(func(arr slime.Value) {
											arr.Add(slime.MakeObject(func(obj slime.Value) {
												obj.Set("name", slime.String("nativeRank(title)"))
												obj.Set("count", slime.Long(3))
												obj.Set("self_time_ms", slime.Double(7.5))
											}))
										}))
									}))
									arr.Add(slime.MakeObject(func(obj slime.Value) {
										obj.Set("tag", slime.String("second_phase_profiling"))
										obj.Set("total_time_ms", slime.Double(33.0))
										obj.Set("roots", slime.MakeArray(func(arr slime.Value) {
											arr.Add(slime.MakeObject(func(obj slime.Value) {
												obj.Set("name", slime.String("onnx(model)"))
												obj.Set("count", slime.Long(2))
												obj.Set("self_time_ms", slime.Double(9.5))
											}))
										}))
									}))
								}))
							}))
						}))
					}))
				}))
			}))
		}))
	}))
	return root
}

func TestProfileSummaryExtractsStableTimingAndPhaseData(t *testing.T) {
	summary := NewContext(makeProfileSummaryResult()).Summary()

	assert.Equal(t, 1, summary.SchemaVersion)
	assert.Equal(t, 1000.0, summary.Timing.TotalMs)
	assert.Equal(t, 500.0, summary.Timing.QueryMs)
	assert.Equal(t, 250.0, summary.Timing.SummaryMs)
	assert.Equal(t, 250.0, summary.Timing.OtherMs)

	assert.Equal(t, 1, len(summary.Searches))
	search := summary.Searches[0]
	assert.Equal(t, 0, search.ID)
	assert.Equal(t, "music", search.DocumentType)
	assert.Equal(t, 1, search.Nodes)
	assert.Equal(t, 123.0, search.BackendTimeMs)

	assert.Equal(t, 1, len(search.NodeSummaries))
	node := search.NodeSummaries[0]
	assert.Equal(t, "music[7]", node.Name)
	assert.Equal(t, 123.0, node.DurationMs)
	assert.Equal(t, 11.0, node.Tasks.MatchingMs)
	assert.Equal(t, 22.0, node.Tasks.FirstPhaseMs)
	assert.Equal(t, 33.0, node.Tasks.SecondPhaseMs)

	assert.Equal(t, []ProfileComponent{{Name: "nativeRank(title)", Count: 3, SelfTimeMs: 7.5}}, node.TopFirstPhaseComponents)
	assert.Equal(t, []ProfileComponent{{Name: "onnx(model)", Count: 2, SelfTimeMs: 9.5}}, node.TopSecondPhaseComponents)
}
