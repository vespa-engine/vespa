// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package startcbinary

import (
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/admin/prog"
)

func startCbinary(spec *prog.Spec) int {
	configureCommonEnv(spec)
	configurePath(spec)
	configureTuning()
	spec.ConfigureValgrind()
	spec.ConfigureNumaCtl()
	spec.ConfigureHugePages()
	spec.ConfigureUseMadvise()
	spec.ConfigureVespaMalloc()
	err := spec.Run()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	} else {
		return 0
	}
}
