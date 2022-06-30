// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa api-key command
// Author: mpolden
package cmd

import (
	"fmt"
	"log"
	"os"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

func newAPIKeyCmd(cli *CLI) *cobra.Command {
	var overwriteKey bool
	cmd := &cobra.Command{
		Use:   "api-key",
		Short: "Create a new user API key for authentication with Vespa Cloud",
		Long: `Create a new user API key for authentication with Vespa Cloud.

The API key will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the API
key as necessary.

It's possible to override the API key used through environment variables. This
can be useful in continuous integration systems.

Example of setting the key in-line:

    export VESPA_CLI_API_KEY="my api key"

Example of loading the key from a custom path:

    export VESPA_CLI_API_KEY_FILE=/path/to/api-key

Note that when overriding API key through environment variables, that key will
always be used. It's not possible to specify a tenant-specific key.`,
		Example:           "$ vespa auth api-key -a my-tenant.my-app.my-instance",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Args:              cobra.ExactArgs(0),
		RunE: func(cmd *cobra.Command, args []string) error {
			return doApiKey(cli, overwriteKey, args)
		},
	}
	cmd.Flags().BoolVarP(&overwriteKey, "force", "f", false, "Force overwrite of existing API key")
	cmd.MarkPersistentFlagRequired(applicationFlag)
	return cmd
}

func doApiKey(cli *CLI, overwriteKey bool, args []string) error {
	app, err := cli.config.application()
	if err != nil {
		return err
	}
	targetType, err := cli.config.targetType()
	if err != nil {
		return err
	}
	system, err := cli.system(targetType)
	if err != nil {
		return err
	}
	apiKeyFile := cli.config.apiKeyPath(app.Tenant)
	if util.PathExists(apiKeyFile) && !overwriteKey {
		err := fmt.Errorf("refusing to overwrite %s", apiKeyFile)
		cli.printErr(err, "Use -f to overwrite it")
		printPublicKey(system, apiKeyFile, app.Tenant)
		return ErrCLI{error: err, quiet: true}
	}
	apiKey, err := vespa.CreateAPIKey()
	if err != nil {
		return fmt.Errorf("could not create api key: %w", err)
	}
	if err := os.WriteFile(apiKeyFile, apiKey, 0600); err == nil {
		cli.printSuccess("API private key written to ", apiKeyFile)
		return printPublicKey(system, apiKeyFile, app.Tenant)
	} else {
		return fmt.Errorf("failed to write: %s: %w", apiKeyFile, err)
	}
}

func printPublicKey(system vespa.System, apiKeyFile, tenant string) error {
	pemKeyData, err := os.ReadFile(apiKeyFile)
	if err != nil {
		return fmt.Errorf("failed to read: %s: %w", apiKeyFile, err)
	}
	key, err := vespa.ECPrivateKeyFrom(pemKeyData)
	if err != nil {
		return fmt.Errorf("failed to load key: %w", err)
	}
	pemPublicKey, err := vespa.PEMPublicKeyFrom(key)
	if err != nil {
		return fmt.Errorf("failed to extract public key: %w", err)
	}
	fingerprint, err := vespa.FingerprintMD5(pemPublicKey)
	if err != nil {
		return fmt.Errorf("failed to extract fingerprint: %w", err)
	}
	log.Printf("\nThis is your public key:\n%s", color.GreenString(string(pemPublicKey)))
	log.Printf("Its fingerprint is:\n%s\n", color.CyanString(fingerprint))
	log.Print("\nTo use this key in Vespa Cloud click 'Add custom key' at")
	log.Printf(color.CyanString("%s/tenant/%s/account/keys"), system.ConsoleURL, tenant)
	log.Print("and paste the entire public key including the BEGIN and END lines.")
	return nil
}
