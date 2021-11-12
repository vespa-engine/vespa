package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/cli"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func init() {
	if vespa.Auth0AccessTokenEnabled() {
		rootCmd.AddCommand(loginCmd)
	}
}

var loginCmd = &cobra.Command{
	Use:               "login",
	Args:              cobra.NoArgs,
	Short:             "Authenticate the Vespa CLI",
	Example:           "$ vespa login",
	DisableAutoGenTag: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		ctx := cmd.Context()
		cfg, err := LoadConfig()
		if err != nil {
			return err
		}
		c, err := cli.GetCli(cfg.AuthConfigPath())
		if err != nil {
			return err
		}
		_, err = cli.RunLogin(ctx, c, false)
		return err
	},
}
