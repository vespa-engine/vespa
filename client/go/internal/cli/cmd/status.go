// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newStatusCmd(cli *CLI) *cobra.Command {
	var waitSecs int
	cmd := &cobra.Command{
		Use: "status",
		Aliases: []string{
			"status container",
			"status document", // TODO: Remove on Vespa 9
			"status query",    // TODO: Remove on Vespa 9
		},
		Short: "Verify that container service(s) are ready to use",
		Example: `$ vespa status
$ vespa status --cluster mycluster`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			cluster := cli.config.cluster()
			t, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			if cluster == "" {
				timeout := time.Duration(waitSecs) * time.Second
				services, err := t.ContainerServices(timeout)
				if err != nil {
					return err
				}
				if len(services) == 0 {
					return errHint(fmt.Errorf("no services exist"), "Deployment may not be ready yet", "Try 'vespa status deployment'")
				}
				for _, service := range services {
					if err := printServiceStatus(service, service.Wait(timeout), cli); err != nil {
						return err
					}
				}
				return nil
			} else {
				s, err := cli.service(t, cluster, 0)
				return printServiceStatus(s, err, cli)
			}
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func newStatusDeployCmd(cli *CLI) *cobra.Command {
	var waitSecs int
	cmd := &cobra.Command{
		Use:               "deploy",
		Short:             "Verify that the deploy service is ready to use",
		Example:           `$ vespa status deploy`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			t, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			s, err := t.DeployService(0)
			if err != nil {
				return err
			}
			return printServiceStatus(s, s.Wait(time.Duration(waitSecs)*time.Second), cli)
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func printServiceStatus(s *vespa.Service, waitErr error, cli *CLI) error {
	if waitErr != nil {
		return waitErr
	}
	desc := s.Description()
	desc = strings.ToUpper(string(desc[0])) + string(desc[1:])
	log.Print(desc, " at ", color.CyanString(s.BaseURL), " is ", color.GreenString("ready"))
	return nil
}
