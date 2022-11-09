// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"github.com/vespa-engine/vespa/client/go/trace"
)

func (spec *Spec) ConfigureHugePages() {
	if spec.matchesListEnv(ENV_VESPA_USE_HUGEPAGES_LIST) {
		trace.Debug("setting", ENV_VESPA_USE_HUGEPAGES, "= 'yes'")
		spec.Setenv(ENV_VESPA_USE_HUGEPAGES, "yes")
	}
}
