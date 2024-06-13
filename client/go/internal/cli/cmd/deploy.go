// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"fmt"
	"io"
	"log"
	"strconv"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/version"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func newDeployCmd(cli *CLI) *cobra.Command {
	var (
		waitSecs    int
		logLevelArg string
		versionArg  string
		copyCert    bool
	)
	cmd := &cobra.Command{
		Use:   "deploy [application-directory-or-file]",
		Short: "Deploy (prepare and activate) an application package",
		Long: `Deploy (prepare and activate) an application package.

An application package defines a deployable Vespa application. See
https://docs.vespa.ai/en/reference/application-packages-reference.html for
details about the files contained in this package.

To get started, 'vespa clone' can be used to download a sample application.

This command deploys an application package. When deploy returns successfully
the application package has been validated and activated on config servers. The
process of applying it on individual nodes has started but may not have
completed.

If application directory is not specified, it defaults to working directory.

In Vespa Cloud you may override the Vespa runtime version (--version) for your
deployment. This option should only be used if you have a reason for using a
specific version. By default Vespa Cloud chooses a suitable version for you.
`,
		Example: `$ vespa deploy .
$ vespa deploy -t cloud
$ vespa deploy -t cloud -z dev.aws-us-east-1c  # -z can be omitted here as this zone is the default
$ vespa deploy -t cloud -z perf.aws-us-east-1c`,
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			pkg, err := cli.applicationPackageFrom(args, vespa.PackageOptions{Compiled: true})
			if err != nil {
				return err
			}
			target, err := cli.target(targetOptions{logLevel: logLevelArg})
			if err != nil {
				return err
			}
			opts := vespa.DeploymentOptions{ApplicationPackage: pkg, Target: target}
			if versionArg != "" {
				version, err := version.Parse(versionArg)
				if err != nil {
					return err
				}
				opts.Version = version
			}
			if target.Type() == vespa.TargetCloud {
				if err := requireCertificate(copyCert, true, cli, target, pkg); err != nil {
					return err
				}
			}
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			if _, err := waiter.DeployService(target); err != nil {
				return err
			}
			var result vespa.PrepareResult
			err = cli.spinner(cli.Stderr, "Uploading application package...", func() error {
				result, err = vespa.Deploy(opts)
				return err
			})
			if err != nil {
				return err
			}
			log.Println()
			if opts.Target.IsCloud() {
				cli.printSuccess("Triggered deployment of ", color.CyanString("'"+pkg.Path+"'"), " with run ID ", color.CyanString(strconv.FormatInt(result.ID, 10)))
			} else {
				cli.printSuccess("Deployed ", color.CyanString("'"+pkg.Path+"'"), " with session ID ", color.CyanString(strconv.FormatInt(result.ID, 10)))
				printPrepareLog(cli.Stderr, result)
			}
			if opts.Target.IsCloud() {
				log.Printf("\nUse %s for deployment status, or follow this deployment at", color.CyanString("vespa status deployment"))
				log.Print(color.CyanString(opts.Target.Deployment().System.ConsoleRunURL(opts.Target.Deployment(), result.ID)))
			}
			return waitForVespaReady(target, result.ID, waiter)
		},
	}
	cmd.Flags().StringVarP(&logLevelArg, "log-level", "l", "error", `Log level for Vespa logs. Must be "error", "warning", "info" or "debug"`)
	cmd.Flags().StringVarP(&versionArg, "version", "V", "", `Override the Vespa runtime version to use in Vespa Cloud`)
	cmd.Flags().BoolVarP(&copyCert, "add-cert", "A", false, `Copy certificate of the configured application to the current application package`)
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func newPrepareCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:               "prepare [application-directory-or-file]",
		Short:             "Prepare an application package for activation",
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			pkg, err := cli.applicationPackageFrom(args, vespa.PackageOptions{Compiled: true})
			if err != nil {
				return fmt.Errorf("could not find application package: %w", err)
			}
			target, err := cli.target(targetOptions{supportedType: localTargetOnly})
			if err != nil {
				return err
			}
			opts := vespa.DeploymentOptions{ApplicationPackage: pkg, Target: target}
			var result vespa.PrepareResult
			err = cli.spinner(cli.Stderr, "Uploading application package...", func() error {
				result, err = vespa.Prepare(opts)
				return err
			})
			if err != nil {
				return err
			}
			if err := cli.config.writeSessionID(vespa.DefaultApplication, result.ID); err != nil {
				return fmt.Errorf("could not write session id: %w", err)
			}
			cli.printSuccess("Prepared ", color.CyanString("'"+pkg.Path+"'"), " with session ", result.ID)
			printPrepareLog(cli.Stderr, result)
			return nil
		},
	}
}

func newActivateCmd(cli *CLI) *cobra.Command {
	var waitSecs int
	cmd := &cobra.Command{
		Use:               "activate",
		Short:             "Activate (deploy) a previously prepared application package",
		Args:              cobra.MaximumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			sessionID, err := cli.config.readSessionID(vespa.DefaultApplication)
			if err != nil {
				return fmt.Errorf("could not read session id: %w", err)
			}
			target, err := cli.target(targetOptions{supportedType: localTargetOnly})
			if err != nil {
				return err
			}
			waiter := cli.waiter(time.Duration(waitSecs)*time.Second, cmd)
			if _, err := waiter.DeployService(target); err != nil {
				return err
			}
			opts := vespa.DeploymentOptions{Target: target}
			err = vespa.Activate(sessionID, opts)
			if err != nil {
				return err
			}
			cli.printSuccess("Activated application with session ", sessionID)
			return waitForVespaReady(target, sessionID, waiter)
		},
	}
	cli.bindWaitFlag(cmd, 0, &waitSecs)
	return cmd
}

func waitForVespaReady(target vespa.Target, sessionOrRunID int64, waiter *Waiter) error {
	fastWait := waiter.FastWaitOn(target)
	hasTimeout := waiter.Timeout > 0
	if fastWait || hasTimeout {
		// Wait for deployment convergence
		if _, err := waiter.Deployment(target, sessionOrRunID); err != nil {
			return err
		}
		// Wait for healthy services
		if hasTimeout {
			_, err := waiter.Services(target)
			return err
		}
	}
	return nil
}

func printPrepareLog(stderr io.Writer, result vespa.PrepareResult) {
	for _, entry := range result.LogLines {
		level := entry.Level
		switch level {
		case "ERROR":
			level = color.RedString(level)
		case "WARNING":
			level = color.YellowString(level)
		}
		fmt.Fprintf(stderr, "%s %s\n", level, entry.Message)
	}
}
