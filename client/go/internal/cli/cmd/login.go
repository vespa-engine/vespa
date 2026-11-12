// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/exec"
	"runtime"
	"strconv"
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
	var useFileStorage bool
	var noBrowser bool
	var batch bool
	var useDeviceFlow bool
	cmd := &cobra.Command{
		Use:   "login",
		Args:  cobra.NoArgs,
		Short: "Authenticate Vespa CLI with Vespa Cloud control plane. This is preferred over api-key for interactive use",
		Long: `Authenticate Vespa CLI with Vespa Cloud control plane. This is preferred over api-key for interactive use.

This command runs a browser-based authentication flow for the Vespa Cloud control plane.

By default, the authorization code flow with PKCE is used: a login page is opened in your browser, and
the result is received by a temporary local server on 127.0.0.1, using the first available port in
` + strconv.Itoa(auth.PKCECallbackPortFirst) + `-` + strconv.Itoa(auth.PKCECallbackPortFirst+auth.PKCECallbackPortCount-1) + `.
This requires the browser to run on the same machine as Vespa CLI.

Use --device to use the device authorization flow instead. This flow prints a confirmation code and a
URL that can be opened in a browser on any machine, and is useful in SSH sessions, containers and other
environments where the browser cannot reach this machine.

Use --file-storage flag to store the refresh token in unencrypted files instead of the system keyring.
This is useful in SSH/CI/Docker environments where keyring access may not be available.

Use --no-browser to print the login URL to standard output instead of opening a browser automatically.
This is useful in non-interactive terminals (e.g. Git Bash on Windows) or remote sessions where
a browser cannot be opened.

Use --batch to suppress all human-readable output and print only machine-readable key=value pairs.
This always uses the device authorization flow:
  code=<device-confirmation-code>
  url=<verification-url>
This is useful for scripting or automation where the output is parsed programmatically.
`,
		Example:           "$ vespa auth login",
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return doLogin(cli, cmd, useFileStorage, noBrowser, batch, useDeviceFlow)
		},
	}
	cmd.Flags().BoolVar(&useFileStorage, "file-storage", false, "Use file storage (unencrypted) instead of keyring for storing refresh token")
	cmd.Flags().BoolVar(&noBrowser, "no-browser", false, "Print login URL to standard output without confirmation and without automatic opening of browser")
	cmd.Flags().BoolVar(&batch, "batch", false, "Print machine-readable key=value pairs (code, url) and suppress all other output")
	cmd.Flags().BoolVar(&useDeviceFlow, "device", false, "Use the device authorization flow instead of the default browser-based flow with a local callback server")
	cmd.MarkFlagsMutuallyExclusive("no-browser", "batch")
	return cmd
}

func doLogin(cli *CLI, cmd *cobra.Command, useFileStorage bool, noBrowser bool, batch bool, useDeviceFlow bool) error {
	ctx := cmd.Context()
	targetType, err := cli.targetType(cloudTargetOnly)
	if err != nil {
		return err
	}
	system, err := cli.system(targetType.name)
	if err != nil {
		return err
	}
	a, err := auth0.NewClient(cli.httpClient, auth0.Options{ConfigPath: cli.config.authConfigPath(), SystemName: system.Name, SystemURL: system.URL, UseFileStorage: useFileStorage})
	if err != nil {
		return err
	}
	if !batch {
		if _, err := a.AccessToken(true); err == nil {
			cli.printDebug("Using stored credentials, refreshed if needed")
			cli.printSuccess("Logged in")
			return nil
		} else {
			cli.printDebug("Could not use stored credentials, starting new login: ", err)
		}
	}
	var res auth.Result
	if useDeviceFlow || batch {
		res, err = loginDeviceFlow(cli, ctx, a, noBrowser, batch)
	} else {
		res, err = loginPKCEFlow(cli, ctx, a, noBrowser)
	}
	if err != nil {
		return err
	}

	// store the refresh token
	secretsStore := auth.NewKeyringWithOptions(useFileStorage)
	err = secretsStore.Set(auth.SecretsNamespace, system.Name, res.RefreshToken)
	if err != nil {
		if useFileStorage && os.IsPermission(err) {
			cli.printWarning(fmt.Sprintf("Could not store refresh token locally because ~/.vespa/keyring.%s.%s has wrong file permissions. Delete it and try again: %v", auth.SecretsNamespace, system.Name, err))
		} else {
			cli.printWarning("Could not store the refresh token locally. You may need to login again once your access token expires (30 minutes).")
			if !useFileStorage {
				cli.printWarning("To persist the refresh token using file storage (unencrypted), use --file-storage flag")
				cli.printWarning("Note: Storing the refresh token unencrypted directly on your file system means someone with access to this file can get unauthorized access to your application for the life of the refresh token (24 hours)")
			}
		}
	}

	creds := auth0.Credentials{
		AccessToken: res.AccessToken,
		ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
		Scopes:      auth.RequiredScopes(),
	}
	if err := a.WriteCredentials(creds); err != nil {
		return fmt.Errorf("failed to write credentials: %w", err)
	}

	if !batch {
		cli.printSuccess("Logged in")
	}
	return nil
}

