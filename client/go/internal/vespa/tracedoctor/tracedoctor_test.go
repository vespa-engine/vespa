// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bytes"
	"strings"
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

func TestAnalyzeProtonTraceSortFeatures(t *testing.T) {
	ctx := NewContext(slime.Object())
	ctx.MakePrompt()
	analyze := func(trace protonTrace) string {
		var buf bytes.Buffer
		ctx.analyzeProtonTrace(trace, nil, &output{out: &buf})
		return buf.String()
	}
	out := analyze(testFactory{}.sortFeaturesProtonTrace(true))
	assert.Equal(t, 2, strings.Count(out, sortFeaturesTaskPromptStr))
	assert.Equal(t, 1, strings.Count(out, protonSummaryPromptStr+"\n"+sortFeaturesTaskPromptStr))
	assert.Equal(t, 1, strings.Count(out, matchThreadSummaryPromptStr+"\n"+sortFeaturesTaskPromptStr))
	assert.Equal(t, 1, strings.Count(out, "sort feature profiling for thread #1 (total time was 100.000 ms)"))
	assert.Equal(t, 1, strings.Count(out, sortFeaturesProfilingPromptStr))
	assert.Equal(t, 1, strings.Count(out, "function has_stock"))
	assert.Equal(t, 1, strings.Count(out, "attribute(stock)"))

	// covers the summary rows, both prompts and the detail header
	out = analyze(testFactory{}.sortFeaturesProtonTrace(false))
	assert.NotContains(t, out, "sort feature")
}
