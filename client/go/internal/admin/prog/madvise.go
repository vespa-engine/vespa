// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func (spec *Spec) ConfigureUseMadvise() {
	limit := spec.valueFromListEnv(envvars.VESPA_USE_MADVISE_LIST)
	if limit != "" {
		trace.Trace("shall use madvise with limit", limit, "as set in", envvars.VESPA_USE_MADVISE_LIST)
		spec.Setenv(envvars.VESPA_MALLOC_MADVISE_LIMIT, limit)
		return
	}
}
