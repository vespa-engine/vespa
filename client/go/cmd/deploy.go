// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
)

func init() {
	rootCmd.AddCommand(deployCmd)
	addTargetFlag(deployCmd)
}

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Deploys (prepares and activates) an application package",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		deploy(false, args)
	},
}

func deploy(prepare bool, args []string) {
	var application string
	if len(args) > 0 {
		application = args[0]
	}
	path, err := vespa.Deploy(prepare, application, deployTarget())
	if err != nil {
		log.Print(color.Red(err))
	} else {
		log.Print("Deployed ", color.Green(path), " successfully")
	}
}
