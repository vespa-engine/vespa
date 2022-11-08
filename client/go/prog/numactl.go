// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

const (
	NUMACTL_PROG = "numactl"
)

func (p *Spec) ConfigureNumaCtl() {
	p.shouldUseNumaCtl = false
	p.numaSocket = -1
	if p.Getenv(ENV_VESPA_NO_NUMACTL) != "" {
		return
	}
	backticks := util.BackTicksIgnoreStderr
	out, err := backticks.Run(NUMACTL_PROG, "--hardware")
	trace.Debug("numactl --hardware says:", out)
	if err != nil {
		trace.Trace("numactl error:", err)
		return
	}
	outfoo, errfoo := backticks.Run(NUMACTL_PROG, "--interleave", "all", "echo", "foo")
	if errfoo != nil {
		trace.Trace("cannot run with numactl:", errfoo)
		return
	}
	if outfoo != "foo\n" {
		trace.Trace("bad numactl output:", outfoo)
		return
	}
	p.shouldUseNumaCtl = true
	if affinity := p.Getenv(ENV_VESPA_AFFINITY_CPU_SOCKET); affinity != "" {
		wantSocket, _ := strconv.Atoi(affinity)
		trace.Debug("want socket:", wantSocket)
		parts := strings.Fields(out)
		for idx := 0; idx+2 < len(parts); idx++ {
			if parts[idx] == "available:" && parts[idx+2] == "nodes" {
				numSockets, _ := strconv.Atoi(parts[idx+1])
				trace.Debug("numSockets:", numSockets)
				if numSockets > 1 {
					p.numaSocket = (wantSocket % numSockets)
					return
				}
			}
		}
	}
}

func (p *Spec) prependNumaCtl(args []string) []string {
	result := make([]string, 0, 5+len(args))
	result = append(result, NUMACTL_PROG)
	if p.numaSocket >= 0 {
		result = append(result, fmt.Sprintf("--cpunodebind=%d", p.numaSocket))
		result = append(result, fmt.Sprintf("--membind=%d", p.numaSocket))
	} else {
		result = append(result, "--interleave")
		result = append(result, "all")
	}
	for _, arg := range args {
		result = append(result, arg)
	}
	return result
}
