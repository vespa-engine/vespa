// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"fmt"
	"log"
	"time"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(statusCmd)
	statusCmd.AddCommand(statusQueryCmd)
	statusCmd.AddCommand(statusDocumentCmd)
	statusCmd.AddCommand(statusDeployCmd)
}

var statusCmd = &cobra.Command{
	Use:     "status",
	Short:   "Verify that a service is ready to use (query by default)",
	Example: `$ vespa status query`,
	Args:    cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		status("query", "Query API")
	},
}

var statusQueryCmd = &cobra.Command{
	Use:     "query",
	Short:   "Verify that the query service is ready to use (default)",
	Example: `$ vespa status query`,
	Args:    cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status("query", "Query API")
	},
}

var statusDocumentCmd = &cobra.Command{
	Use:     "document",
	Short:   "Verify that the document service is ready to use",
	Example: `$ vespa status document`,
	Args:    cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status("document", "Document API")
	},
}

var statusDeployCmd = &cobra.Command{
	Use:     "deploy",
	Short:   "Verify that the deploy service is ready to use",
	Example: `$ vespa status deploy`,
	Args:    cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		status("deploy", "Deploy API")
	},
}

func status(service string, description string) {
	s := getService(service)
	timeout := time.Duration(waitSecsArg) * time.Second
	if timeout > 0 {
		log.Printf("Waiting %d %s for service to become ready ...", color.Cyan(waitSecsArg), color.Cyan("seconds"))
	}
	status, err := s.Wait(timeout)
	if status/100 == 2 {
		log.Print(description, " at ", color.Cyan(s.BaseURL), " is ", color.Green("ready"))
	} else {
		log.Print(description, " at ", color.Cyan(s.BaseURL), " is ", color.Red("not ready"))
		if err == nil {
			log.Print(color.Yellow(fmt.Sprintf("Status %d", status)))
		} else {
			log.Print(color.Yellow(err))
		}
	}
}
