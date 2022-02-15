package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth0"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var loginCmd = &cobra.Command{
	Use:               "login",
	Args:              cobra.NoArgs,
	Short:             "Authenticate the Vespa CLI",
	Example:           "$ vespa auth login",
	DisableAutoGenTag: true,
	SilenceUsage:      true,
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
		if vespa.Auth0AccessTokenEnabled() {
			if err == nil {
				if err := cfg.Set(cloudAuthFlag, "access-token"); err != nil {
					return err
				}
				if err := cfg.Write(); err != nil {
					return err
				}
			}
		}
		return err
	},
}
