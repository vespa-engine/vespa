// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAdjustment(t *testing.T) {
	lastAdj := 64
	for i := range 4096 {
		adj := adjustAvailableMemory(MegaBytesOfMemory(i)).ToMB()
		assert.True(t, int(adj) >= lastAdj)
		lastAdj = int(adj)
	}
	adj := adjustAvailableMemory(MegaBytesOfMemory(30700)).ToMB()
	assert.Equal(t, 30000, int(adj))
}
