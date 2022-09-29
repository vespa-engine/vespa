// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

func (spec *ProgSpec) optionallyReduceBaseFrequency() {
	if spec.getenv(ENV_VESPA_TIMER_HZ) == "" {
		backticks := util.BackTicksIgnoreStderr
		out, _ := backticks.Run("uname", "-r")
		if strings.Contains(out, "linuxkit") {
			trace.Trace("Running docker on macos. Reducing base frequency from 1000hz to 100hz due to high cost of sampling time. This will reduce timeout accuracy.")
			spec.setenv(ENV_VESPA_TIMER_HZ, "100")
		}
	}
}

func getThpSizeMb() int {
	const fn = "/sys/kernel/mm/transparent_hugepage/hpage_pmd_size"
	thp_size := 2
	line, err := os.ReadFile(fn)
	if err == nil {
		chomped := strings.TrimSuffix(string(line), "\n")
		number, err := strconv.Atoi(chomped)
		if err == nil {
			thp_size = number / (1024 * 1024)
			trace.Trace("thp_size", chomped, "=>", thp_size)
		} else {
			trace.Trace("no thp_size:", err)
		}
	} else {
		trace.Trace("no thp_size:", err)
	}
	return thp_size
}

func (spec *ProgSpec) configureTuning() {
	spec.optionallyReduceBaseFrequency()
}
