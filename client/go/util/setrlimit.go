// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

//go:build !windows

package util

import (
	"os"
	"strconv"

	"github.com/vespa-engine/vespa/client/go/trace"
	"golang.org/x/sys/unix"
)

type ResourceId int

const (
	RLIMIT_CORE   ResourceId = unix.RLIMIT_AS
	RLIMIT_NOFILE ResourceId = unix.RLIMIT_NOFILE
	RLIMIT_NPROC  ResourceId = unix.RLIMIT_NPROC
	NO_RLIMIT     uint64     = ^uint64(0)
)

func (rid ResourceId) String() string {
	switch rid {
	case RLIMIT_CORE:
		return "core file size"
	case RLIMIT_NOFILE:
		return "open files"
	case RLIMIT_NPROC:
		return "max user processes"
	}
	return "unknown resource id"
}

func readableLimit(val uint64) string {
	if val == NO_RLIMIT {
		return "unlimited"
	}
	return strconv.FormatUint(val, 10)
}

func SetResourceLimit(resource ResourceId, newVal uint64) {
	var current unix.Rlimit
	err := unix.Getrlimit(int(resource), &current)
	if err != nil {
		trace.Warning("Could not get current resource limit:", err)
		return
	}
	wanted := current
	if current.Max < newVal {
		if os.Getuid() == 0 {
			wanted.Max = newVal
		} else if newVal > current.Max {
			trace.Warning(
				"Wanted", newVal,
				"as limit for", resource.String(),
				"but cannot exceed current hard limit:", current.Max)
			newVal = current.Max
		}
	}
	if current.Cur < newVal {
		wanted.Cur = newVal
	}
	err = unix.Setrlimit(int(resource), &wanted)
	if err != nil {
		trace.Trace("Failed setting resource limit:", err)
	} else if (current.Cur != wanted.Cur) || (current.Max != wanted.Max) {
		trace.Debug("Resource limit", resource, "was:", readableLimit(current.Cur), "/", readableLimit(current.Max))
		trace.Trace("Resource limit", resource, "adjusted OK:", readableLimit(wanted.Cur), "/", readableLimit(wanted.Max))
	}
}
