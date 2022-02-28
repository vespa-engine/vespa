package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth0"
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
		targetType, err := getTargetType()
		if err != nil {
			return err
		}
		system, err := getSystem(targetType)
		if err != nil {
			return err
		}
		a, err := auth0.GetAuth0(cfg.AuthConfigPath(), system.Name, system.URL)
		if err != nil {
			return err
		}
		_, err = auth0.RunLogin(ctx, a, false)
		return err
	},
}
