// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

func RunApplicationContainer(extraArgs []string) int {
	if doTrace := os.Getenv("TRACE_JVM_STARTUP"); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv("DEBUG_JVM_STARTUP"); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	container := NewApplicationContainer(extraArgs)
	container.Exec()
	// unreachable:
	return 1
}

func NewApplicationContainer(extraArgs []string) Container {
	var a ApplicationContainer
	a.configId = os.Getenv(util.ENV_CONFIG_ID)
	a.serviceName = os.Getenv(util.ENV_SERVICE_NAME)
	a.jvmArgs = NewOptions(&a)
	a.configureOptions()
	for _, x := range extraArgs {
		a.JvmOptions().AddOption(x)
	}
	return &a
}
