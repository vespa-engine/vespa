// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"fmt"
	"log"
	"os"
	"time"

	"github.com/pkg/browser"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth"
	"github.com/vespa-engine/vespa/client/go/internal/cli/auth/auth0"
)

// newLoginCmd runs the login flow guiding the user through the process
// by showing the login instructions, opening the browser.
// Use `expired` to run the login from other commands setup:
// this will only affect the messages.
func newLoginCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "login",
		Args:  cobra.NoArgs,
		Short: "Authenticate Vespa CLI with Vespa Cloud control plane. This is preferred over api-key for interactive use",
		Long: `Authenticate Vespa CLI with Vespa Cloud control plane. This is preferred over api-key for interactive use.

This command runs a browser-based authentication flow for the Vespa Cloud control plane.
`,
		Example:           "$ vespa auth login",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx := cmd.Context()
			targetType, err := cli.targetType(cloudTargetOnly)
			if err != nil {
				return err
			}
			system, err := cli.system(targetType.name)
			if err != nil {
				return err
			}
			a, err := auth0.NewClient(cli.httpClient, auth0.Options{ConfigPath: cli.config.authConfigPath(), SystemName: system.Name, SystemURL: system.URL})
			if err != nil {
				return err
			}
			state, err := a.Authenticator.Start(ctx)
			if err != nil {
				return fmt.Errorf("could not start the authentication process: %w", err)
			}

			log.Printf("Your Device Confirmation code is: %s\n", state.UserCode)

			autoOpen, err := cli.confirm("Automatically open confirmation page in your default browser?", true)
			if err != nil {
				return err
			}

			if autoOpen {
				log.Printf("Opened link in your browser: %s\n", state.VerificationURI)
				err = browser.OpenURL(state.VerificationURI)
				if err != nil {
					log.Println("Couldn't open the URL, please do it manually")
				}
			} else {
				log.Printf("Please open link in your browser: %s\n", state.VerificationURI)
			}

			var res auth.Result
			err = cli.spinner(os.Stderr, "Waiting for login to complete in browser ...", func() error {
				res, err = a.Authenticator.Wait(ctx, state)
				return err
			})

			if err != nil {
				switch err.Error() {
				case "600":
					return errHint(fmt.Errorf("Unable to login through social channels"),
						"Your organization does not allow logging in through our social channels",
						"Please login by entering your address into the email field")
				default:
					return fmt.Errorf("login error: %w", err)
				}
			}

			// store the refresh token
			secretsStore := &auth.Keyring{}
			err = secretsStore.Set(auth.SecretsNamespace, system.Name, res.RefreshToken)
			if err != nil {
				// log the error but move on
				cli.printWarning("Could not store the refresh token locally. You may need to login again once your access token expires")
			}

			creds := auth0.Credentials{
				AccessToken: res.AccessToken,
				ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
				Scopes:      auth.RequiredScopes(),
			}
			if err := a.WriteCredentials(creds); err != nil {
				return fmt.Errorf("failed to write credentials: %w", err)
			}

			cli.printSuccess("Logged in")
			return nil
		},
	}
}
