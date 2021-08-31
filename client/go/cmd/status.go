// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"log"

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
	Short: "Verify that a Vespa target is ready to use (query by default)",
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Query API")
	},
}

var statusQueryCmd = &cobra.Command{
	Use:   "query",
	Short: "Verify that your Vespa query API container endpoint is ready [Default]",
	Args:  cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status(queryTarget(), "Query API")
	},
}

var statusDocumentCmd = &cobra.Command{
	Use:   "document",
	Short: "Verify that your Vespa document API container endpoint is ready [Default]",
	Args:  cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status(documentTarget(), "Document API")
	},
}

var statusDeployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Verify that your Vespa deploy API config server endpoint is ready",
	Args:  cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status(deployTarget(), "Deploy API")
	},
}

func status(target string, description string) {
	path := "/ApplicationStatus"
	response, err := util.HttpGet(target, path, description)
	if err != nil {
		log.Print(description, " at ", color.Cyan(target), " is ", color.Red("not ready"))
		log.Print(color.Brown(err))
		return
	}
	defer response.Body.Close()

	if response.StatusCode != 200 {
		log.Print(description, " at ", color.Cyan(target), " is ", color.Red("not ready"))
		log.Print(color.Brown(response.Status))
	} else {
		log.Print(description, " at ", color.Cyan(target), " is ", color.Green("ready"))
	}
}
