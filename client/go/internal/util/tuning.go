// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func OptionallyReduceTimerFrequency() {
	if os.Getenv(envvars.VESPA_TIMER_HZ) == "" {
		backticks := BackTicksIgnoreStderr
		out, _ := backticks.Run("uname", "-r")
		if strings.Contains(out, "linuxkit") {
			if os.Getenv(envvars.VESPA_TIMER_HZ) != "100" {
				trace.Trace(
					"Running docker on macos.",
					"Reducing base frequency from 1000hz to 100hz due to high cost of sampling time.",
					"This will reduce timeout accuracy.")
			}
			os.Setenv(envvars.VESPA_TIMER_HZ, "100")
		}
	}
}

func TuneResourceLimits() {
	var numfiles uint64 = 262144
	var numprocs uint64 = 409600
	if env := os.Getenv(envvars.FILE_DESCRIPTOR_LIMIT); env != "" {
		n, err := strconv.Atoi(env)
		if err != nil {
			numfiles = uint64(n)
		}
	}
	if env := os.Getenv(envvars.NUM_PROCESSES_LIMIT); env != "" {
		n, err := strconv.Atoi(env)
		if err != nil {
			numprocs = uint64(n)
		}
	}
	SetResourceLimit(RLIMIT_CORE, NO_RLIMIT)
	SetResourceLimit(RLIMIT_NOFILE, numfiles)
	SetResourceLimit(RLIMIT_NPROC, numprocs)
}
