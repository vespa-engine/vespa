// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	PowerOfTwo10 = 1 << 10
)

func (opts *Options) getOrSetHeapMb(prefix string, heapMb int) int {
	var missing bool = true
	for _, x := range opts.jvmArgs {
		if strings.HasPrefix(x, prefix) {
			var val int
			var suffix rune
			n, err := fmt.Sscanf(x, prefix+"%d%c", &val, &suffix)
			if n == 2 && err == nil {
				missing = false
				switch suffix {
				case 'k':
					heapMb = val / PowerOfTwo10
				case 'm':
					heapMb = val
				case 'g':
					heapMb = val * PowerOfTwo10
				default:
					missing = true
				}
			}
		}
	}
	if missing {
		suffix := "m"
		newVal := heapMb
		if (newVal % PowerOfTwo10) == 0 {
			suffix = "g"
			newVal /= PowerOfTwo10
		}
		opts.AppendOption(fmt.Sprintf("%s%d%s", prefix, newVal, suffix))
	}
	return heapMb
}

func (opts *Options) CurMinHeapMb(fallback int) int {
	return opts.getOrSetHeapMb("-Xms", fallback)
}

func (opts *Options) CurMaxHeapMb(fallback int) int {
	return opts.getOrSetHeapMb("-Xmx", fallback)
}

func (opts *Options) AddDefaultHeapSizeArgs(minHeapMb, maxHeapMb int) {
	trace.Trace("AddDefaultHeapSizeArgs", minHeapMb, "/", maxHeapMb)
	minHeapMb = opts.CurMinHeapMb(minHeapMb)
	maxHeapMb = opts.CurMaxHeapMb(maxHeapMb)
	opts.MaybeAddHugepages(maxHeapMb)
}

func (opts *Options) MaybeAddHugepages(maxHeapMb int) {
	thpSizeMb := util.GetThpSizeMb()
	if thpSizeMb*2 < maxHeapMb {
		trace.Trace("add UseTransparentHugePages, thpSize", thpSizeMb, "* 2 < maxHeap", maxHeapMb)
		opts.AddOption("-XX:+UseTransparentHugePages")
	} else {
		trace.Trace("no UseTransparentHugePages, thpSize", thpSizeMb, "* 2 >= maxHeap", maxHeapMb)
	}
}

func adjustAvailableMemory(measured int) int {
	reserved := 1024
	need_min := 64
	if measured > need_min+2*reserved {
		return measured - reserved
	}
	if measured > need_min {
		return (measured + need_min) / 2
	}
	return need_min
}
