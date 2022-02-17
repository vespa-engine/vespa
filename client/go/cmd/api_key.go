// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa api-key command
// Author: mpolden
package cmd

import (
	"fmt"
	"io/ioutil"
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/util"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

var overwriteKey bool

const apiKeyLongDoc = `Create a new user API key for authentication with Vespa Cloud.

The API key will be stored in the Vespa CLI home directory
(see 'vespa help config'). Other commands will then automatically load the API
key as necessary.

It's possible to override the API key used through environment variables. This
can be useful in continuous integration systems.

* VESPA_CLI_API_KEY containing the key directly:

  export VESPA_CLI_API_KEY="my api key"

* VESPA_CLI_API_KEY_FILE containing path to the key:

  export VESPA_CLI_API_KEY_FILE=/path/to/api-key

Note that when overriding API key through environment variables, that key will
always be used. It's not possible to specify a tenant-specific key.`

func init() {
	apiKeyCmd.Flags().BoolVarP(&overwriteKey, "force", "f", false, "Force overwrite of existing API key")
	apiKeyCmd.MarkPersistentFlagRequired(applicationFlag)
}

func apiKeyExample() string {
	if vespa.Auth0AccessTokenEnabled() {
		return "$ vespa auth api-key -a my-tenant.my-app.my-instance"
	} else {
		return "$ vespa api-key -a my-tenant.my-app.my-instance"
	}
}

var apiKeyCmd = &cobra.Command{
	Use:               "api-key",
	Short:             "Create a new user API key for authentication with Vespa Cloud",
	Long:              apiKeyLongDoc,
	Example:           apiKeyExample(),
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(0),
	RunE:              doApiKey,
}

var deprecatedApiKeyCmd = &cobra.Command{
	Use:               "api-key",
	Short:             "Create a new user API key for authentication with Vespa Cloud",
	Long:              apiKeyLongDoc,
	Example:           apiKeyExample(),
	DisableAutoGenTag: true,
	SilenceUsage:      true,
	Args:              cobra.ExactArgs(0),
	Hidden:            true,
	Deprecated:        "use 'vespa auth api-key' instead",
	RunE:              doApiKey,
}

func doApiKey(_ *cobra.Command, _ []string) error {
	cfg, err := LoadConfig()
	if err != nil {
		return fmt.Errorf("could not load config: %w", err)
	}
	app, err := getApplication()
	if err != nil {
		return err
	}
	apiKeyFile := cfg.APIKeyPath(app.Tenant)
	if util.PathExists(apiKeyFile) && !overwriteKey {
		err := fmt.Errorf("refusing to overwrite %s", apiKeyFile)
		printErrHint(err, "Use -f to overwrite it")
		printPublicKey(apiKeyFile, app.Tenant)
		return ErrCLI{error: err, quiet: true}
	}
	apiKey, err := vespa.CreateAPIKey()
	if err != nil {
		return fmt.Errorf("could not create api key: %w", err)
	}
	if err := ioutil.WriteFile(apiKeyFile, apiKey, 0600); err == nil {
		printSuccess("API private key written to ", apiKeyFile)
		printPublicKey(apiKeyFile, app.Tenant)
		if vespa.Auth0AccessTokenEnabled() {
			if err == nil {
				if err := cfg.Set(cloudAuthFlag, "api-key"); err != nil {
					return fmt.Errorf("could not write config: %w", err)
				}
				if err := cfg.Write(); err != nil {
					return err
				}
			}
		}
		return nil
	} else {
		return fmt.Errorf("failed to write: %s: %w", apiKeyFile, err)
	}
}

func printPublicKey(apiKeyFile, tenant string) error {
	pemKeyData, err := ioutil.ReadFile(apiKeyFile)
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
	log.Printf("\nThis is your public key:\n%s", color.Green(pemPublicKey))
	log.Printf("Its fingerprint is:\n%s\n", color.Cyan(fingerprint))
	log.Print("\nTo use this key in Vespa Cloud click 'Add custom key' at")
	log.Printf(color.Cyan("%s/tenant/%s/keys").String(), getConsoleURL(), tenant)
	log.Print("and paste the entire public key including the BEGIN and END lines.")
	return nil
}
