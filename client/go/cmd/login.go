package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth0"
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
		a, err := auth0.GetAuth0(cfg.AuthConfigPath(), getSystemName(), getApiURL())
		if err != nil {
			return err
		}
		_, err = auth0.RunLogin(ctx, a, false)
		return err
	},
}
