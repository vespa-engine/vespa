// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Entrypoint for Vespa CLI
// Author: bratseth

package main

import (
	"errors"
	"fmt"
	"os"

	"github.com/vespa-engine/vespa/client/go/internal/cli/cmd"
)

func fatal(status int, err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
	}
	os.Exit(status)
}

func main() {
	cli, err := cmd.New(os.Stdout, os.Stderr, os.Environ())
	if err != nil {
		fatal(1, err)
	}
	if err := cli.Run(); err != nil {
		var cliErr cmd.CLIError
		if errors.As(err, &cliErr) {
			fatal(cliErr.Status, nil)
		} else {
			fatal(1, nil)
		}
	}
}
