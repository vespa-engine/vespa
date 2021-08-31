// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
// author: bratseth

package cmd

import (
	"log"
	"os"

	"github.com/logrusorgru/aurora"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
	"github.com/spf13/cobra"
)

var (
	// global flags
	// TODO: add timeout flag
	// TODO: add flag to show http request made

	rootCmd = &cobra.Command{
		Use:   "vespa",
		Short: "A command-line tool for working with Vespa instances",
		Long: `TO
DO`,
	}

	color aurora.Aurora
)

func configureLogger() {
	color = aurora.NewAurora(isatty.IsTerminal(os.Stdout.Fd()))
	log.SetFlags(0) // No timestamps
	log.SetOutput(colorable.NewColorableStdout())
}

func init() {
	configureLogger()
	cobra.OnInitialize(readConfig)
}

// Execute executes the root command.
func Execute() error { return rootCmd.Execute() }
