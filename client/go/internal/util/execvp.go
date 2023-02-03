// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

//go:build !windows

package util

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"golang.org/x/sys/unix"
)

func findInPath(prog string) string {
	if strings.Contains(prog, "/") {
		return prog
	}
	path := strings.Split(os.Getenv(envvars.PATH), ":")
	for _, dir := range path {
		fn := dir + "/" + prog
		if IsExecutableFile(fn) {
			return fn
		}
	}
	return prog
}

func Execvp(prog string, argv []string) error {
	return Execvpe(prog, argv, os.Environ())
}

func Execvpe(prog string, argv []string, envv []string) error {
	prog = findInPath(prog)
	argv[0] = prog
	return Execve(prog, argv, envv)
}

func Execve(prog string, argv []string, envv []string) error {
	trace.Trace("run cmd:", strings.Join(argv, " "))
	err := unix.Exec(prog, argv, envv)
	return fmt.Errorf("cannot execute '%s': %v", prog, err)
}
