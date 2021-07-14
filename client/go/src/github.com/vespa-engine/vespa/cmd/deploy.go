// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
    "github.com/spf13/cobra"
    // "github.com/vespa-engine/vespa/utils"
)

func init() {
    rootCmd.AddCommand(deployCmd)
}

var deployCmd = &cobra.Command{
    Use:   "deploy",
    Short: "Deploys an application package",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        deploy()
    },
}

func deploy() {

}

