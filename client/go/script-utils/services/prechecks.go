// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/envvars"
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func commonPreChecks() (veHome, veHost string) {
	if doTrace := os.Getenv(envvars.TRACE_STARTUP); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv(envvars.DEBUG_STARTUP); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	_ = vespa.FindAndVerifyVespaHome()
	err := vespa.LoadDefaultEnv()
	if err != nil {
		panic(err)
	}
	veHome = vespa.FindAndVerifyVespaHome()
	veHost, err = vespa.FindOurHostname()
	if err != nil {
		trace.Warning("could not detect hostname:", err, "; using fallback:", veHost)
	}
	err = os.Chdir(veHome)
	if err != nil {
		util.JustExitWith(err)
	}
	return
}
