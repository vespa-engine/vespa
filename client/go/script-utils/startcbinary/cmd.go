// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func Run(args []string) int {
	trace.AdjustVerbosity(0)
	if len(args) < 1 {
		trace.Warning("missing program argument")
		return 1
	}
	spec := NewProgSpec(args)
	err := vespa.LoadDefaultEnv()
	if err != nil {
		util.JustExitWith(err)
	}
	hostname, err := vespa.FindOurHostname()
	if err != nil {
		trace.Warning("could not detect hostname:", err, "; using fallback:", hostname)
	}
	return startCbinary(spec)
}

func IsCandidate(program string) bool {
	binary := program + "-bin"
	if strings.Contains(binary, "/") {
		return util.IsRegularFile(binary)
	} else {
		path := strings.Split(os.Getenv(ENV_PATH), ":")
		for _, dir := range path {
			fn := dir + "/" + binary
			if util.IsRegularFile(fn) {
				return true
			}
		}
	}
	return false
}
