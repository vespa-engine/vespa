// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newApplicationCmd() *cobra.Command {
	cmd := &cobra.Command{
		Hidden: true,
		Use:    "application",
		Short:  "",
		Long:   ``,
		Example: `$ vespa application list -a <tenant>.IGNORED
$ vespa application show -a <tenant>.<application>`,
		DisableAutoGenTag: true,
		SilenceUsage:      false,
		Args:              cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}
	return cmd
}

func newApplicationListCmd(cli *CLI) *cobra.Command {
	var listAllApplications bool
	targetFlags := NewTargetFlagsWithCLI(cli)
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List all application for a given tenant",
		Long: `The tenant is provided by specifying -a <tenant>.ignored
The applications are listed without any extra information. In the format <tenant>.<application>.`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.targetWithFlags(targetOptions{noCertificate: true, supportedType: cloudTargetOnly}, targetFlags)
			if err != nil {
				return err
			}

			// Use TargetFlags application instead of config
			applicationString := targetFlags.Application()
			if applicationString == "" {
				return fmt.Errorf("no application specified")
			}
			appId, err := vespa.ApplicationFromString(applicationString)
			if err != nil {
				return err
			}
			applicationList, err := target.ListApplications(appId.Tenant, 30*time.Second)
			if err != nil {
				return err
			}

			seen := map[string]bool{}
			for _, app := range applicationList.Applications {
				appId := fmt.Sprintf("%s.%s", app.Tenant, app.Application)
				// skip application with multiple instances
				if seen[appId] {
					continue
				}
				if listAllApplications || activeApplication(vespa.ApplicationID{
					Tenant:      app.Tenant,
					Application: app.Application,
					Instance:    "",
				}, target) {
					fmt.Fprintf(cli.Stdout, "%s\n", appId)
					seen[appId] = true
				}
			}

			return nil
		},
	}
	cmd.PersistentFlags().BoolVarP(&listAllApplications, "list-all-applications", "A", false, "List all applications, not just the active ones")
	targetFlags.AddTargetFlag(cmd)
	targetFlags.AddApplicationFlag(cmd)
	return cmd
}

func activeApplication(id vespa.ApplicationID, target vespa.Target) bool {
	app, err := target.ShowApplicationInstance(id, 10*time.Second)
	if err != nil {
		return true // assume true
	} else {
		deployments := 0
		for _, instance := range app.Instances {
			deployments += len(instance.Deployments)
		}
		return deployments > 0
	}
}

func newApplicationShowCmd(cli *CLI) *cobra.Command {
	var format string
	var listAllInstances bool
	targetFlags := NewTargetFlagsWithCLI(cli)
	cmd := &cobra.Command{
		Use:               "show",
		Short:             "Show information about a given application",
		Long:              `Show the known instances of an application, and the Vespa Zones they are deployed in.`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Example: `$ vespa application show -a <tenant>.<application>
$ vespa application show -a <tenant>.<application> --format plain`,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.targetWithFlags(targetOptions{noCertificate: true, supportedType: cloudTargetOnly}, targetFlags)
			if err != nil {
				return err
			}
			if target.Type() != vespa.TargetCloud && target.Type() != vespa.TargetHosted {
				return fmt.Errorf("application show does not support %s target", target.Type())
			}

			// Use TargetFlags application instead of config
			applicationString := targetFlags.Application()
			if applicationString == "" {
				return fmt.Errorf("no application specified")
			}
			appId, err := vespa.ApplicationFromString(applicationString)
			if err != nil {
				return err
			}
			application, err := target.ShowApplicationInstance(appId, 30*time.Second)
			if err != nil {
				return err
			}
			if format == "plain" {
				for _, instance := range application.Instances {
					for _, deployment := range instance.Deployments {
						fmt.Fprintf(cli.Stdout, "%s.%s.%s %s.%s\n", appId.Tenant, appId.Application, instance.Instance, deployment.Environment, deployment.Region)
					}
				}
			} else {
				for _, instance := range application.Instances {
					if listAllInstances || len(instance.Deployments) > 0 {
						fmt.Fprintf(cli.Stdout, "%s.%s.%s:\n", appId.Tenant, appId.Application, instance.Instance)
						for _, deployment := range instance.Deployments {
							fmt.Fprintf(cli.Stdout, "  %s.%s\n", deployment.Environment, deployment.Region)
						}
					}
				}
			}
			return nil
		},
	}
	cmd.PersistentFlags().StringVarP(&format, "format", "", "human", "Output format. Must be 'human' (human-readable) or 'plain'")
	cmd.PersistentFlags().BoolVarP(&listAllInstances, "list-all-instances", "A", false, "List all instances, not just the active ones")
	targetFlags.AddTargetFlag(cmd)
	targetFlags.AddApplicationFlag(cmd)
	return cmd
}
