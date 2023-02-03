// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package jvm

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
)

type dummyContainer struct{ containerBase }

func (*dummyContainer) ArgForMain() string           { return "arg-for-main" }
func (*dummyContainer) exportExtraEnv(ps *prog.Spec) {}
func newDummyContainer() Container {
	var dc dummyContainer
	dc.serviceName = "foo"
	dc.jvmOpts = NewOptions(&dc)
	return &dc
}

func TestHeapSizeSimple(t *testing.T) {
	var (
		aa = MegaBytesOfMemory(123)
		bb = MegaBytesOfMemory(234)
	)
	o := newDummyContainer().JvmOptions()
	assert.Equal(t, aa, o.CurMinHeapSize(aa))
	assert.Equal(t, bb, o.CurMaxHeapSize(bb))
	assert.Equal(t, 2, len(o.jvmArgs))
	assert.Equal(t, "-Xms123m", o.jvmArgs[0])
	assert.Equal(t, "-Xmx234m", o.jvmArgs[1])
}

func TestHeapSizeMulti(t *testing.T) {
	var (
		aa = MegaBytesOfMemory(123)
		bb = MegaBytesOfMemory(234)
		cc = MegaBytesOfMemory(456)
		dd = MegaBytesOfMemory(567)
	)
	o := newDummyContainer().JvmOptions()
	assert.Equal(t, aa, o.CurMinHeapSize(aa))
	assert.Equal(t, aa, o.CurMaxHeapSize(aa))
	assert.Equal(t, 2, len(o.jvmArgs))
	o.AppendOption("-Xms234m")
	o.AppendOption("-Xmx456m")
	assert.Equal(t, 4, len(o.jvmArgs))
	assert.Equal(t, bb, o.CurMinHeapSize(aa))
	assert.Equal(t, bb, o.CurMinHeapSize(dd))
	assert.Equal(t, cc, o.CurMaxHeapSize(aa))
	assert.Equal(t, cc, o.CurMaxHeapSize(dd))
	o.AppendOption("-Xms1g")
	o.AppendOption("-Xmx2g")
	assert.Equal(t, GigaBytesOfMemory(1), o.CurMinHeapSize(aa))
	assert.Equal(t, GigaBytesOfMemory(2), o.CurMaxHeapSize(aa))
	o.AppendOption("-Xms16777216k")
	o.AppendOption("-Xmx32505856k")
	assert.Equal(t, KiloBytesOfMemory(16777216), o.CurMinHeapSize(aa))
	assert.Equal(t, KiloBytesOfMemory(32505856), o.CurMaxHeapSize(aa))
}

func TestHeapSizeAdd(t *testing.T) {
	var (
		gg = MegaBytesOfMemory(12345)
		hh = MegaBytesOfMemory(23456)
	)
	o := newDummyContainer().JvmOptions()
	o.AddDefaultHeapSizeArgs(gg, hh)
	assert.Equal(t, 3, len(o.jvmArgs))
	assert.Equal(t, "-Xms12345m", o.jvmArgs[0])
	assert.Equal(t, "-Xmx23456m", o.jvmArgs[1])
	assert.Equal(t, "-XX:+UseTransparentHugePages", o.jvmArgs[2])
}

func TestHeapSizeNoAdd(t *testing.T) {
	var (
		bb = MegaBytesOfMemory(234)
		cc = MegaBytesOfMemory(456)
	)
	o := newDummyContainer().JvmOptions()
	o.AppendOption("-Xms128k")
	o.AppendOption("-Xmx1280k")
	o.AddDefaultHeapSizeArgs(bb, cc)
	assert.Equal(t, 2, len(o.jvmArgs))
	assert.Equal(t, "-Xms128k", o.jvmArgs[0])
	assert.Equal(t, "-Xmx1280k", o.jvmArgs[1])
}
