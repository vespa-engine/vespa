// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"strings"

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
	Use:   "deploy [<application-directory>]",
	Short: "Deploy (prepare and activate) an application package",
	Long: `Deploy (prepare and activate) an application package.

When this returns successfully the application package has been validated
and activated on config servers. The process of applying it on individual nodes
has started but may not have completed.

If application directory is not specified, it defaults to working directory.`,
	Example: "$ vespa deploy .",
	Args:    cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		pkg, err := vespa.ApplicationPackageFrom(applicationSource(args))
		if err != nil {
			fatalErr(nil, err.Error())
			return
		}
		target := getTarget()
		opts := vespa.DeploymentOpts{ApplicationPackage: pkg, Target: target}
		if opts.IsCloud() {
			deployment := deploymentFromArgs()
			if !opts.ApplicationPackage.HasCertificate() {
				fatalErrHint(fmt.Errorf("Missing certificate in application package"), "Applications in Vespa Cloud require a certificate", "Try 'vespa cert'")
			}
			opts.APIKey = readAPIKey(deployment.Application.Tenant)
			opts.Deployment = deployment
		}
		if err := vespa.Deploy(opts); err == nil {
			printSuccess("Deployed ", color.Cyan(pkg.Path))
			if opts.IsCloud() {
				log.Printf("\nUse %s for deployment status, or see", color.Cyan("vespa status"))
				log.Print(color.Cyan(fmt.Sprintf("https://console.vespa.oath.cloud/tenant/%s/application/%s/dev/instance/%s", opts.Deployment.Application.Tenant, opts.Deployment.Application.Application, opts.Deployment.Application.Instance)))
			}
			waitForQueryService()
		} else {
			fatalErr(nil, err.Error())
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
			fatalErr(err, "Could not find application package")
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
			fatalErr(nil, err.Error())
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
			fatalErr(err, "Could not find application package")
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
			waitForQueryService()
		} else {
			fatalErr(nil, err.Error())
		}
	},
}

func waitForQueryService() {
	if waitSecsArg > 0 {
		log.Println()
		waitForService("query")
	}
}

func writeSessionID(appConfigDir string, sessionID int64) {
	if err := os.MkdirAll(appConfigDir, 0755); err != nil {
		fatalErr(err, "Could not create directory for session ID")
	}
	if err := ioutil.WriteFile(sessionIDFile(appConfigDir), []byte(fmt.Sprintf("%d\n", sessionID)), 0600); err != nil {
		fatalErr(err, "Could not write session ID")
	}
}

func readSessionID(appConfigDir string) int64 {
	b, err := ioutil.ReadFile(sessionIDFile(appConfigDir))
	if err != nil {
		fatalErr(err, "Could not read session ID")
	}
	id, err := strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
	if err != nil {
		fatalErr(err, "Invalid session ID")
	}
	return id
}

func sessionIDFile(appConfigDir string) string { return filepath.Join(appConfigDir, "session_id") }
