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

