// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func (spec *Spec) Run() error {
	prog := spec.Program
	args := spec.Args
	if spec.shouldUseValgrind {
		args = spec.prependValgrind(args)
		prog = args[0]
	} else if spec.shouldUseNumaCtl {
		args = spec.prependNumaCtl(args)
		prog = args[0]
	}
	if spec.shouldUseVespaMalloc {
		spec.Setenv(envvars.LD_PRELOAD, spec.vespaMallocPreload)
	}
	envv := spec.EffectiveEnv()
	return util.Execvpe(prog, args, envv)
}
