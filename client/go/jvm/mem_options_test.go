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
		adj := adjustAvailableMemory(MegaBytesOfMemory(i)).ToMB()
		assert.True(t, int(adj) >= lastAdj)
		lastAdj = int(adj)
	}
	adj := adjustAvailableMemory(MegaBytesOfMemory(31024)).ToMB()
	assert.Equal(t, 30000, int(adj))
}

func TestParseFree(t *testing.T) {
	res := parseFree(`
              total        used        free      shared  buff/cache   available
Mem:          19986         656        3157         218       16172       18832
Swap:          2047         320        1727
`)
	assert.Equal(t, MegaBytesOfMemory(19986), res)
}

func TestGetAvail(t *testing.T) {
	trace.AdjustVerbosity(0)
	available := getAvailableMemory()
	assert.True(t, available.ToMB() >= 0)
}
