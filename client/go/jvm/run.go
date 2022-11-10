// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/prog"
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
	configId := os.Getenv(util.ENV_CONFIG_ID)
	serviceName := os.Getenv(util.ENV_SERVICE_NAME)
	a := ApplicationContainer{
		configId:    configId,
		serviceName: serviceName,
	}
	opts := NewOptions(&a)
	a.addJvmArgs(&opts)
	for _, x := range extraArgs {
		opts.AddOption(x)
	}
	return &a
}

func (a *ApplicationContainer) Exec() {
	argv := make([]string, 0, 100)
	argv = append(argv, "java")
	for _, x := range a.jvmArgs.Args() {
		argv = append(argv, x)
	}
	p := prog.NewSpec(argv)
	p.ConfigureNumaCtl()
	exportEnvSettings(a, p)
	err := p.Run()
	util.JustExitWith(err)
}
