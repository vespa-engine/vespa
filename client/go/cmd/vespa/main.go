// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Cobra commands main file
// Author: bratseth

package main

import (
	"os"

	"github.com/vespa-engine/vespa/client/go/cmd"
)

func main() {
	if err := cmd.Execute(); err != nil {
		if cliErr, ok := err.(cmd.ErrCLI); ok {
			os.Exit(cliErr.Status)
		} else {
			os.Exit(1)
		}
	}
}
