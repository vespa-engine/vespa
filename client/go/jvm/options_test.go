// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

const (
	aa = 123
	bb = 234
	cc = 456
	dd = 567
	ee = 16 * PowerOfTwo10
	ff = 31 * PowerOfTwo10
)

func TestHeapMbSimple(t *testing.T) {
	o := NewOptions(NewStandaloneContainer("foo"))
	assert.Equal(t, aa, o.CurMinHeapMb(aa))
	assert.Equal(t, bb, o.CurMaxHeapMb(bb))
	assert.Equal(t, 2, len(o.jvmArgs))
	assert.Equal(t, "-Xms123m", o.jvmArgs[0])
	assert.Equal(t, "-Xmx234m", o.jvmArgs[1])
}

func TestHeapMbMulti(t *testing.T) {
	o := NewOptions(NewStandaloneContainer("foo"))
	assert.Equal(t, aa, o.CurMinHeapMb(aa))
	assert.Equal(t, aa, o.CurMaxHeapMb(aa))
	assert.Equal(t, 2, len(o.jvmArgs))
	o.AppendOption("-Xms234m")
	o.AppendOption("-Xmx456m")
	assert.Equal(t, 4, len(o.jvmArgs))
	assert.Equal(t, bb, o.CurMinHeapMb(aa))
	assert.Equal(t, bb, o.CurMinHeapMb(dd))
	assert.Equal(t, cc, o.CurMaxHeapMb(aa))
	assert.Equal(t, cc, o.CurMaxHeapMb(dd))
	o.AppendOption("-Xms1g")
	o.AppendOption("-Xmx2g")
	assert.Equal(t, 1*PowerOfTwo10, o.CurMinHeapMb(aa))
	assert.Equal(t, 2*PowerOfTwo10, o.CurMaxHeapMb(aa))
	o.AppendOption("-Xms16777216k")
	o.AppendOption("-Xmx32505856k")
	assert.Equal(t, ee, o.CurMinHeapMb(aa))
	assert.Equal(t, ff, o.CurMaxHeapMb(aa))
}

func TestHeapMbAdd(t *testing.T) {
	o := NewOptions(NewStandaloneContainer("foo"))
	o.AddDefaultHeapSizeArgs(12345, 23456)
	assert.Equal(t, 3, len(o.jvmArgs))
	assert.Equal(t, "-Xms12345m", o.jvmArgs[0])
	assert.Equal(t, "-Xmx23456m", o.jvmArgs[1])
	assert.Equal(t, "-XX:+UseTransparentHugePages", o.jvmArgs[2])
}

func TestHeapMbNoAdd(t *testing.T) {
	o := NewOptions(NewStandaloneContainer("foo"))
	o.AppendOption("-Xms128k")
	o.AppendOption("-Xmx1280k")
	o.AddDefaultHeapSizeArgs(234, 345)
	assert.Equal(t, 2, len(o.jvmArgs))
	assert.Equal(t, "-Xms128k", o.jvmArgs[0])
	assert.Equal(t, "-Xmx1280k", o.jvmArgs[1])
}
