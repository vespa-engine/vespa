// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/defaults"
)

const (
	JAR_FOR_STANDALONE_CONTAINER = "standalone-container-jar-with-dependencies.jar"
)

type StandaloneContainer struct {
	serviceName string
}

func (a *StandaloneContainer) ArgForMain() string {
	return JAR_FOR_STANDALONE_CONTAINER
}

func (a *StandaloneContainer) ServiceName() string {
	return a.serviceName
}

func (a *StandaloneContainer) ConfigId() string {
	return ""
}

func (a *StandaloneContainer) addJvmArgs(opts *Options) {
	opts.AddCommonXX()
	opts.AddOption("-XX:-OmitStackTraceInFastThrow")
	opts.AddCommonOpens()
	opts.AddCommonJdkProperties()
	svcName := a.ServiceName()
	if svcName == "configserver" {
		RemoveStaleZkLocks(a)
		logsDir := defaults.UnderVespaHome("logs/vespa")
		zkLogFile := fmt.Sprintf("%s/zookeeper.%s", logsDir, svcName)
		opts.AddOption("-Dzookeeper_log_file_prefix=" + zkLogFile)
	}
	panic("not finished yet")
}

func (a *StandaloneContainer) Exec() {
	panic("not implemented yet")
}

func NewStandaloneContainer(svcName string) Container {
	a := StandaloneContainer{
		serviceName: svcName,
	}
	return &a
}
