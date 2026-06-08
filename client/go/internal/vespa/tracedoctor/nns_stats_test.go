// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package tracedoctor

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestApproximateNnsAnalysis(t *testing.T) {
	var stats = newApproximateNnsStats(simpleProtonTrace())
	assert.True(t, stats.useful())
	assert.True(t, stats.setupStats.Field("approximate_nns_distances_computed").Valid())
}

func TestExactNnsAnalysis(t *testing.T) {
	var stats = newExactNnsStats(simpleProtonTrace())
	assert.True(t, stats.useful())
	assert.True(t, stats.evalStats.Field("exact_nns_distances_computed").Valid())
}

func TestApproximateNnsStatsAnalysisWhenNotPresent(t *testing.T) {
	var stats = newApproximateNnsStats(emptyProtonTrace())
	assert.False(t, stats.useful())
}

func TestExactNnsStatsAnalysisWhenNotPresent(t *testing.T) {
	var stats = newExactNnsStats(emptyProtonTrace())
	assert.False(t, stats.useful())
}
