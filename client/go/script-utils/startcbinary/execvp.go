// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

//go:build !windows

package startcbinary

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
	"golang.org/x/sys/unix"
)

func findInPath(prog string) string {
	if strings.Contains(prog, "/") {
		return prog
	}
	path := strings.Split(os.Getenv(ENV_PATH), ":")
	for _, dir := range path {
		fn := dir + "/" + prog
		if util.IsRegularFile(fn) {
			return fn
		}
	}
	return prog
}

func myexecvp(prog string, argv []string, envv []string) error {
	trace.Trace("run cmd", strings.Join(argv, " "))
	prog = findInPath(prog)
	argv[0] = prog
	err := unix.Exec(prog, argv, envv)
	return fmt.Errorf("cannot execute '%s': %v", prog, err)
}
