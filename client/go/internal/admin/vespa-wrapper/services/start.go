// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func StartServices() int {
	veHome, _ := commonPreChecks()
	vespa.CheckCorrectUser()
	trace.Debug("running as correct user")
	exportSettings(veHome)
	if vespa.HasOnlyIpV6() {
		os.Setenv(envvars.VESPA_ONLY_IP_V6_NETWORKING, "true")
	}
	startProxyWithRunserver()
	if waitForProxyResponse() {
		startSentinelWithRunserver()
		if waitForSentinelPid() {
			return 0
		}
	}
	return 1
}

func checkjava() {
	backticks := osutil.BackTicksWithStderr
	out, err := backticks.Run("java", "-version")
	if err != nil {
		trace.Warning("cannot run 'java -version'")
		osutil.ExitErr(err)
	}
	if !strings.Contains(out, "64-Bit Server VM") {
		osutil.ExitErr(fmt.Errorf("java must invoke the 64-bit Java VM, but -version says:\n%s\n", out))
	}
}

func runvalidation() {
	// not implemented
}

func VespaStartServices() int {
	home, host := commonPreChecks()
	trace.Debug("common prechecks ok, running in", home, "on", host)
	vespa.RunPreStart()
	trace.Debug("prestart ok")
	osutil.TuneResourceLimits()
	increase_vm_max_map_count()
	trace.Debug("resource limits ok")
	checkjava()
	trace.Debug("java ok")
	runvalidation()
	enable_transparent_hugepages_with_background_compaction()
	disable_vm_zone_reclaim_mode()
	drop_caches()
	err := vespa.MaybeSwitchUser("start-services")
	if err != nil {
		osutil.ExitErr(err)
	}
	return StartServices()
}
