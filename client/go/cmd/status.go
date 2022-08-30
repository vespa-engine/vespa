// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"fmt"
	"log"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func newStatusCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "status",
		Short:             "Verify that a service is ready to use (query by default)",
		Example:           `$ vespa status query`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return printServiceStatus(cli, vespa.QueryService)
		},
	}
}

func newStatusQueryCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "query",
		Short:             "Verify that the query service is ready to use (default)",
		Example:           `$ vespa status query`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			return printServiceStatus(cli, vespa.QueryService)
		},
	}
}

func newStatusDocumentCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "document",
		Short:             "Verify that the document service is ready to use",
		Example:           `$ vespa status document`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			return printServiceStatus(cli, vespa.DocumentService)
		},
	}
}

func newStatusDeployCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "deploy",
		Short:             "Verify that the deploy service is ready to use",
		Example:           `$ vespa status deploy`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			return printServiceStatus(cli, vespa.DeployService)
		},
	}
}

func printServiceStatus(cli *CLI, name string) error {
	t, err := cli.target(targetOptions{})
	if err != nil {
		return err
	}
	cluster := cli.config.cluster()
	s, err := cli.service(t, name, 0, cluster)
	if err != nil {
		return err
	}
	timeout, err := cli.config.timeout()
	if err != nil {
		return err
	}
	status, err := s.Wait(timeout)
	clusterPart := ""
	if cluster != "" {
		clusterPart = fmt.Sprintf(" named %s", color.CyanString(cluster))
	}
	if status/100 == 2 {
		log.Print(s.Description(), clusterPart, " at ", color.CyanString(s.BaseURL), " is ", color.GreenString("ready"))
	} else {
		if err == nil {
			err = fmt.Errorf("status %d", status)
		}
		return fmt.Errorf("%s%s at %s is %s: %w", s.Description(), clusterPart, color.CyanString(s.BaseURL), color.RedString("not ready"), err)
	}
	return nil
}
