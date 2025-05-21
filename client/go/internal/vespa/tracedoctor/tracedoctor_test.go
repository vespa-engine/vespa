// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/slime"
	"testing"
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
