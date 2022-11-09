// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
)

func (opts *Options) getOrSetHeapSize(prefix string, heapSize AmountOfMemory) AmountOfMemory {
	var missing bool = true
	for _, x := range opts.jvmArgs {
		if strings.HasPrefix(x, prefix) {
			val, err := ParseJvmMemorySpec(strings.TrimPrefix(x, prefix))
			if err == nil {
				missing = false
				heapSize = val
			}
		}
	}
	if missing {
		opts.AppendOption(fmt.Sprintf("%s%s", prefix, heapSize.AsJvmSpec()))
	}
	return heapSize
}

func (opts *Options) CurMinHeapSize(fallback AmountOfMemory) AmountOfMemory {
	return opts.getOrSetHeapSize("-Xms", fallback)
}

func (opts *Options) CurMaxHeapSize(fallback AmountOfMemory) AmountOfMemory {
	return opts.getOrSetHeapSize("-Xmx", fallback)
}

func (opts *Options) AddDefaultHeapSizeArgs(minHeapSize, maxHeapSize AmountOfMemory) {
	trace.Trace("AddDefaultHeapSizeArgs", minHeapSize, "/", maxHeapSize)
	minHeapSize = opts.CurMinHeapSize(minHeapSize)
	maxHeapSize = opts.CurMaxHeapSize(maxHeapSize)
	opts.MaybeAddHugepages(maxHeapSize)
}

func (opts *Options) MaybeAddHugepages(heapSize AmountOfMemory) {
	thpSize := getTransparentHugepageSize()
	if thpSize.numBytes*2 < heapSize.numBytes {
		trace.Trace("add UseTransparentHugePages, 2 * thpSize", thpSize, " < maxHeap", heapSize)
		opts.AddOption("-XX:+UseTransparentHugePages")
	} else {
		trace.Trace("no UseTransparentHugePages, 2 * thpSize", thpSize, " >= maxHeap", heapSize)
	}
}

func adjustAvailableMemory(measured AmountOfMemory) AmountOfMemory {
	reserved := 1024 // MB
	need_min := 64   // MB
	available := measured.ToMB()
	if available > need_min+2*reserved {
		return MegaBytesOfMemory(available - reserved)
	}
	if available > need_min {
		adjusted := (available + need_min) / 2
		return MegaBytesOfMemory(adjusted)
	}
	return MegaBytesOfMemory(need_min)
}
