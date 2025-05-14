// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Entrypoint for internal Vespa commands: vespa-logfmt, vespa-deploy etc.
// Author: arnej

package main

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/clusterstate"
	"github.com/vespa-engine/vespa/client/go/internal/admin/deploy"
	"github.com/vespa-engine/vespa/client/go/internal/admin/jvm"
	"github.com/vespa-engine/vespa/client/go/internal/admin/vespa-wrapper/configserver"
	"github.com/vespa-engine/vespa/client/go/internal/admin/vespa-wrapper/logfmt"
	"github.com/vespa-engine/vespa/client/go/internal/admin/vespa-wrapper/services"
	"github.com/vespa-engine/vespa/client/go/internal/admin/vespa-wrapper/standalone"
	"github.com/vespa-engine/vespa/client/go/internal/admin/vespa-wrapper/startcbinary"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const minRequiredMemoryInBytes = 4 * 1024 * 1024 * 1024

func basename(s string) string {
	parts := strings.Split(s, "/")
	return parts[len(parts)-1]
}

func main() {
	defer handleSimplePanic()

	verifyAvailableMemory()

	action := basename(os.Args[0])
	if action == "vespa-wrapper" && len(os.Args) > 1 {
		action = os.Args[1]
		os.Args = os.Args[1:]
	}
	if action == "vespa-logfmt" {
		// "vespa-logfmt" does not require verified VESPA_HOME
		logfmt.RunCmdLine()
		return
	}
	_ = vespa.FindAndVerifyVespaHome()
	switch action {
	case "vespa-stop-services":
		os.Exit(services.VespaStopServices())
	case "vespa-start-services":
		os.Exit(services.VespaStartServices())
	case "start-services":
		os.Exit(services.StartServices())
	case "just-run-configproxy":
		os.Exit(services.JustRunConfigproxy())
	case "vespa-start-configserver":
		os.Exit(configserver.StartConfigserverEtc())
	case "just-start-configserver":
		os.Exit(configserver.JustStartConfigserver())
	case "vespa-start-container-daemon":
		os.Exit(jvm.RunApplicationContainer(os.Args[1:]))
	case "run-standalone-container":
		os.Exit(standalone.StartStandaloneContainer(os.Args[1:]))
	case "start-c-binary":
		os.Exit(startcbinary.Run(os.Args[1:]))
	case "export-env":
		vespa.ExportDefaultEnvToSh()
	case "security-env", "vespa-security-env":
		vespa.ExportSecurityEnvToSh()
	case "ipv6-only":
		if vespa.HasOnlyIpV6() {
			os.Exit(0)
		} else {
			os.Exit(1)
		}
	case "detect-hostname":
		myName, err := vespa.FindOurHostname()
		fmt.Println(myName)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	case "vespa-deploy":
		cobra := deploy.NewDeployCmd()
		cobra.Execute()
	case "vespa-get-cluster-state":
		cobra := clusterstate.NewGetClusterStateCmd()
		cobra.Execute()
	case "vespa-get-node-state":
		cobra := clusterstate.NewGetNodeStateCmd()
		cobra.Execute()
	case "vespa-set-node-state":
		cobra := clusterstate.NewSetNodeStateCmd()
		cobra.Execute()
	default:
		if startcbinary.IsCandidate(os.Args[0]) {
			os.Exit(startcbinary.Run(os.Args))
		}
		fmt.Fprintf(os.Stderr, "unknown action '%s'\n", action)
		fmt.Fprintln(os.Stderr, "actions: export-env, ipv6-only, security-env, detect-hostname")
		fmt.Fprintln(os.Stderr, "(also: vespa-deploy, vespa-logfmt)")
	}
}

func verifyAvailableMemory() {
	if !hasContainerMemoryFiles() {
		return
	}

	if os.Getenv("VESPA_IGNORE_NOT_ENOUGH_MEMORY") != "" {
		fmt.Fprintln(os.Stderr, "Memory check disabled via VESPA_DISABLE_MEMORY_CHECK.")
		return
	}

	availableMemory, err := getMemoryLimitCgroupMax()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		osutil.ExitMsg("Available memory could not be obtained, " + err.Error())
	}

	if availableMemory < minRequiredMemoryInBytes {
		osutil.ExitMsg("Running the Vespa container image requires at least 4GB available memory." +
			" See the relevant docs (https://docs.vespa.ai/en/operations-selfhosted/docker-containers.html#memory) " +
			" or set VESPA_IGNORE_NOT_ENOUGH_MEMORY=true")
	}
}

func hasContainerMemoryFiles() bool {
	paths := []string{
		"/sys/fs/cgroup/memory.max", // cgroup v2
		"/proc/meminfo",             // host-level memory info
	}

	for _, path := range paths {
		if _, err := os.Stat(path); os.IsNotExist(err) {
			return false
		}
	}

	return true
}

func getMemoryLimitCgroupMax() (uint64, error) {
	data, err := os.ReadFile("/sys/fs/cgroup/memory.max")
	if err != nil {
		return 0, fmt.Errorf("failed to read memory.max: %w", err)
	}

	trimmed := strings.TrimSpace(string(data))
	if trimmed == "max" {
		// No memory limit enforced, use available system memory
		return getAvailableSystemMemory()
	}

	limit, err := strconv.ParseUint(trimmed, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("failed to parse memory limit: %w", err)
	}

	return limit, nil
}

func getAvailableSystemMemory() (uint64, error) {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return 0, fmt.Errorf("failed to open /proc/meminfo: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "MemAvailable:") {
			fields := strings.Fields(line)
			if len(fields) < 2 {
				return 0, fmt.Errorf("unexpected format in MemAvailable line: %s", line)
			}
			// MemAvailable is in kB
			kb, err := strconv.ParseUint(fields[1], 10, 64)
			if err != nil {
				return 0, fmt.Errorf("failed to parse memory value: %w", err)
			}
			return kb * 1024, nil // convert to bytes
		}
	}
	if err := scanner.Err(); err != nil {
		return 0, fmt.Errorf("scanner error: %w", err)
	}

	return 0, fmt.Errorf("MemAvailable not found in /proc/meminfo")
}

func handleSimplePanic() {
	if r := recover(); r != nil {
		if jee, ok := r.(*osutil.ExitError); ok {
			fmt.Fprintln(os.Stderr, jee)
			os.Exit(1)
		} else {
			panic(r)
		}
	}
}
