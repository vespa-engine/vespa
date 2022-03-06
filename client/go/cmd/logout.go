package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth/auth0"
)

func newLogoutCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "logout",
		Args:              cobra.NoArgs,
		Short:             "Log out of Vespa Cli",
		Example:           "$ vespa auth logout",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
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
			err = auth0.RunLogout(a)
			return err
		},
	}
}
