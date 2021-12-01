// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Cobra commands main file
// Author: bratseth

package main

import (
	"github.com/vespa-engine/vespa/client/go/cmd"
	"os"
)

func main() {
	if err := cmd.Execute(); err != nil {
		os.Exit(1)
	}
}
