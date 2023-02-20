// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
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
	os.Unsetenv(envvars.LD_PRELOAD)
	spec.setenv(envvars.STD_THREAD_PREVENT_TRY_CATCH, "true")
	spec.setenv(envvars.GLIBCXX_FORCE_NEW, "1")
	spec.setenv(envvars.LD_LIBRARY_PATH, vespa.FindHome()+"/lib64:/opt/vespa-deps/lib64")
	spec.setenv(envvars.MALLOC_ARENA_MAX, "1")

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

func (spec *ProgSpec) configureHugePages() {
	if spec.matchesListEnv(envvars.VESPA_USE_HUGEPAGES_LIST) {
		spec.setenv(envvars.VESPA_USE_HUGEPAGES, "yes")
	}
}

func (spec *ProgSpec) configureUseMadvise() {
	limit := spec.valueFromListEnv(envvars.VESPA_USE_MADVISE_LIST)
	if limit != "" {
		trace.Trace("shall use madvise with limit", limit, "as set in", envvars.VESPA_USE_MADVISE_LIST)
		spec.setenv(envvars.VESPA_MALLOC_MADVISE_LIMIT, limit)
		return
	}
}

func (spec *ProgSpec) configurePath() {
	// Prefer newer gdb and pstack:
	spec.prependPath("/opt/rh/gcc-toolset-12/root/usr/bin")
	// Maven is needed for tester applications:
	spec.prependPath(vespa.FindHome() + "/local/maven/bin")
	spec.prependPath(vespa.FindHome() + "/bin64")
	spec.prependPath(vespa.FindHome() + "/bin")
	// how to find the "java" program?
	// should be available in $VESPA_HOME/bin or JAVA_HOME
	if javaHome := spec.getenv(envvars.JAVA_HOME); javaHome != "" {
		spec.prependPath(javaHome + "/bin")
	}
}
