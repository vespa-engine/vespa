// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
)

func init() {
	rootCmd.AddCommand(deployCmd)
}

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Deploys (prepares and activates) an application package",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) == 0 {
			vespa.Deploy(false, "", deployTarget())
		} else {
			vespa.Deploy(false, args[0], deployTarget())
		}
	},
}
