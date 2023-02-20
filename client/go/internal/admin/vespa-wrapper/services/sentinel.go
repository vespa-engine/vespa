// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package services

import (
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	SENTINEL_PIDFILE      = "var/run/sentinel.pid"
	SENTINEL_SERVICE_NAME = "config-sentinel"
)

func startSentinelWithRunserver() {
	_, veHost := commonPreChecks()
	vespa.CheckCorrectUser()
	os.Setenv(envvars.VESPA_SERVICE_NAME, SENTINEL_SERVICE_NAME)
	configId := fmt.Sprintf("hosts/%s", veHost)
	args := []string{
		"-r", "10",
		"-s", SENTINEL_SERVICE_NAME,
		"-p", SENTINEL_PIDFILE,
		"--",
		"sbin/vespa-config-sentinel",
		"-c", configId,
	}
	cmd := exec.Command("vespa-runserver", args...)
	cmd.Stdin = nil
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Start()
	p := cmd.Process
	if p != nil {
		p.Release()
	}
	os.Unsetenv(envvars.VESPA_SERVICE_NAME)
}

func waitForSentinelPid() bool {
	backtick := util.BackTicksWithStderr
	start := time.Now()
	for sleepcount := 0; sleepcount < 1000; sleepcount++ {
		time.Sleep(10 * time.Millisecond)
		got, err := os.ReadFile(CONFIGPROXY_PIDFILE)
		if err == nil {
			pid, err := strconv.Atoi(strings.TrimSpace(string(got)))
			if err == nil && pid > 0 {
				_, err := backtick.Run("kill", "-0", strconv.Itoa(pid))
				if err == nil {
					secs := int(time.Since(start).Seconds())
					if secs > 0 {
						fmt.Printf("config sentinel started after %d seconds\n", secs)
					}
					return true
				}
			}
		}
		if sleepcount%500 == 90 {
			trace.Warning("Waiting for sentinel to start")
		}
	}
	return false
}

func StartConfigSentinel() int {
	commonPreChecks()
	vespa.MaybeSwitchUser("start-config-sentinel")
	startSentinelWithRunserver()
	if waitForSentinelPid() {
		return 0
	}
	return 1
}

func stopSentinelWithRunserver() {
	_, err := util.SystemCommand.Run("vespa-runserver",
		"-s", SENTINEL_SERVICE_NAME,
		"-p", SENTINEL_PIDFILE, "-S")
	if err != nil {
		trace.Warning("Stopping sentinel:", err)
	}
}
