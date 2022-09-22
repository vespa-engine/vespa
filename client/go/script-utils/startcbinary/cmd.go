// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func Run(args []string) bool {
	trace.AdjustVerbosity(1)
	if len(args) < 1 {
		trace.Warning("missing program argument")
		return false
	}
	spec := ProgSpec{
		Program: args[0],
		Args:    args[1:],
	}
	spec.setup()
	vespa.LoadDefaultEnv()
	return startCbinary(spec)
}
