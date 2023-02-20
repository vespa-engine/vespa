// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// for starting standalone jdisc containers
package standalone

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/jvm"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func commonPreChecks() {
	if doTrace := os.Getenv("TRACE_JVM_STARTUP"); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv("DEBUG_JVM_STARTUP"); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	veHome := vespa.FindAndVerifyVespaHome()
	err := os.Chdir(veHome)
	if err != nil {
		util.JustExitWith(err)
	}
	err = vespa.LoadDefaultEnv()
	if err != nil {
		util.JustExitWith(err)
	}
}

func StartStandaloneContainer(extraArgs []string) int {
	commonPreChecks()
	util.TuneResourceLimits()
	serviceName := os.Getenv("VESPA_SERVICE_NAME")
	if serviceName == "" {
		util.JustExitMsg("Missing service name, ensure VESPA_SERVICE_NAME is set in the environment")
	}
	c := jvm.NewStandaloneContainer(serviceName)
	jvmOpts := c.JvmOptions()
	jvmOpts.AddOption("-DOnnxBundleActivator.skip=true")
	for _, extra := range extraArgs {
		jvmOpts.AddOption(extra)
	}
	minFallback := jvm.MegaBytesOfMemory(128)
	maxFallback := jvm.MegaBytesOfMemory(2048)
	jvmOpts.AddDefaultHeapSizeArgs(minFallback, maxFallback)
	c.Exec()
	// unreachable:
	return 1
}
