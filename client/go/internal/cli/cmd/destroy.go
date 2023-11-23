// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newDestroyCmd(cli *CLI) *cobra.Command {
	force := false
	cmd := &cobra.Command{
		Use:   "destroy",
		Short: "Remove a deployed Vespa application and its data",
		Long: `Remove a deployed Vespa application and its data.

This command removes the currently deployed application and permanently
deletes its data.

When run interactively, the command will prompt for confirmation before
removing the application. When run non-interactively, the command will refuse
to remove the application unless the --force option is given.

This command can only be used to remove non-production deployments. See
https://cloud.vespa.ai/en/deleting-applications for how to remove
production deployments. This command can only be used for deployments to
Vespa Cloud, for other systems destroy an application by cleaning up
containers in use by the application, see e.g
https://github.com/vespa-engine/sample-apps/tree/master/examples/operations/multinode-HA#clean-up-after-testing

`,
		Example: `$ vespa destroy
$ vespa destroy -a mytenant.myapp.myinstance
$ vespa destroy --force`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{supportedType: cloudTargetOnly})
			if err != nil {
				return err
			}
			description := target.Deployment().String()
			env := target.Deployment().Zone.Environment
			if env != "dev" && env != "perf" {
				return errHint(fmt.Errorf("cannot remove production %s", description), "See https://cloud.vespa.ai/en/deleting-applications")
			}
			ok := force
			if !ok {
				cli.printWarning(fmt.Sprintf("This operation will irrecoverably remove the %s and all of its data", color.RedString(description)))
				ok, _ = cli.confirm("Proceed with removal?", false)
			}
			if ok {
				err := vespa.Deactivate(vespa.DeploymentOptions{Target: target})
				if err == nil {
					cli.printSuccess(fmt.Sprintf("Removed %s", description))
				}
				return err
			}
			return fmt.Errorf("refusing to remove %s without confirmation", description)
		},
	}
	cmd.PersistentFlags().BoolVar(&force, "force", false, "Disable confirmation (default false)")
	return cmd
}
