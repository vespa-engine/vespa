// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
    "github.com/spf13/cobra"
    "github.com/vespa-engine/vespa/utils"
)

func init() {
    rootCmd.AddCommand(statusCmd)
    statusCmd.AddCommand(statusContainerCmd)
    statusCmd.AddCommand(statusConfigServerCmd)
}

var statusCmd = &cobra.Command{
    Use:   "status",
    Short: "Verifies that a vespa target is ready to use (container by default)",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status(getTarget(queryContext).query, "Container")
    },
}

var statusContainerCmd = &cobra.Command{
    Use:   "container",
    Short: "Verifies that your Vespa container endpoint is ready [Default]",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status(getTarget(queryContext).query, "Container")
    },
}

var statusConfigServerCmd = &cobra.Command{
    Use:   "config-server",
    Short: "Verifies that your Vespa config server endpoint is ready",
    Long:  `TODO`,
    Run: func(cmd *cobra.Command, args []string) {
        status(getTarget(deployContext).deploy, "Config server")
    },
}

func status(target string, description string) {
    path := "/ApplicationStatus"
    response := utils.HttpGet(target, path, description)
    if (response == nil) {
        return
    }
    defer response.Body.Close()

    if response.StatusCode != 200 {
        utils.Error(description, "at", target, "is not ready")
        utils.Detail("Response status:", response.Status)
    } else {
        utils.Success(description, "at", target, "is ready")
    }
}