const deviceFlowHint = "If logging in through the browser does not work, for example on a remote machine or when the browser cannot reach this machine, try 'vespa auth login --device'"

// loginPKCEFlow runs the authorization code flow with PKCE, receiving the result on a local callback server.
func loginPKCEFlow(cli *CLI, ctx context.Context, a *auth0.Client, noBrowser bool) (auth.Result, error) {
	state, err := a.Authenticator.StartPKCE(ctx)
	if err != nil {
		return auth.Result{}, errHint(fmt.Errorf("could not start the authentication process: %w", err), deviceFlowHint)
	}
	defer state.Close()

	autoOpen := !noBrowser && isBrowserOpenPossible()
	if autoOpen {
		autoOpen, _ = cli.confirm("Automatically open login page in your default browser?", true)
	}
	if autoOpen {
		log.Printf("Opened link in your browser:\n\t %s\n", state.AuthorizeURL)
		if err := browser.OpenURL(state.AuthorizeURL); err != nil {
			log.Println("Couldn't open the URL, please do it manually")
		}
	} else {
		log.Printf("Please open link in your browser:\n\t %s\n", state.AuthorizeURL)
	}

	var res auth.Result
	err = cli.spinner(os.Stderr, "Waiting for login to complete in browser ...", func() error {
		res, err = a.Authenticator.WaitPKCE(ctx, state)
		return err
	})
	if err != nil {
		if err.Error() == "600" {
			return auth.Result{}, errSSORequired()
		}
		return auth.Result{}, errHint(fmt.Errorf("login error: %w", err), deviceFlowHint)
	}
	return res, nil
}

// loginDeviceFlow runs the device authorization flow.
func loginDeviceFlow(cli *CLI, ctx context.Context, a *auth0.Client, noBrowser bool, batch bool) (auth.Result, error) {
	state, err := a.Authenticator.Start(ctx)
	if err != nil {
		return auth.Result{}, fmt.Errorf("could not start the authentication process: %w", err)
	}

	if batch {
		fmt.Fprintf(cli.Stdout, "code=%s\n", state.UserCode)
		fmt.Fprintf(cli.Stdout, "url=%s\n", state.VerificationURI)
	} else {
		log.Printf("Your Device Confirmation code is: %s\n", state.UserCode)

		autoOpen := !noBrowser && isBrowserOpenPossible()
		if autoOpen {
			autoOpen, _ = cli.confirm("Automatically open confirmation page in your default browser?", true)
		}
		if autoOpen {
			log.Printf("Opened link in your browser:\n\t %s\n", state.VerificationURI)
			err = browser.OpenURL(state.VerificationURI)
			if err != nil {
				log.Println("Couldn't open the URL, please do it manually")
			}
		} else {
			log.Printf("Please open link in your browser:\n\t %s\n", state.VerificationURI)
		}
	}

	var res auth.Result
	if batch {
		res, err = a.Authenticator.Wait(ctx, state)
	} else {
		err = cli.spinner(os.Stderr, "Waiting for login to complete in browser ...", func() error {
			res, err = a.Authenticator.Wait(ctx, state)
			return err
		})
	}
	if err != nil {
		if err.Error() == "600" {
			return auth.Result{}, errSSORequired()
		}
		return auth.Result{}, fmt.Errorf("login error: %w", err)
	}
	return res, nil
}

func errSSORequired() error {
	return errHint(fmt.Errorf("Your organization require SSO for Vespa Cloud access"),
		"Please login by entering your email in the email address field")
}

func isBrowserOpenPossible() bool {
	if runtime.GOOS == "windows" {
		return true
	}
	providers := []string{
		"open",
		"xdg-open",
		"x-www-browser",
		"www-browser",
	}
	for _, provider := range providers {
		if _, err := exec.LookPath(provider); err == nil {
			return true
		}
	}
	return false
}
