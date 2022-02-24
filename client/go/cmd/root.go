// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Root Cobra command: vespa
// author: bratseth

package cmd

import (
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"os"

	"github.com/logrusorgru/aurora/v3"
	"github.com/mattn/go-colorable"
	"github.com/mattn/go-isatty"
	"github.com/spf13/cobra"
)

// ErrCLI is an error returned to the user. It wraps an exit status, a regular error and optional hints for resolving
// the error.
type ErrCLI struct {
	Status int
	quiet  bool
	hints  []string
	error
}

var (
	rootCmd = &cobra.Command{
		Use:   "vespa command-name",
		Short: "The command-line tool for Vespa.ai",
		Long: `The command-line tool for Vespa.ai.

Use it on Vespa instances running locally, remotely or in the cloud.
Prefer web service API's to this in production.

Vespa documentation: https://docs.vespa.ai`,
		DisableAutoGenTag: true,
		SilenceErrors:     true, // We have our own error printing
		SilenceUsage:      false,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			return configureOutput()
		},
		Args: cobra.MinimumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			return fmt.Errorf("invalid command: %s", args[0])
		},
	}

	targetArg      string
	applicationArg string
	waitSecsArg    int
	colorArg       string
	quietArg       bool
	apiKeyFileArg  string
	apiKeyArg      string
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
	apiKeyFileFlag  = "api-key-file"
	apiKeyFlag      = "api-key"
)

func isTerminal() bool {
	if f, ok := stdout.(*os.File); ok {
		return isatty.IsTerminal(f.Fd())
	}
	if f, ok := stderr.(*os.File); ok {
		return isatty.IsTerminal(f.Fd())
	}
	return false
}

func configureOutput() error {
	if quietArg {
		stdout = ioutil.Discard
	}
	log.SetFlags(0) // No timestamps
	log.SetOutput(stdout)

	config, err := LoadConfig()
	if err != nil {
		return err
	}
	colorValue, err := config.Get(colorFlag)
	if err != nil {
		return err
	}

	colorize := false
	switch colorValue {
	case "auto":
		colorize = isTerminal()
	case "always":
		colorize = true
	case "never":
	default:
		return errHint(fmt.Errorf("invalid value for %s option", colorFlag), "Must be \"auto\", \"never\" or \"always\"")
	}
	color = aurora.NewAurora(colorize)
	return nil
}

func init() {
	rootCmd.PersistentFlags().StringVarP(&targetArg, targetFlag, "t", "local", "The name or URL of the recipient of this command")
	rootCmd.PersistentFlags().StringVarP(&applicationArg, applicationFlag, "a", "", "The application to manage")
	rootCmd.PersistentFlags().IntVarP(&waitSecsArg, waitFlag, "w", 0, "Number of seconds to wait for a service to become ready")
	rootCmd.PersistentFlags().StringVarP(&colorArg, colorFlag, "c", "auto", "Whether to use colors in output. Can be \"auto\", \"never\" or \"always\"")
	rootCmd.PersistentFlags().BoolVarP(&quietArg, quietFlag, "q", false, "Quiet mode. Only errors are printed.")
	rootCmd.PersistentFlags().StringVarP(&apiKeyFileArg, apiKeyFileFlag, "k", "", "Path to API key used for deployment authentication")

	bindFlagToConfig(targetFlag, rootCmd)
	bindFlagToConfig(applicationFlag, rootCmd)
	bindFlagToConfig(waitFlag, rootCmd)
	bindFlagToConfig(colorFlag, rootCmd)
	bindFlagToConfig(quietFlag, rootCmd)
	bindFlagToConfig(apiKeyFileFlag, rootCmd)

	bindEnvToConfig(apiKeyFlag, "VESPA_CLI_API_KEY")
	bindEnvToConfig(apiKeyFileFlag, "VESPA_CLI_API_KEY_FILE")
}

// errHint creates a new CLI error, with optional hints that will be printed after the error
func errHint(err error, hints ...string) ErrCLI { return ErrCLI{Status: 1, hints: hints, error: err} }

// Execute executes command and prints any errors.
func Execute() error {
	err := rootCmd.Execute()
	if err != nil {
		if cliErr, ok := err.(ErrCLI); ok {
			if !cliErr.quiet {
				printErrHint(cliErr, cliErr.hints...)
			}
		} else {
			printErrHint(err)
		}
	}
	return err
}
