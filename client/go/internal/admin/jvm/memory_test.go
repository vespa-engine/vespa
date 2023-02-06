// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestConversion(t *testing.T) {
	v1 := GigaBytesOfMemory(17)
	v2 := MegaBytesOfMemory(17 * 1024)
	v3 := KiloBytesOfMemory(17 * 1024 * 1024)
	var numBytes int64 = 17 * 1024 * 1024 * 1024
	assert.Equal(t, v1, v2)
	assert.Equal(t, v1, v3)
	assert.Equal(t, v2, v1)
	assert.Equal(t, v2, v3)
	assert.Equal(t, v3, v1)
	assert.Equal(t, v3, v2)
	assert.Equal(t, numBytes, v1.ToBytes())
	assert.Equal(t, numBytes, v2.ToBytes())
	assert.Equal(t, numBytes, v3.ToBytes())
	assert.Equal(t, "17g", v1.AsJvmSpec())
	assert.Equal(t, "17g", v2.AsJvmSpec())
	assert.Equal(t, "17g", v3.AsJvmSpec())

	v1 = GigaBytesOfMemory(17)
	v2 = MegaBytesOfMemory(17 * 1000)
	v3 = KiloBytesOfMemory(17 * 1000 * 1000)
	assert.Equal(t, "17g", v1.AsJvmSpec())
	assert.Equal(t, "17000m", v2.AsJvmSpec())
	assert.Equal(t, "17000000k", v3.AsJvmSpec())
	assert.Equal(t, "{17 GiB}", v1.String())
	assert.Equal(t, "{17000 MiB}", v2.String())
	assert.Equal(t, "{17000000 KiB}", v3.String())

	var result AmountOfMemory
	var err error
	result, err = ParseJvmMemorySpec("17g")
	assert.Nil(t, err)
	assert.Equal(t, v1, result)
	result, err = ParseJvmMemorySpec("17000m")
	assert.Nil(t, err)
	assert.Equal(t, v2, result)
	result, err = ParseJvmMemorySpec("17000000k")
	assert.Nil(t, err)
	assert.Equal(t, v3, result)
}
