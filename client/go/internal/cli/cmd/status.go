// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa status command
// author: bratseth

package cmd

import (
	"fmt"
	"log"
	"strconv"
	"strings"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newStatusCmd(cli *CLI) *cobra.Command {
	var (
		waitSecs int
		format   string
	)
	cmd := &cobra.Command{
		Use: "status",
		Aliases: []string{
			"status container",
			"status document", // TODO: Remove on Vespa 9
			"status query",    // TODO: Remove on Vespa 9
		},
		Short: "Show Vespa endpoints and status",
		Long: `Show Vespa endpoints and status.

This command shows the current endpoints, and their status, of a deployed Vespa
application.`,
		Example: `$ vespa status
$ vespa status --cluster mycluster
$ vespa status --cluster mycluster --wait 600
$ vepsa status --format plain --cluster mycluster`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			cluster := cli.config.cluster()
			t, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			if err := verifyFormat(format); err != nil {
				return err
			}
			waiter := cli.waiter(time.Duration(waitSecs) * time.Second)
			var failingContainers []*vespa.Service
			if cluster == "" {
				services, err := waiter.Services(t)
				if err != nil {
					return err
				}
				if len(services) == 0 {
					return errHint(fmt.Errorf("no services exist"), "Deployment may not be ready yet", "Try 'vespa status deployment'")
				}
				for _, s := range services {
					if !printServiceStatus(s, format, waiter, cli) {
						failingContainers = append(failingContainers, s)
					}
				}
			} else {
				s, err := waiter.Service(t, cluster)
				if err != nil {
					return err
				}
				if !printServiceStatus(s, format, waiter, cli) {
					failingContainers = append(failingContainers, s)
				}
			}
			return failingServicesErr(failingContainers...)
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	cmd.PersistentFlags().StringVarP(&format, "format", "", "human", "Output format. Must be 'human' (human-readable) or 'plain' (cluster URL only)")
	return cmd
}

func verifyFormat(format string) error {
	switch format {
	case "human", "plain":
		return nil
	default:
		return fmt.Errorf("invalid format: %s", format)
	}
}

func failingServicesErr(services ...*vespa.Service) error {
	if len(services) == 0 {
		return nil
	}
	var nameOrURL []string
	for _, s := range services {
		if s.Name != "" {
			nameOrURL = append(nameOrURL, s.Name)
		} else {
			nameOrURL = append(nameOrURL, s.BaseURL)
		}
	}
	return fmt.Errorf("services not ready: %s", strings.Join(nameOrURL, ", "))
}

func newStatusDeployCmd(cli *CLI) *cobra.Command {
	var (
		waitSecs int
		format   string
	)
	cmd := &cobra.Command{
		Use:               "deploy",
		Short:             "Show status of the Vespa deploy service",
		Example:           `$ vespa status deploy`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			t, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			if err := verifyFormat(format); err != nil {
				return err
			}
			waiter := cli.waiter(time.Duration(waitSecs) * time.Second)
			s, err := waiter.DeployService(t)
			if err != nil {
				return err
			}
			if !printServiceStatus(s, format, waiter, cli) {
				return failingServicesErr(s)
			}
			return nil
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	cmd.PersistentFlags().StringVarP(&format, "format", "", "human", "Output format. Must be 'human' (human-readable text) or 'plain' (cluster URL only)")
	return cmd
}

func newStatusDeploymentCmd(cli *CLI) *cobra.Command {
	var waitSecs int
	cmd := &cobra.Command{
		Use:   "deployment",
		Short: "Show status of a Vespa deployment",
		Long: `Show status of a Vespa deployment.

This commands shows whether a Vespa deployment has converged on the latest run
 (Vespa Cloud) or config generation (self-hosted). If an argument is given,
show the convergence status of that particular run or generation.
`,
		Example: `$ vespa status deployment
$ vespa status deployment -t cloud [run-id]
$ vespa status deployment -t local [session-id]
$ vespa status deployment -t local [session-id] --wait 600
`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			wantedID := vespa.LatestDeployment
			if len(args) > 0 {
				n, err := strconv.ParseInt(args[0], 10, 64)
				if err != nil {
					return fmt.Errorf("invalid id: %s: %w", args[0], err)
				}
				wantedID = n
			}
			t, err := cli.target(targetOptions{logLevel: "none"})
			if err != nil {
				return err
			}
			waiter := cli.waiter(time.Duration(waitSecs) * time.Second)
			id, err := waiter.Deployment(t, wantedID)
			if err != nil {
				return err
			}
			if t.IsCloud() {
				log.Printf("Deployment run %s has completed", color.CyanString(strconv.FormatInt(id, 10)))
				log.Printf("See %s for more details", color.CyanString(t.Deployment().System.ConsoleRunURL(t.Deployment(), id)))
			} else {
				log.Printf("Deployment is %s on config generation %s", color.GreenString("ready"), color.CyanString(strconv.FormatInt(id, 10)))
			}
			return nil
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func printServiceStatus(s *vespa.Service, format string, waiter *Waiter, cli *CLI) bool {
	err := s.Wait(waiter.Timeout)
	var sb strings.Builder
	switch format {
	case "human":
		desc := s.Description()
		desc = strings.ToUpper(string(desc[0])) + string(desc[1:])
		sb.WriteString(fmt.Sprintf("%s at %s is ", desc, color.CyanString(s.BaseURL)))
		if err == nil {
			sb.WriteString(color.GreenString("ready"))
		} else {
			sb.WriteString(color.RedString("not ready"))
			sb.WriteString(": ")
			sb.WriteString(err.Error())
		}
	case "plain":
		sb.WriteString(s.BaseURL)
	default:
		panic("invalid format: " + format)
	}
	fmt.Fprintln(cli.Stdout, sb.String())
	return err == nil
}
