package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newFetchCmd(cli *CLI) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "fetch [path]",
		Short: "Download a deployed application package",
		Long: `Download a deployed application package.

This command can be used to download an already deployed Vespa application
package. The package is written as a ZIP file to the given path, or current
directory if no path is given.
`,
		Example: `$ vespa fetch
$ vespa fetch mydir/
$ vespa fetch -t cloud mycloudapp.zip
`,
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			target, err := cli.target(targetOptions{})
			if err != nil {
				return err
			}
			path := "."
			if len(args) > 0 {
				path = args[0]
			}
			dstPath := ""
			if err := cli.spinner(cli.Stderr, "Downloading application package...", func() error {
				dstPath, err = vespa.Fetch(vespa.DeploymentOptions{Target: target}, path)
				return err
			}); err != nil {
				return err
			}
			cli.printSuccess("Application package written to ", dstPath)
			return nil
		},
	}
	return cmd
}
