// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa prepare command
// Author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/vespa"
)

func init() {
    rootCmd.AddCommand(prepareCmd)
}

// TODO: Implement and test

var prepareCmd = &cobra.Command{
    Use:   "prepare",
    Short: "Prepares an application package for activation",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        if len(args) == 0 {
            vespa.Deploy(true, "", deployTarget())
        } else {
            vespa.Deploy(true, args[0], deployTarget())
        }
    },
}
