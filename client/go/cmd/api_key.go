// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa api-key command
// Author: mpolden
package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/util"
	"github.com/vespa-engine/vespa/vespa"
)

var overwriteKey bool

func init() {
	rootCmd.AddCommand(apiKeyCmd)
	apiKeyCmd.Flags().BoolVarP(&overwriteKey, "force", "f", false, "Force overwrite of existing API key")
	apiKeyCmd.MarkPersistentFlagRequired(applicationFlag)
}

var apiKeyCmd = &cobra.Command{
	Use:     "api-key",
	Short:   "Create a new user API key for authentication with Vespa Cloud",
	Example: "$ vespa api-key -a my-tenant.my-app.my-instance",
	Args:    cobra.ExactArgs(0),
	Run: func(cmd *cobra.Command, args []string) {
		configDir := configDir("")
		if configDir == "" {
			return
		}
		app, err := vespa.ApplicationFromString(getApplication())
		if err != nil {
			printErr(err, "Could not parse application")
			return
		}
		apiKeyFile := filepath.Join(configDir, app.Tenant+".api-key.pem")
		if util.PathExists(apiKeyFile) && !overwriteKey {
			printErrHint(fmt.Errorf("File %s already exists", apiKeyFile), "Use -f to overwrite it")
			return
		}
		apiKey, err := vespa.CreateAPIKey()
		if err != nil {
			printErr(err, "Could not create API key")
			return
		}
		if err := os.WriteFile(apiKeyFile, apiKey, 0600); err == nil {
			printSuccess("API key written to ", apiKeyFile)
		} else {
			printErr(err, "Failed to write ", apiKeyFile)

		}
	},
}
