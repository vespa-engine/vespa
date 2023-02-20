// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func (spec *Spec) configureCommonEnv() {
	os.Unsetenv(envvars.LD_PRELOAD)
	spec.Setenv(envvars.STD_THREAD_PREVENT_TRY_CATCH, "true")
	spec.Setenv(envvars.GLIBCXX_FORCE_NEW, "1")
	spec.Setenv(envvars.LD_LIBRARY_PATH, vespa.FindHome()+"/lib64:/opt/vespa-deps/lib64")
	spec.Setenv(envvars.MALLOC_ARENA_MAX, "1")

	// fallback from old env.vars:
	spec.considerEnvFallback(envvars.VESPA_USE_HUGEPAGES_LIST, envvars.HUGEPAGES_LIST)
	spec.considerEnvFallback(envvars.VESPA_USE_MADVISE_LIST, envvars.MADVISE_LIST)
	spec.considerEnvFallback(envvars.VESPA_USE_VESPAMALLOC, envvars.VESPAMALLOC_LIST)
	spec.considerEnvFallback(envvars.VESPA_USE_VESPAMALLOC_D, envvars.VESPAMALLOCD_LIST)
	spec.considerEnvFallback(envvars.VESPA_USE_VESPAMALLOC_DST, envvars.VESPAMALLOCDST_LIST)
	spec.considerEnvFallback(envvars.VESPA_USE_NO_VESPAMALLOC, envvars.NO_VESPAMALLOC_LIST)
	// other fallbacks:
	spec.considerFallback(envvars.ROOT, vespa.FindHome())
	spec.considerFallback(envvars.VESPA_USER, vespa.FindVespaUser())
	spec.considerFallback(envvars.VESPA_USE_HUGEPAGES_LIST, "all")
	spec.considerFallback(envvars.VESPA_USE_VESPAMALLOC, "all")
	spec.considerFallback(envvars.VESPA_USE_NO_VESPAMALLOC, strings.Join([]string{
		"vespa-rpc-invoke",
		"vespa-get-config",
		"vespa-sentinel-cmd",
		"vespa-route",
		"vespa-proton-cmd",
		"vespa-configproxy-cmd",
		"vespa-config-status",
	}, " "))

}
