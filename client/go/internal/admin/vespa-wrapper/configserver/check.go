// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package configserver

import (
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func checkIsConfigserver(myname string) {
	onlyHosts := defaults.VespaConfigserverHosts()
	for _, hn := range onlyHosts {
		if hn == "localhost" || hn == myname {
			trace.Debug("should run configserver:", hn)
			return
		}
	}
	trace.Warning("only these hosts should run a config server:", onlyHosts)
	util.JustExitMsg(fmt.Sprintf("this host [%s] should not run a config server", myname))
}

type pingChecker struct {
	hostNames []string
	lastErr   map[string]error
	lastOut   map[string]string
	backticks util.BackTicks
}

func (pc *pingChecker) ping(hostname string) bool {
	out, err := pc.backticks.Run("ping", "-c", "1", "-q", hostname)
	pc.lastErr[hostname] = err
	pc.lastOut[hostname] = strings.TrimSuffix(out, "\n")
	return err == nil
}

func (pc *pingChecker) pingAll() {
	for _, hn := range pc.hostNames {
		pc.ping(hn)
	}
}

func (pc *pingChecker) countOk() int {
	isOk := 0
	for _, err := range pc.lastErr {
		if err == nil {
			isOk++
		}
	}
	return isOk
}

func (pc *pingChecker) requiredOk() int {
	return len(pc.hostNames)/2 + 1
}

func (pc *pingChecker) printErrors() {
	for hn, err := range pc.lastErr {
		if err != nil {
			out := pc.lastOut[hn]
			trace.Warning("failed to 'ping' host:", hn, "=>", err, "command output:", out)
		}
	}
}

func waitForDnsResolving() {
	onlyHosts := defaults.VespaConfigserverHosts()
	if len(onlyHosts) < 2 {
		// no wait in single-node case
		return
	}
	if os.Getenv(envvars.VESPA_SKIP_PING) != "" {
		trace.Debug("skipping DNS resolution check")
		return
	}
	helper := pingChecker{
		hostNames: onlyHosts,
		lastErr:   make(map[string]error),
		lastOut:   make(map[string]string),
		backticks: util.BackTicksWithStderr,
	}
	myname, _ := vespa.FindOurHostname()
	if !helper.ping(myname) {
		trace.Warning("self-ping failed, consider skipping this check")
	}
	trace.Debug("check DNS resolution, require", helper.requiredOk(), "OK answers")
	for i := 0; i < 180; i++ {
		helper.pingAll()
		isOk := helper.countOk()
		if isOk >= helper.requiredOk() {
			if i > 2 || isOk < len(onlyHosts) {
				trace.Info("successful 'ping' of", isOk, "configservers after", i, "retries")
			}
			helper.printErrors()
			return
		}
		if i%10 == 2 {
			trace.Warning("waiting for successful 'ping' of configservers", onlyHosts)
		}
		if i%40 == 3 {
			helper.printErrors()
		}
		if i == 2 {
			trace.Warning(fmt.Sprintf("set %s=true in environment to skip this check", envvars.VESPA_SKIP_PING))
		}
		time.Sleep(1000 * time.Millisecond)
	}
	util.JustExitMsg("Giving up waiting for working 'ping' of enough configservers")
}
