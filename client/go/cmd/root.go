// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
// author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
)

var (
	// flags
	// TODO: add timeout flag
	// TODO: add flag to show http request made
	targetArgument string

	rootCmd = &cobra.Command{
		Use:   "vespa",
		Short: "A command-line tool for working with Vespa instances",
		Long: `TO
DO`,
	}
)

func init() {
	cobra.OnInitialize(readConfig)
	rootCmd.PersistentFlags().StringVarP(&targetArgument, "target", "t", "local", "The name or URL of the recipient of this command")
}

// Execute executes the root command.
func Execute() error {
	err := rootCmd.Execute()
	return err
}
