package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth0"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func init() {
	if vespa.Auth0AccessTokenEnabled() {
		rootCmd.AddCommand(logoutCmd)
	}
}

var logoutCmd = &cobra.Command{
	Use:               "logout",
	Args:              cobra.NoArgs,
	Short:             "Log out of Vespa Cli",
	Example:           "$ vespa logout",
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	RunE: func(cmd *cobra.Command, args []string) error {
		cfg, err := LoadConfig()
		if err != nil {
			return err
		}
		a, err := auth0.GetAuth0(cfg.AuthConfigPath(), getSystemName())
		if err != nil {
			return err
		}
		err = auth0.RunLogout(a)
		return err
	},
}
