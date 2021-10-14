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

var (
	rootCmd = &cobra.Command{
		Use:   "vespa command-name",
		Short: "The command-line tool for Vespa.ai",
		Long: `The command-line tool for Vespa.ai.

Use it on Vespa instances running locally, remotely or in the cloud.
Prefer web service API's to this in production.

Vespa documentation: https://docs.vespa.ai`,
		DisableAutoGenTag: true,
		PersistentPreRun: func(cmd *cobra.Command, args []string) {
			configureOutput()
		},
	}

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
		fatalErrHint(fmt.Errorf("Invalid value for %s option", colorFlag), "Must be \"auto\", \"never\" or \"always\"")
	}
	color = aurora.NewAurora(colorize)
}

func init() {
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

// Execute executes the root command.
func Execute() error { return rootCmd.Execute() }
