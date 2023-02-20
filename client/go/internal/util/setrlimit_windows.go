// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

//go:build windows

package util

type ResourceId int

const (
	RLIMIT_CORE ResourceId = iota
	RLIMIT_NOFILE
	RLIMIT_NPROC
	NO_RLIMIT uint64 = ^uint64(0)
)

func SetResourceLimit(resource ResourceId, max uint64) {
	// nop
}
