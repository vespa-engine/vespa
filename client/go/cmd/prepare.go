// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa prepare command
// Author: bratseth

package cmd

import (
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(prepareCmd)
	addTargetFlag(prepareCmd)
}

// TODO: Implement and test

var prepareCmd = &cobra.Command{
	Use:   "prepare",
	Short: "Prepares an application package for activation",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		deploy(true, args)
	},
}
