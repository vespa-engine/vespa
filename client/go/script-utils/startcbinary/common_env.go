// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/trace"
)

const (
	ENV_LD_PRELOAD                   = "LD_PRELOAD"
	ENV_STD_THREAD_PREVENT_TRY_CATCH = "STD_THREAD_PREVENT_TRY_CATCH"
	ENV_GLIBCXX_FORCE_NEW            = "GLIBCXX_FORCE_NEW"

	ENV_VESPA_AFFINITY_CPU_SOCKET    = "VESPA_AFFINITY_CPU_SOCKET"
	ENV_VESPA_LOAD_CODE_AS_HUGEPAGES = "VESPA_LOAD_CODE_AS_HUGEPAGES"
	ENV_VESPA_MALLOC_HUGEPAGES       = "VESPA_MALLOC_HUGEPAGES"
	ENV_VESPA_MALLOC_MADVISE_LIMIT   = "VESPA_MALLOC_MADVISE_LIMIT"
	ENV_VESPA_NO_NUMACTL             = "VESPA_NO_NUMACTL"
	ENV_VESPA_USE_HUGEPAGES          = "VESPA_USE_HUGEPAGES"
	ENV_VESPA_USE_HUGEPAGES_LIST     = "VESPA_USE_HUGEPAGES_LIST"
	ENV_VESPA_USE_MADVISE_LIST       = "VESPA_USE_MADVISE_LIST"
	ENV_VESPA_USE_NO_VESPAMALLOC     = "VESPA_USE_NO_VESPAMALLOC"
	ENV_VESPA_USE_VALGRIND           = "VESPA_USE_VALGRIND"
	ENV_VESPA_USE_VESPAMALLOC_D      = "VESPA_USE_VESPAMALLOC_D"
	ENV_VESPA_USE_VESPAMALLOC_DST    = "VESPA_USE_VESPAMALLOC_DST"
	ENV_VESPA_VALGRIND_OPT           = "VESPA_VALGRIND_OPT"
)

func (spec *ProgSpec) configureCommonEnv() {
	os.Unsetenv(ENV_LD_PRELOAD)
	spec.setenv(ENV_STD_THREAD_PREVENT_TRY_CATCH, "true")
	spec.setenv(ENV_GLIBCXX_FORCE_NEW, "1")
}

func (spec *ProgSpec) configureHugePages() {
	if spec.matchesListEnv(ENV_VESPA_USE_HUGEPAGES_LIST) {
		spec.setenv(ENV_VESPA_USE_HUGEPAGES, "yes")
	}
}

func (spec *ProgSpec) configureUseMadvise() {
	limit := spec.valueFromListEnv(ENV_VESPA_USE_MADVISE_LIST)
	if limit != "" {
		trace.Trace("shall use madvise with limit", limit, "as set in", ENV_VESPA_USE_MADVISE_LIST)
		spec.setenv(ENV_VESPA_MALLOC_MADVISE_LIMIT, limit)
		return
	}
}
