package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/cli"
)

func loginCmd(c *cli.Cli) *cobra.Command {
	cmd := &cobra.Command{
		Use:               "login",
		Args:              cobra.NoArgs,
		Short:             "Authenticate the Vespa CLI",
		Example:           "$ vespa login",
		DisableAutoGenTag: true,
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()
			_, err := cli.RunLogin(ctx, c, false)
			return err
		},
	}
	return cmd
}
