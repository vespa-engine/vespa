// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
// author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
)



var (
	// flags
	DeployTarget string
	ContainerTarget string

	rootCmd = &cobra.Command{
		Use:   "vespa",
		Short: "A command-line tool for working with Vespa instances",
		Long: `TO
DO`,
	}
)

func init() {
	rootCmd.PersistentFlags().StringVarP(&ContainerTarget, "container-target", "c", "http://127.0.0.1:8080", "The target of commands to application containers")
	rootCmd.PersistentFlags().StringVarP(&DeployTarget, "deploy-target", "d", "http://127.0.0.1:19071", "The target of deploy commands.")
}

// Execute executes the root command.
func Execute() error {
	return rootCmd.Execute()
}

