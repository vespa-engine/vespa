// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

const (
	ENV_JAVA_HOME        = util.ENV_JAVA_HOME
	ENV_LD_LIBRARY_PATH  = util.ENV_LD_LIBRARY_PATH
	ENV_LD_PRELOAD       = util.ENV_LD_PRELOAD
	ENV_MALLOC_ARENA_MAX = util.ENV_MALLOC_ARENA_MAX
	ENV_PATH             = util.ENV_PATH
	ENV_ROOT             = util.ENV_ROOT
	ENV_VESPA_USER       = util.ENV_VESPA_USER

	ENV_VESPA_AFFINITY_CPU_SOCKET    = "VESPA_AFFINITY_CPU_SOCKET"
	ENV_VESPA_LOAD_CODE_AS_HUGEPAGES = "VESPA_LOAD_CODE_AS_HUGEPAGES"
	ENV_VESPA_MALLOC_HUGEPAGES       = "VESPA_MALLOC_HUGEPAGES"
	ENV_VESPA_MALLOC_MADVISE_LIMIT   = "VESPA_MALLOC_MADVISE_LIMIT"
	ENV_VESPA_NO_NUMACTL             = "VESPA_NO_NUMACTL"
	ENV_VESPA_TIMER_HZ               = "VESPA_TIMER_HZ"
	ENV_VESPA_USE_HUGEPAGES          = "VESPA_USE_HUGEPAGES"
	ENV_VESPA_USE_HUGEPAGES_LIST     = "VESPA_USE_HUGEPAGES_LIST"
	ENV_VESPA_USE_MADVISE_LIST       = "VESPA_USE_MADVISE_LIST"
	ENV_VESPA_USE_NO_VESPAMALLOC     = "VESPA_USE_NO_VESPAMALLOC"
	ENV_VESPA_USE_VALGRIND           = "VESPA_USE_VALGRIND"
	ENV_VESPA_USE_VESPAMALLOC        = "VESPA_USE_VESPAMALLOC"
	ENV_VESPA_USE_VESPAMALLOC_D      = "VESPA_USE_VESPAMALLOC_D"
	ENV_VESPA_USE_VESPAMALLOC_DST    = "VESPA_USE_VESPAMALLOC_DST"
	ENV_VESPA_VALGRIND_OPT           = "VESPA_VALGRIND_OPT"

	// backwards compatibility variables:
	ENV_GLIBCXX_FORCE_NEW            = "GLIBCXX_FORCE_NEW"
	ENV_HUGEPAGES_LIST               = "HUGEPAGES_LIST"
	ENV_MADVISE_LIST                 = "MADVISE_LIST"
	ENV_NO_VESPAMALLOC_LIST          = "NO_VESPAMALLOC_LIST"
	ENV_STD_THREAD_PREVENT_TRY_CATCH = "STD_THREAD_PREVENT_TRY_CATCH"
	ENV_VESPAMALLOCDST_LIST          = "VESPAMALLOCDST_LIST"
	ENV_VESPAMALLOCD_LIST            = "VESPAMALLOCD_LIST"
	ENV_VESPAMALLOC_LIST             = "VESPAMALLOCD_LIST"
)

func (spec *Spec) configureCommonEnv() {
	os.Unsetenv(ENV_LD_PRELOAD)
	spec.Setenv(ENV_STD_THREAD_PREVENT_TRY_CATCH, "true")
	spec.Setenv(ENV_GLIBCXX_FORCE_NEW, "1")
	spec.Setenv(ENV_LD_LIBRARY_PATH, vespa.FindHome()+"/lib64")
	spec.Setenv(ENV_MALLOC_ARENA_MAX, "1")

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
