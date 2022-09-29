// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

//go:build windows

package startcbinary

import (
	"fmt"
)

func myexecvp(prog string, args []string, envv []string) error {
	return fmt.Errorf("cannot execvp: %s", prog)
}
