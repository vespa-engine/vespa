// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"fmt"
	"log"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/vespa"
)

const (
	zoneFlag = "zone"
)

var (
	zoneArg string
)

func init() {
	rootCmd.AddCommand(deployCmd)
	rootCmd.AddCommand(prepareCmd)
	rootCmd.AddCommand(activateCmd)
	deployCmd.PersistentFlags().StringVarP(&zoneArg, zoneFlag, "z", "dev.aws-us-east-1c", "The zone to use for deployment")
}

var deployCmd = &cobra.Command{
	Use:   "deploy [application-directory]",
	Short: "Deploy (prepare and activate) an application package",
	Long: `Deploy (prepare and activate) an application package.

When this returns successfully the application package has been validated
and activated on config servers. The process of applying it on individual nodes
has started but may not have completed.

If application directory is not specified, it defaults to working directory.`,
	Example:           "$ vespa deploy .",
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.FindApplicationPackage(applicationSource(args), true)
		if err != nil {
			fatalErr(nil, err.Error())
			return
		}
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		target := getTarget()
		opts := vespa.DeploymentOpts{ApplicationPackage: pkg, Target: target}
		if opts.IsCloud() {
			deployment := deploymentFromArgs()
			if !opts.ApplicationPackage.HasCertificate() {
				fatalErrHint(fmt.Errorf("Missing certificate in application package"), "Applications in Vespa Cloud require a certificate", "Try 'vespa cert'")
				return
			}
			opts.APIKey, err = cfg.ReadAPIKey(deployment.Application.Tenant)
			if err != nil {
				fatalErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
				return
			}
			opts.Deployment = deployment
		}
		if sessionOrRunID, err := vespa.Deploy(opts); err == nil {
			if opts.IsCloud() {
				printSuccess("Triggered deployment of ", color.Cyan(pkg.Path), " with run ID ", color.Cyan(sessionOrRunID))
			} else {
				printSuccess("Deployed ", color.Cyan(pkg.Path))
			}
			if opts.IsCloud() {
				log.Printf("\nUse %s for deployment status, or follow this deployment at", color.Cyan("vespa status"))
				log.Print(color.Cyan(fmt.Sprintf("%s/tenant/%s/application/%s/dev/instance/%s/job/%s-%s/run/%d",
					defaultConsoleURL,
					opts.Deployment.Application.Tenant, opts.Deployment.Application.Application, opts.Deployment.Application.Instance,
					opts.Deployment.Zone.Environment, opts.Deployment.Zone.Region,
					sessionOrRunID)))
			}
			waitForQueryService(sessionOrRunID)
		} else {
			fatalErr(nil, err.Error())
		}
	},
}

var prepareCmd = &cobra.Command{
	Use:               "prepare application-directory",
	Short:             "Prepare an application package for activation",
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.FindApplicationPackage(applicationSource(args), true)
		if err != nil {
			fatalErr(err, "Could not find application package")
			return
		}
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		target := getTarget()
		sessionID, err := vespa.Prepare(vespa.DeploymentOpts{
			ApplicationPackage: pkg,
			Target:             target,
		})
		if err == nil {
			if err := cfg.WriteSessionID(vespa.DefaultApplication, sessionID); err != nil {
				fatalErr(err, "Could not write session ID")
				return
			}
			printSuccess("Prepared ", color.Cyan(pkg.Path), " with session ", sessionID)
		} else {
			fatalErr(nil, err.Error())
		}
	},
}

var activateCmd = &cobra.Command{
	Use:               "activate",
	Short:             "Activate (deploy) a previously prepared application package",
	Args:              cobra.MaximumNArgs(1),
	DisableAutoGenTag: true,
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.FindApplicationPackage(applicationSource(args), true)
		if err != nil {
			fatalErr(err, "Could not find application package")
			return
		}
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		sessionID, err := cfg.ReadSessionID(vespa.DefaultApplication)
		if err != nil {
			fatalErr(err, "Could not read session ID")
			return
		}
		target := getTarget()
		err = vespa.Activate(sessionID, vespa.DeploymentOpts{
			ApplicationPackage: pkg,
			Target:             target,
		})
		if err == nil {
			printSuccess("Activated ", color.Cyan(pkg.Path), " with session ", sessionID)
			waitForQueryService(sessionID)
		} else {
			fatalErr(nil, err.Error())
		}
	},
}

func waitForQueryService(sessionOrRunID int64) {
	if waitSecsArg > 0 {
		log.Println()
		waitForService("query", sessionOrRunID)
	}
}
