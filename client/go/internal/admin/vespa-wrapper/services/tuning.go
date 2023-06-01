// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

const (
	DROP_CACHES_CTL                = "/proc/sys/vm/drop_caches"
	TRANSPARENT_HUGEPAGE_ENABLED   = "/sys/kernel/mm/transparent_hugepage/enabled"
	TRANSPARENT_HUGEPAGE_DEFRAG    = "/sys/kernel/mm/transparent_hugepage/defrag"
	TRANSPARENT_HUGEPAGE_KH_DEFRAG = "/sys/kernel/mm/transparent_hugepage/khugepaged/defrag"
	ZONE_RECLAIM_CTL               = "/proc/sys/vm/zone_reclaim_mode"
	VM_MAX_MAP_COUNT               = "/proc/sys/vm/max_map_count"
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

func increase_vm_max_map_count() {
	const need_minimum = 262144
	var min_as_text string = strconv.Itoa(need_minimum)
	const name = "vm.max_map_count"
	trace.Debug("Checking: " + VM_MAX_MAP_COUNT)
	data, err := os.ReadFile(VM_MAX_MAP_COUNT)
	if err != nil {
		trace.Info("Could not check", name, " -  assuming it is OK and proceeding")
		return
	}
	line := strings.TrimSuffix(string(data), "\n")
	qline := "[" + line + "]"
	num, err := strconv.Atoi(line)
	if err != nil || num <= 0 {
		trace.Info("Bad data", qline, "checking", name, " -  assuming it is OK and proceeding")
		return
	}
	if num < need_minimum {
		trace.Info("Too low", name, "["+line+"] - trying to increase it to", min_as_text)
		if maybeEcho(VM_MAX_MAP_COUNT, min_as_text) {
			trace.Debug("Increased:", name)
		} else {
			trace.Warning("Could not increase", name, "- current value", qline, "too low, should be at least", min_as_text)
		}
	}
}
