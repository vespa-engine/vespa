package cmd

import (
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func init() {
	if vespa.Auth0AccessTokenEnabled() {
		rootCmd.AddCommand(authCmd)
		rootCmd.AddCommand(deprecatedCertCmd)
		rootCmd.AddCommand(deprecatedApiKeyCmd)
		authCmd.AddCommand(certCmd)
		authCmd.AddCommand(apiKeyCmd)
		authCmd.AddCommand(loginCmd)
		authCmd.AddCommand(logoutCmd)
	} else {
		rootCmd.AddCommand(certCmd)
		rootCmd.AddCommand(apiKeyCmd)
	}
}

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Manage Vespa Cloud credentials",
	Long:  `Manage Vespa Cloud credentials.`,

	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		// Root command does nothing
		cmd.Help()
		exitFunc(1)
	},
}
