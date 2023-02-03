// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func RunApplicationContainer(extraArgs []string) int {
	if doTrace := os.Getenv(envvars.TRACE_JVM_STARTUP); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv(envvars.DEBUG_JVM_STARTUP); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	container := NewApplicationContainer(extraArgs)
	container.Exec()
	// unreachable:
	return 1
}

func NewApplicationContainer(extraArgs []string) Container {
	var a ApplicationContainer
	a.configId = os.Getenv(envvars.VESPA_CONFIG_ID)
	a.serviceName = os.Getenv(envvars.VESPA_SERVICE_NAME)
	a.jvmOpts = NewOptions(&a)
	a.configureOptions()
	for _, x := range extraArgs {
		a.JvmOptions().AddOption(x)
	}
	return &a
}
