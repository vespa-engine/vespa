// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
// author: bratseth

package cmd

import (
	"context"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"
	"os/signal"

	"github.com/joeshaw/envdecode"
	"github.com/logrusorgru/aurora/v3"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/auth"
	"github.com/vespa-engine/vespa/client/go/cli"
)

var (
	// default to vespa-cd.auth0.com
	authCfg struct {
		Audience           string `env:"AUTH0_AUDIENCE,default=https://vespa-cd.auth0.com/api/v2/"`
		ClientID           string `env:"AUTH0_CLIENT_ID,default=4wYWA496zBP28SLiz0PuvCt8ltL11DZX"`
		DeviceCodeEndpoint string `env:"AUTH0_DEVICE_CODE_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/device/code"`
		OauthTokenEndpoint string `env:"AUTH0_OAUTH_TOKEN_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/token"`
	}

	c       = &cli.Cli{}
	rootCmd = buildRootCmd(c)

	targetArg      string
	applicationArg string
	waitSecsArg    int
	colorArg       string
	quietArg       bool
	stdin          io.ReadWriter = os.Stdin

	color  = aurora.NewAurora(false)
	stdout = colorable.NewColorableStdout()
	stderr = colorable.NewColorableStderr()
)

const (
	applicationFlag = "application"
	targetFlag      = "target"
	waitFlag        = "wait"
	colorFlag       = "color"
	quietFlag       = "quiet"
)

func buildRootCmd(cli *cli.Cli) *cobra.Command {
	rootCmd := &cobra.Command{
		Use:   "vespa command-name",
		Short: "The command-line tool for Vespa.ai",
		Long: `The command-line tool for Vespa.ai.

Use it on Vespa instances running locally, remotely or in the cloud.
Prefer web service API's to this in production.

Vespa documentation: https://docs.vespa.ai`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			configureOutput()

			// auth
			if err := envdecode.StrictDecode(&authCfg); err != nil {
				return fmt.Errorf("could not decode env: %w", err)
			}
			cli.Authenticator = &auth.Authenticator{
				Audience:           authCfg.Audience,
				ClientID:           authCfg.ClientID,
				DeviceCodeEndpoint: authCfg.DeviceCodeEndpoint,
				OauthTokenEndpoint: authCfg.OauthTokenEndpoint,
			}

			// TODO: whitelist other commands
			// If the user is trying to log in, no need to go through setup
			if cmd.Use == "login" && cmd.Parent().Use == "vespa command-name" {
				return nil
			}

			return cli.Setup(cmd.Context())
		},
	}
	return rootCmd
}

func addPersistentFlags(rootCmd *cobra.Command) {
	rootCmd.PersistentFlags().StringVarP(&targetArg, targetFlag, "t", "local", "The name or URL of the recipient of this command")
	rootCmd.PersistentFlags().StringVarP(&applicationArg, applicationFlag, "a", "", "The application to manage")
	rootCmd.PersistentFlags().IntVarP(&waitSecsArg, waitFlag, "w", 0, "Number of seconds to wait for a service to become ready")
	rootCmd.PersistentFlags().StringVarP(&colorArg, colorFlag, "c", "auto", "Whether to use colors in output. Can be \"auto\", \"never\" or \"always\"")
	rootCmd.PersistentFlags().BoolVarP(&quietArg, quietFlag, "q", false, "Quiet mode. Only errors are printed.")
	bindFlagToConfig(targetFlag, rootCmd)
	bindFlagToConfig(applicationFlag, rootCmd)
	bindFlagToConfig(waitFlag, rootCmd)
	bindFlagToConfig(colorFlag, rootCmd)
	bindFlagToConfig(quietFlag, rootCmd)
}

func addSubcommands(rootCmd *cobra.Command, cli *cli.Cli) {
	rootCmd.AddCommand(loginCmd(cli))
}

func isTerminal() bool {
	if f, ok := stdout.(*os.File); ok {
		return isatty.IsTerminal(f.Fd())
	}
	if f, ok := stderr.(*os.File); ok {
		return isatty.IsTerminal(f.Fd())
	}
	return false
}

func configureOutput() {
	if quietArg {
		stdout = ioutil.Discard
	}
	log.SetFlags(0) // No timestamps
	log.SetOutput(stdout)

	config, err := LoadConfig()
	if err != nil {
		fatalErr(err, "Could not load config")
	}

	// path to auth config
	if c.Path == "" {
		c.Path = config.AuthConfigPath()
	}

	colorValue, err := config.Get(colorFlag)
	if err != nil {
		fatalErr(err)
	}

	colorize := false
	switch colorValue {
	case "auto":
		colorize = isTerminal()
	case "always":
		colorize = true
	case "never":
	default:
		fatalErrHint(fmt.Errorf("invalid value for %s option", colorFlag), "Must be \"auto\", \"never\" or \"always\"")
	}
	color = aurora.NewAurora(colorize)
}

func contextWithCancel() context.Context {
	ctx, cancel := context.WithCancel(context.Background())

	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt)

	go func() {
		<-ch
		defer cancel()
		os.Exit(0)
	}()

	return ctx
}

// Execute executes the root command.
func Execute() error {
	addPersistentFlags(rootCmd)
	addSubcommands(rootCmd, c)
	return rootCmd.ExecuteContext(contextWithCancel())
}
