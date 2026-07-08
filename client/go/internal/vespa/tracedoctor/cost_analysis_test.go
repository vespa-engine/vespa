// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestCostAnalysisUseful(t *testing.T) {
	assert.True(t, (&costAnalysis{maxCost: 100}).useful())
	assert.False(t, (&costAnalysis{maxTimeMs: 100}).useful())
}

func TestCostAnalysisDiff(t *testing.T) {
	c := &costAnalysis{maxTimeMs: 100, maxCost: 100}
	assert.Equal(t, "", c.analyze(&queryNode{totalTimeMs: 10, absCost: 10}))
	assert.Equal(t, "+", c.analyze(&queryNode{totalTimeMs: 16, absCost: 10}))
	assert.Equal(t, "++", c.analyze(&queryNode{totalTimeMs: 21, absCost: 10}))
	assert.Equal(t, "+++", c.analyze(&queryNode{totalTimeMs: 31, absCost: 10}))
	assert.Equal(t, "-", c.analyze(&queryNode{totalTimeMs: 10, absCost: 16}))
	assert.Equal(t, "--", c.analyze(&queryNode{totalTimeMs: 10, absCost: 21}))
	assert.Equal(t, "---", c.analyze(&queryNode{totalTimeMs: 10, absCost: 31}))
	assert.Equal(t, "", (&costAnalysis{}).analyze(&queryNode{totalTimeMs: 10, absCost: 10}))
}
