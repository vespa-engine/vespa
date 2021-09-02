// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
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
	Use:   "deploy <application-directory>",
	Short: "Deploy (prepare and activate) an application package",
	Long: `Deploy (prepare and activate) an application package.

When this returns successfully the application package has been validated
and activated on config servers. The process of applying it on individual nodes
has started but may not have completed.`,
	Example: "$ vespa deploy .",
	Args:    cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.ApplicationPackageFrom(applicationSource(args))
		if err != nil {
			printErr(nil, err.Error())
			return
		}
		target := getTarget()
		opts := vespa.DeploymentOpts{ApplicationPackage: pkg, Target: target}
		if opts.IsCloud() {
			deployment := deploymentFromArgs()
			if !opts.ApplicationPackage.HasCertificate() {
				printErrHint(fmt.Errorf("Missing certificate in application package"), "Applications in Vespa Cloud require a certificate", "Try 'vespa cert'")
			}
			opts.APIKey = readAPIKey(deployment.Application.Tenant)
			opts.Deployment = deployment
		}
		if err := vespa.Deploy(opts); err == nil {
			printSuccess("Deployed ", color.Cyan(pkg.Path))
			if opts.IsCloud() {
				log.Printf("\n\nUse %s for deployment status, or see", color.Cyan("vespa status"))
				log.Print(color.Cyan(fmt.Sprintf("https://console.vespa.oath.cloud/tenant/%s/application/%s/dev/instance/%s", opts.Deployment.Application.Tenant, opts.Deployment.Application.Application, opts.Deployment.Application.Instance)))
			}
		} else {
			printErr(nil, err.Error())
		}
	},
}

var prepareCmd = &cobra.Command{
	Use:   "prepare <application-directory>",
	Short: "Prepare an application package for activation",
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.ApplicationPackageFrom(applicationSource(args))
		if err != nil {
			printErr(err, "Could not find application package")
			return
		}
		configDir := configDir("default")
		if configDir == "" {
			return
		}
		target := getTarget()
		sessionID, err := vespa.Prepare(vespa.DeploymentOpts{
			ApplicationPackage: pkg,
			Target:             target,
		})
		if err == nil {
			writeSessionID(configDir, sessionID)
			printSuccess("Prepared ", color.Cyan(pkg.Path), " with session ", sessionID)
		} else {
			printErr(nil, err.Error())
		}
	},
}

var activateCmd = &cobra.Command{
	Use:   "activate",
	Short: "Activate (deploy) a previously prepared application package",
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.ApplicationPackageFrom(applicationSource(args))
		if err != nil {
			printErr(err, "Could not find application package")
			return
		}
		configDir := configDir("default")
		sessionID := readSessionID(configDir)
		target := getTarget()
		err = vespa.Activate(sessionID, vespa.DeploymentOpts{
			ApplicationPackage: pkg,
			Target:             target,
		})
		if err == nil {
			printSuccess("Activated ", color.Cyan(pkg.Path), " with session ", sessionID)
		} else {
			printErr(nil, err.Error())
		}
	},
}

func writeSessionID(appConfigDir string, sessionID int64) {
	if err := os.MkdirAll(appConfigDir, 0755); err != nil {
		printErr(err, "Could not create directory for session ID")
	}
	if err := os.WriteFile(sessionIDFile(appConfigDir), []byte(fmt.Sprintf("%d\n", sessionID)), 0600); err != nil {
		printErr(err, "Could not write session ID")
	}
}

func readSessionID(appConfigDir string) int64 {
	b, err := os.ReadFile(sessionIDFile(appConfigDir))
	if err != nil {
		printErr(err, "Could not read session ID")
	}
	id, err := strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
	if err != nil {
		printErr(err, "Invalid session ID")
	}
	return id
}

func sessionIDFile(appConfigDir string) string { return filepath.Join(appConfigDir, "session_id") }

func readAPIKey(tenant string) []byte {
	configDir := configDir("")
	apiKeyPath := filepath.Join(configDir, tenant+".api-key.pem")
	key, err := os.ReadFile(apiKeyPath)
	if err != nil {
		printErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
	}
	return key
}

func deploymentFromArgs() vespa.Deployment {
	zone, err := vespa.ZoneFromString(zoneArg)
	if err != nil {
		printErrHint(err, "Zone format is <env>.<region>")
	}
	app, err := vespa.ApplicationFromString(getApplication())
	if err != nil {
		printErrHint(err, "Application format is <tenant>.<app>.<instance>")
	}
	return vespa.Deployment{Application: app, Zone: zone}
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	return "."
}
