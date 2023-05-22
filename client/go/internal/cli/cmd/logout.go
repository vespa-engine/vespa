package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
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
			targetType, err := cli.targetType()
			if err != nil {
				return err
			}
			system, err := cli.system(targetType.name)
			if err != nil {
				return err
			}
			a, err := auth0.NewClient(cli.httpClient, auth0.Options{ConfigPath: cli.config.authConfigPath(), SystemName: system.Name, SystemURL: system.URL})
			if err != nil {
				return err
			}
			if err := a.RemoveCredentials(); err != nil {
				return err
			}
			cli.printSuccess("Logged out")
			return nil
		},
	}
}
