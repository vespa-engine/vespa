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
		Short: "Remove a deployed application and its data",
		Long: `Remove a deployed application and its data.

This command removes the currently deployed application and permanently
deletes its data.

When run interactively, the command will prompt for confirmation before
removing the application. When run non-interactively, the command will refuse
to remove the application unless the --force option is given.

This command cannot be used to remove production deployments in Vespa Cloud. See
https://cloud.vespa.ai/en/deleting-applications for how to remove production
deployments.
`,
		Example: `$ vespa destroy
$ vespa destroy -a mytenant.myapp.myinstance
$ vespa destroy --force`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			description := "current deployment"
			if target.IsCloud() {
				description = target.Deployment().String()
				env := target.Deployment().Zone.Environment
				if env != "dev" && env != "perf" {
					return errHint(fmt.Errorf("cannot remove production %s", description), "See https://cloud.vespa.ai/en/deleting-applications")
				}
			}
			ok := force
			if !ok {
				cli.printWarning(fmt.Sprintf("This operation will irrecoverably remove %s and all of its data", color.RedString(description)))
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
