// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/vespa"
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
	ENV_VESPA_USE_VESPAMALLOC        = "VESPA_USE_VESPAMALLOC"
	ENV_VESPA_USE_VESPAMALLOC_D      = "VESPA_USE_VESPAMALLOC_D"
	ENV_VESPA_USE_VESPAMALLOC_DST    = "VESPA_USE_VESPAMALLOC_DST"
	ENV_VESPA_VALGRIND_OPT           = "VESPA_VALGRIND_OPT"
	ENV_VESPA_USER                   = "VESPA_USER"

	// backwards compatibility variables:
	ENV_ROOT                = "ROOT"
	ENV_HUGEPAGES_LIST      = "HUGEPAGES_LIST"
	ENV_MADVISE_LIST        = "MADVISE_LIST"
	ENV_NO_VESPAMALLOC_LIST = "NO_VESPAMALLOC_LIST"
	ENV_VESPAMALLOC_LIST    = "VESPAMALLOCD_LIST"
	ENV_VESPAMALLOCD_LIST   = "VESPAMALLOCD_LIST"
	ENV_VESPAMALLOCDST_LIST = "VESPAMALLOCDST_LIST"
)

func (spec *ProgSpec) considerFallback(varName, varValue string) {
	if spec.getenv(varName) == "" && varValue != "" {
		spec.setenv(varName, varValue)
	}
}

func (spec *ProgSpec) considerEnvFallback(targetVar, fallbackVar string) {
	spec.considerFallback(targetVar, spec.getenv(fallbackVar))
}

func (spec *ProgSpec) configureCommonEnv() {
	os.Unsetenv(ENV_LD_PRELOAD)
	spec.setenv(ENV_STD_THREAD_PREVENT_TRY_CATCH, "true")
	spec.setenv(ENV_GLIBCXX_FORCE_NEW, "1")
	// fallback from old env.vars:
	spec.considerEnvFallback(ENV_VESPA_USE_HUGEPAGES_LIST, ENV_HUGEPAGES_LIST)
	spec.considerEnvFallback(ENV_VESPA_USE_MADVISE_LIST, ENV_MADVISE_LIST)
	spec.considerEnvFallback(ENV_VESPA_USE_VESPAMALLOC, ENV_VESPAMALLOC_LIST)
	spec.considerEnvFallback(ENV_VESPA_USE_VESPAMALLOC_D, ENV_VESPAMALLOCD_LIST)
	spec.considerEnvFallback(ENV_VESPA_USE_VESPAMALLOC_DST, ENV_VESPAMALLOCDST_LIST)
	spec.considerEnvFallback(ENV_VESPA_USE_NO_VESPAMALLOC, ENV_NO_VESPAMALLOC_LIST)
	// other fallbacks:
	spec.considerFallback(ENV_ROOT, vespa.FindHome())
	spec.considerFallback(ENV_VESPA_USER, vespa.FindVespaUser())
	spec.considerFallback(ENV_VESPA_USE_HUGEPAGES_LIST, "all")
	spec.considerFallback(ENV_VESPA_USE_VESPAMALLOC, "all")
	spec.considerFallback(ENV_VESPA_USE_NO_VESPAMALLOC, strings.Join([]string{
		"vespa-rpc-invoke",
		"vespa-get-config",
		"vespa-sentinel-cmd",
		"vespa-route",
		"vespa-proton-cmd",
		"vespa-configproxy-cmd",
		"vespa-config-status",
	}, " "))

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
