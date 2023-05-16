// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/jvm"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	SERVICE_NAME = "configserver"
)

func commonPreChecks() (veHome string) {
	if doTrace := os.Getenv(envvars.TRACE_JVM_STARTUP); doTrace != "" {
		trace.AdjustVerbosity(1)
	}
	if doDebug := os.Getenv(envvars.DEBUG_JVM_STARTUP); doDebug != "" {
		trace.AdjustVerbosity(2)
	}
	_ = vespa.FindAndVerifyVespaHome()
	err := vespa.LoadDefaultEnv()
	if err != nil {
		panic(err)
	}
	veHome = vespa.FindAndVerifyVespaHome()
	veHost, e := vespa.FindOurHostname()
	if e != nil {
		trace.Warning("could not detect hostname:", err, "; using fallback:", veHost)
	}
	checkIsConfigserver(veHost)
	e = os.Chdir(veHome)
	if e != nil {
		util.JustExitWith(e)
	}
	return
}

func JustStartConfigserver() int {
	vespaHome := commonPreChecks()
	vespa.CheckCorrectUser()
	util.TuneResourceLimits()
	util.TuneLogging(SERVICE_NAME, "com.google.api.client.http.HttpTransport", "config=off")
	exportSettings(vespaHome)
	removeStaleZkLocks(vespaHome)
	c := jvm.NewStandaloneContainer(SERVICE_NAME)
	jvmOpts := c.JvmOptions()
	if extra := os.Getenv(envvars.VESPA_CONFIGSERVER_JVMARGS); extra != "" {
		jvmOpts.AddJvmArgsFromString(extra)
	}
	minFallback := jvm.MegaBytesOfMemory(128)
	maxFallback := jvm.MegaBytesOfMemory(2048)
	jvmOpts.AddDefaultHeapSizeArgs(minFallback, maxFallback)
	c.Exec()
	// unreachable:
	return 1
}

func runConfigserverWithRunserver() int {
	commonPreChecks()
	vespa.CheckCorrectUser()
	rs := RunServer{
		ServiceName: SERVICE_NAME,
		Args:        []string{"just-start-configserver"},
	}
	rs.Exec("libexec/vespa/vespa-wrapper")
	return 1
}

func StartConfigserverEtc() int {
	vespaHome := commonPreChecks()
	vespa.RunPreStart()
	util.TuneResourceLimits()
	fixSpec := makeFixSpec()
	fixDirsAndFiles(fixSpec)
	exportSettings(vespaHome)
	vespa.MaybeSwitchUser("vespa-start-configserver")
	maybeStartLogd()
	return runConfigserverWithRunserver()
}
