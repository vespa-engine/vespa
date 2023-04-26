// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func configureTuning() {
	util.OptionallyReduceTimerFrequency()
	util.TuneResourceLimits()
}
