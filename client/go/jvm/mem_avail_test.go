// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/trace"
)

func TestCg2Get(t *testing.T) {
	trace.AdjustVerbosity(2)
	const MM = "memory.max"
	res, err := vespa_cg2get(MM)

	res, err = vespa_cg2get_impl("mock-cg2/a", MM)
	assert.Nil(t, err)
	assert.Equal(t, "123", res)

	res, err = vespa_cg2get_impl("mock-cg2/b", MM)
	assert.Nil(t, err)
	assert.Equal(t, "67430985728", res)

	res, err = vespa_cg2get_impl("mock-cg2/c", MM)
	assert.Nil(t, err)
	assert.Equal(t, "9663676416", res)
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
