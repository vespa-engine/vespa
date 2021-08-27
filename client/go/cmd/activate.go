// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa activate command
// Author: bratseth

package cmd

import (
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(activateCmd)
	addTargetFlag(activateCmd)
}

// TODO: Implement and test

var activateCmd = &cobra.Command{
	Use:   "activate",
	Short: "Activates (deploys) the previously prepared application package",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		deploy(true, nil)
	},
}
