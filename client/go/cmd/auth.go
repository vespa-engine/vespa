package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(authCmd)
	rootCmd.AddCommand(deprecatedCertCmd)
	rootCmd.AddCommand(deprecatedApiKeyCmd)
	authCmd.AddCommand(certCmd)
	authCmd.AddCommand(apiKeyCmd)
	authCmd.AddCommand(loginCmd)
	authCmd.AddCommand(logoutCmd)
}

var authCmd = &cobra.Command{
	Use:               "auth",
	Short:             "Manage Vespa Cloud credentials",
	Long:              `Manage Vespa Cloud credentials.`,
	DisableAutoGenTag: true,
	SilenceUsage:      false,
	Args:              cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		return fmt.Errorf("invalid command: %s", args[0])
	},
}
