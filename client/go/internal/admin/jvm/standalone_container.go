// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
	"github.com/vespa-engine/vespa/client/go/internal/util"
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
	opts := a.jvmOpts
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
	a.jvmOpts = NewOptions(&a)
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
	a.propsFile = propsFile
	opts.AddOption("-Djdisc.export.packages=")
	opts.AddOption("-Djdisc.config.file=" + propsFile)
	opts.AddOption("-Djdisc.cache.path=" + bCacheDir)
	opts.AddOption("-Djdisc.logger.tag=" + svcName)
}

func (c *StandaloneContainer) exportExtraEnv(ps *prog.Spec) {
	vespaHome := defaults.VespaHome()
	app := fmt.Sprintf("%s/conf/%s-app", vespaHome, c.ServiceName())
	if util.IsDirectory(app) {
		ps.Setenv(envvars.STANDALONE_JDISC_APP_LOCATION, app)
	} else {
		util.JustExitMsg("standalone container requires an application directory, missing: " + app)
	}
}
