// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(statusCmd)
	statusCmd.AddCommand(statusQueryCmd)
	statusCmd.AddCommand(statusDocumentCmd)
	statusCmd.AddCommand(statusDeployCmd)
}

var statusCmd = &cobra.Command{
	Use:               "status",
	Short:             "Verify that a service is ready to use (query by default)",
	Example:           `$ vespa status query`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		return waitForService("query", 0)
	},
}

var statusQueryCmd = &cobra.Command{
	Use:               "query",
	Short:             "Verify that the query service is ready to use (default)",
	Example:           `$ vespa status query`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(0),
	RunE: func(cmd *cobra.Command, args []string) error {
		return waitForService("query", 0)
	},
}

var statusDocumentCmd = &cobra.Command{
	Use:               "document",
	Short:             "Verify that the document service is ready to use",
	Example:           `$ vespa status document`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(0),
	RunE: func(cmd *cobra.Command, args []string) error {
		return waitForService("document", 0)
	},
}

var statusDeployCmd = &cobra.Command{
	Use:               "deploy",
	Short:             "Verify that the deploy service is ready to use",
	Example:           `$ vespa status deploy`,
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(0),
	RunE: func(cmd *cobra.Command, args []string) error {
		return waitForService("deploy", 0)
	},
}
