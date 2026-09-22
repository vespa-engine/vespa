// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"bytes"
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

func TestAnalyzeThreadSortFeatures(t *testing.T) {
	ctx := NewContext(slime.Object())
	withSort := twoThreadSortFeatureTraces(true)
	withoutSort := twoThreadSortFeatureTraces(false)
	withThreads := withSort.findThreadTraces()
	withoutThreads := withoutSort.findThreadTraces()

	var buf bytes.Buffer
	ctx.analyzeThread(withSort, withThreads[1], nil, &output{out: &buf})
	out := buf.String()
	assert.Contains(t, out, "sort features")
	assert.Contains(t, out, "sort feature profiling for thread #1")
	assert.Contains(t, out, "function has_stock")

	buf.Reset()
	ctx.analyzeThread(withoutSort, withoutThreads[0], nil, &output{out: &buf})
	out = buf.String()
	assert.Contains(t, out, "sort features")
	assert.NotContains(t, out, "sort feature profiling for thread")
}
