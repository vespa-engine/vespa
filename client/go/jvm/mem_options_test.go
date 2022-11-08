// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/trace"
)

func TestAdjustment(t *testing.T) {
	lastAdj := 64
	for i := 0; i < 4096; i++ {
		adj := adjustAvailableMemory(i)
		assert.True(t, adj >= lastAdj)
		lastAdj = adj
	}
	assert.Equal(t, 30000, adjustAvailableMemory(31024))
}

func TestParseFree(t *testing.T) {
	res := parseFree(`
              total        used        free      shared  buff/cache   available
Mem:          19986         656        3157         218       16172       18832
Swap:          2047         320        1727
`)
	assert.Equal(t, 19986, res)
}

func TestGetAvail(t *testing.T) {
	trace.AdjustVerbosity(0)
	available := getAvailableMbOfMemory()
	assert.True(t, available >= 0)
}
