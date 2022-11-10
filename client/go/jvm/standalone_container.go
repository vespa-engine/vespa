// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/defaults"
	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	JAR_FOR_STANDALONE_CONTAINER = "standalone-container-jar-with-dependencies.jar"
)

type StandaloneContainer struct {
	containerBase
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

func (a *StandaloneContainer) configureOptions() {
	opts := a.jvmArgs
	opts.ConfigureCpuCount(0)
	opts.AddCommonXX()
	opts.AddOption("-XX:-OmitStackTraceInFastThrow")
	opts.AddCommonOpens()
	opts.AddCommonJdkProperties()
	a.addJdiscProperties()
	svcName := a.ServiceName()
	if svcName == "configserver" {
		RemoveStaleZkLocks(a)
		logsDir := defaults.UnderVespaHome("logs/vespa")
		zkLogFile := fmt.Sprintf("%s/zookeeper.%s", logsDir, svcName)
		opts.AddOption("-Dzookeeper_log_file_prefix=" + zkLogFile)
	}
}

func NewStandaloneContainer(svcName string) Container {
	var a StandaloneContainer
	a.serviceName = svcName
	a.jvmArgs = NewOptions(&a)
	a.configureOptions()
	return &a
}

func (a *StandaloneContainer) addJdiscProperties() {
	opts := a.JvmOptions()
	opts.AddCommonJdiscProperties()
	containerParentDir := defaults.UnderVespaHome("var/jdisc_container")
	bCacheParentDir := defaults.UnderVespaHome("var/vespa/bundlecache")
	svcName := a.ServiceName()
	bCacheDir := fmt.Sprintf("%s/%s", bCacheParentDir, svcName)
	propsFile := fmt.Sprintf("%s/%s.properties", containerParentDir, svcName)
	opts.fixSpec.FixDir(containerParentDir)
	opts.fixSpec.FixDir(bCacheParentDir)
	opts.fixSpec.FixDir(bCacheDir)
	trace.Trace("write props file:", propsFile)
	err := os.WriteFile(propsFile, selectedEnv(), 0600)
	if err != nil {
		util.JustExitWith(err)
	}
	opts.AddOption("-Djdisc.export.packages=")
	opts.AddOption("-Djdisc.config.file=" + propsFile)
	opts.AddOption("-Djdisc.cache.path=" + bCacheDir)
	opts.AddOption("-Djdisc.logger.tag=" + svcName)
}
