// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

//go:build windows

package util

import (
	"fmt"
)

func Execvp(prog string, argv []string) error {
	return fmt.Errorf("cannot execvp on windows: %s", prog)
}

func Execvpe(prog string, argv []string, envv []string) error {
	return fmt.Errorf("cannot execvp on windows: %s", prog)
}

func Execve(prog string, argv []string, envv []string) error {
	return fmt.Errorf("cannot execvp on windows: %s", prog)
}
