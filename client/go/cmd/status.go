// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
)

func init() {
	rootCmd.AddCommand(statusCmd)
	statusCmd.AddCommand(statusContainerCmd)
	statusCmd.AddCommand(statusConfigServerCmd)
}

// TODO: Use deploy, query and document instead of container and config-server

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Verifies that a vespa target is ready to use (container by default)",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Container")
	},
}

var statusContainerCmd = &cobra.Command{
	Use:   "container",
	Short: "Verifies that your Vespa container endpoint is ready [Default]",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Container")
	},
}

var statusConfigServerCmd = &cobra.Command{
	Use:   "config-server",
	Short: "Verifies that your Vespa config server endpoint is ready",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(deployTarget(), "Config server")
	},
}

func status(target string, description string) {
	path := "/ApplicationStatus"
	response := util.HttpGet(target, path, description)
	if response == nil {
		return
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		util.Error(description, "at", target, "is not ready")
		util.Detail(response.Status)
	} else {
		util.Success(description, "at", target, "is ready")
	}
}
