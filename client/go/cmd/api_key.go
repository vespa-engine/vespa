// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa api-key command
// Author: mpolden
package cmd

import (
	"fmt"
	"log"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
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
			fatalErr(err, "Could not parse application")
			return
		}
		apiKeyFile := filepath.Join(configDir, app.Tenant+".api-key.pem")
		if util.PathExists(apiKeyFile) && !overwriteKey {
			printErrHint(fmt.Errorf("File %s already exists", apiKeyFile), "Use -f to overwrite it")
			printPublicKey(apiKeyFile, app.Tenant)
			return
		}
		apiKey, err := vespa.CreateAPIKey()
		if err != nil {
			fatalErr(err, "Could not create API key")
			return
		}
		if err := os.WriteFile(apiKeyFile, apiKey, 0600); err == nil {
			printSuccess("API private key written to ", apiKeyFile)
			printPublicKey(apiKeyFile, app.Tenant)
		} else {
			fatalErr(err, "Failed to write ", apiKeyFile)
		}
	},
}

func printPublicKey(apiKeyFile, tenant string) {
	pemKeyData, err := os.ReadFile(apiKeyFile)
	if err != nil {
		fatalErr(err, "Failed to read ", apiKeyFile)
		return
	}
	key, err := vespa.ECPrivateKeyFrom(pemKeyData)
	if err != nil {
		fatalErr(err, "Failed to load key")
		return
	}
	pemPublicKey, err := vespa.PEMPublicKeyFrom(key)
	if err != nil {
		fatalErr(err, "Failed to extract public key")
		return
	}
	fingerprint, err := vespa.FingerprintMD5(pemPublicKey)
	if err != nil {
		fatalErr(err, "Failed to extract fingerprint")
	}
	log.Printf("\nThis is your public key:\n%s", color.Green(pemPublicKey))
	log.Printf("Its fingerprint is:\n%s\n", color.Cyan(fingerprint))
	log.Print("\nTo use this key in Vespa Cloud click 'Add custom key' at")
	log.Printf(color.Cyan("https://console.vespa.oath.cloud/tenant/%s/keys").String(), tenant)
	log.Print("and paste the entire public key including the BEGIN and END lines.")
}
