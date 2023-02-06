// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func (spec *Spec) ConfigureHugePages() {
	if spec.matchesListEnv(envvars.VESPA_USE_HUGEPAGES_LIST) {
		trace.Debug("setting", envvars.VESPA_USE_HUGEPAGES, "= 'yes'")
		spec.Setenv(envvars.VESPA_USE_HUGEPAGES, "yes")
	}
}
