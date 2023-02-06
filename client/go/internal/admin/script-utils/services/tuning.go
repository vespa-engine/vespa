// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

const (
	DROP_CACHES_CTL                = "/proc/sys/vm/drop_caches"
	TRANSPARENT_HUGEPAGE_ENABLED   = "/sys/kernel/mm/transparent_hugepage/enabled"
	TRANSPARENT_HUGEPAGE_DEFRAG    = "/sys/kernel/mm/transparent_hugepage/defrag"
	TRANSPARENT_HUGEPAGE_KH_DEFRAG = "/sys/kernel/mm/transparent_hugepage/khugepaged/defrag"
	ZONE_RECLAIM_CTL               = "/proc/sys/vm/zone_reclaim_mode"
)

func maybeEcho(fileName, toWrite string) bool {
	f, err := os.OpenFile(fileName, os.O_WRONLY, 0)
	if err == nil {
		_, err = fmt.Fprintf(f, "%s\n", toWrite)
		f.Close()
		if err == nil {
			trace.Debug("wrote", toWrite, "to", fileName)
			return true
		} else {
			trace.Warning(err)
		}
	}
	return false
}

func enable_transparent_hugepages_with_background_compaction() {
	// Should probably also be done on host.
	maybeEcho(TRANSPARENT_HUGEPAGE_ENABLED, "always")
	maybeEcho(TRANSPARENT_HUGEPAGE_DEFRAG, "never")
	maybeEcho(TRANSPARENT_HUGEPAGE_KH_DEFRAG, "1")
}

func disable_vm_zone_reclaim_mode() {
	maybeEcho(ZONE_RECLAIM_CTL, "0")
}

func drop_caches() {
	if maybeEcho(DROP_CACHES_CTL, "3") {
		trace.Debug("dropped caches")
	}
}
