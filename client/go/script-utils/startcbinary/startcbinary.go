// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"
	"os"
	"os/exec"

	"github.com/vespa-engine/vespa/client/go/trace"
)

func startCbinary(spec ProgSpec) bool {
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
	}
	return err == nil
}

func (spec *ProgSpec) run() error {
	prog := spec.Program + "-bin"
	args := spec.Args
	cmd := exec.Command(prog, args...)
	if spec.shouldUseValgrind {
		cmd.Path = spec.valgrindBinary()
		cmd.Args = spec.prependValgrind(prog, args)
	} else if spec.shouldUseNumaCtl {
		cmd.Path = spec.numaCtlBinary()
		cmd.Args = spec.prependNumaCtl(prog, args)
	}
	if spec.shouldUseVespaMalloc {
		spec.setenv("LD_PRELOAD", spec.vespaMallocPreload)
	}
	if len(spec.Env) > 0 {
		env := os.Environ()
		for k, v := range spec.Env {
			trace.Trace("add to environment:", k, "=", v)
			env = append(env, k+"="+v)
		}
		cmd.Env = env
	}
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	trace.Trace("run cmd", cmd)
	return cmd.Run()
}
