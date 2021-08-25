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
	statusCmd.AddCommand(statusQueryCmd)
	statusCmd.AddCommand(statusDocumentCmd)
	statusCmd.AddCommand(statusDeployCmd)
}

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "Verifies that a vespa target is ready to use (query by default)",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Query API")
	},
}

var statusQueryCmd = &cobra.Command{
	Use:   "query",
	Short: "Verifies that your Vespa query API container endpoint is ready [Default]",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Query API")
	},
}

var statusDocumentCmd = &cobra.Command{
	Use:   "document",
	Short: "Verifies that your Vespa document API container endpoint is ready [Default]",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(documentTarget(), "Document API")
	},
}

var statusDeployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Verifies that your Vespa deploy API config server endpoint is ready",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		status(deployTarget(), "Deploy API")
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
