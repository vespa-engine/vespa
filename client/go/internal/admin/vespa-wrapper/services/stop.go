// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func VespaStopServices() int {
	if doTrace := os.Getenv(envvars.TRACE_STARTUP); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv(envvars.DEBUG_STARTUP); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	err := vespa.LoadDefaultEnv()
	if err != nil {
		util.JustExitWith(err)
	}
	err = vespa.MaybeSwitchUser("vespa-stop-services")
	if err != nil {
		util.JustExitWith(err)
	}
	vespa.CheckCorrectUser()
	trace.Debug("running as correct user")
	stopSentinelWithRunserver()
	stopProxyWithRunserver()
	return 0
}
