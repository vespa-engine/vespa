// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/util"
)

func startCbinary(spec *ProgSpec) int {
	spec.configureCommonEnv()
	spec.configurePath()
	spec.configureTuning()
	spec.configureValgrind()
	spec.configureNumaCtl()
	spec.configureHugePages()
	spec.configureUseMadvise()
	spec.configureVespaMalloc()
	err := spec.run()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	} else {
		return 0
	}
}

func (spec *ProgSpec) run() error {
	prog := spec.Program
	args := spec.Args
	if spec.shouldUseValgrind {
		args = spec.prependValgrind(args)
		prog = spec.valgrindBinary()
	} else if spec.shouldUseNumaCtl {
		args = spec.prependNumaCtl(args)
		prog = spec.numaCtlBinary()
	}
	if spec.shouldUseVespaMalloc {
		spec.setenv(ENV_LD_PRELOAD, spec.vespaMallocPreload)
	}
	envv := spec.effectiveEnv()
	return util.Execvpe(prog, args, envv)
}
