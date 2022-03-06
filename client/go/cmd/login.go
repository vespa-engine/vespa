package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
)

func newLoginCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "login",
		Args:              cobra.NoArgs,
		Short:             "Authenticate the Vespa CLI",
		Example:           "$ vespa auth login",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()
			targetType, err := cli.config.targetType()
			if err != nil {
				return err
			}
			system, err := cli.system(targetType)
			if err != nil {
				return err
			}
			a, err := auth0.GetAuth0(cli.config.authConfigPath(), system.Name, system.URL)
			if err != nil {
				return err
			}
			_, err = auth0.RunLogin(ctx, a, false)
			return err
		},
	}
}
