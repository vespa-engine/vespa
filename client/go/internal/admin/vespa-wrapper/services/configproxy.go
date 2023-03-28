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

	"github.com/vespa-engine/vespa/client/go/internal/admin/defaults"
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/jvm"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	CONFIGPROXY_PIDFILE = "var/run/configproxy.pid"
	PROXY_SERVICE_NAME  = "configproxy"
)

func JustRunConfigproxy() int {
	os.Setenv(envvars.VESPA_SERVICE_NAME, PROXY_SERVICE_NAME)
	commonPreChecks()
	vespa.CheckCorrectUser()
	configsources := defaults.VespaConfigserverRpcAddrs()
	if len(configsources) < 1 {
		util.JustExitMsg("could not find any configservers")
	}
	util.TuneResourceLimits()
	c := jvm.NewConfigProxyJvm(PROXY_SERVICE_NAME)
	userargs := os.Getenv(envvars.VESPA_CONFIGPROXY_JVMARGS)
	c.ConfigureOptions(configsources, userargs)
	c.Exec()
	// unreachable:
	return 1
}

func startProxyWithRunserver() {
	commonPreChecks()
	vespa.CheckCorrectUser()
	configsources := defaults.VespaConfigserverRpcAddrs()
	fmt.Printf(
		"Starting config proxy using %s as config source(s)\n",
		strings.Join(configsources, " and "))
	args := []string{
		"-r", "10",
		"-s", PROXY_SERVICE_NAME,
		"-p", CONFIGPROXY_PIDFILE,
		"--",
		"libexec/vespa/vespa-wrapper", "just-run-configproxy",
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
}

func waitForProxyResponse() bool {
	hname, _ := vespa.FindOurHostname()
	backtick := util.BackTicksWithStderr
	start := time.Now()
	fmt.Printf("Waiting for config proxy to start\n")
	for sleepcount := 0; sleepcount < 1800; sleepcount++ {
		time.Sleep(100 * time.Millisecond)
		got, err := os.ReadFile(CONFIGPROXY_PIDFILE)
		if err == nil {
			pid, err := strconv.Atoi(strings.TrimSpace(string(got)))
			if err == nil && pid > 0 {
				out, err := backtick.Run("vespa-ping-configproxy", "-s", hname)
				if err == nil {
					secs := time.Since(start).Seconds()
					fmt.Printf("config proxy started after %ds (runserver pid %d)\n", int(secs), pid)
					return true
				}
				if sleepcount%50 == 9 {
					trace.Warning("Could not ping configproxy:", err)
					if sleepcount%500 == 59 {
						secs := time.Since(start).Seconds()
						trace.Warning("ping output after", int(secs), "seconds:", strings.TrimSpace(out))
						logFile := defaults.VespaLogFile()
						cmd := fmt.Sprintf("tail -n 15 %s | vespa-logfmt -l all -N", logFile)
						out, err = backtick.Run("sh", "-c", cmd)
						fmt.Fprintf(os.Stderr, "tail of logfile: >>>\n%s<<<\n", out)
					}
				}
			} else {
				trace.Debug("bad contents (", string(got), ") in pid file", CONFIGPROXY_PIDFILE)
			}
		} else {
			trace.Debug("bad pid file", CONFIGPROXY_PIDFILE, err)
		}
	}
	secs := time.Since(start).Seconds()
	fmt.Fprintf(os.Stderr, "Config proxy still failed to start after %d seconds!\n", int(secs))
	got, err := os.ReadFile(CONFIGPROXY_PIDFILE)
	if err != nil {
		fmt.Fprintf(os.Stderr, "pid file %s was not created\n", CONFIGPROXY_PIDFILE)
		return false
	}
	gotpid := strings.TrimSpace(string(got))
	pid, err := strconv.Atoi(gotpid)
	if err != nil || pid < 1 {
		fmt.Fprintf(os.Stderr, "invalid pid '%s' in file %s\n", gotpid, CONFIGPROXY_PIDFILE)
		return false
	}
	out, err := backtick.Run("kill", "-0", gotpid)
	if err != nil {
		fmt.Fprintf(os.Stderr, "config proxy process `%s` has terminated: %s\n", gotpid, strings.TrimSpace(out))
		return false
	}
	out, err = backtick.Run("vespa-ping-configproxy", "-s", hname)
	fmt.Fprintf(os.Stderr, "failed to ping configproxy: %s\n", out)
	return false
}

func StartConfigproxy() int {
	commonPreChecks()
	vespa.MaybeSwitchUser("start-configproxy")
	startProxyWithRunserver()
	if waitForProxyResponse() {
		return 0
	}
	return 1
}

func stopProxyWithRunserver() {
	_, err := util.SystemCommand.Run("vespa-runserver",
		"-s", PROXY_SERVICE_NAME,
		"-p", CONFIGPROXY_PIDFILE, "-S")
	if err != nil {
		trace.Warning("Stopping sentinel:", err)
	}
}
