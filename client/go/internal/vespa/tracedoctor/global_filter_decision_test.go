// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestGlobalFilterAnalysis(t *testing.T) {
	var decision = newGlobalFilterDecision(simpleProtonTrace())
	assert.True(t, decision.useful())
	assert.True(t, decision.root.Field("parameters").Field("estimated_hit_ratio").Valid())
}

func TestGlobalFilterAnalysisWhenNotPresent(t *testing.T) {
	var decision = newGlobalFilterDecision(emptyProtonTrace())
	assert.False(t, decision.useful())
}
