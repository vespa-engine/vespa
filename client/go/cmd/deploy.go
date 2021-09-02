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
		d := vespa.Deployment{
			ApplicationPackage: pkg,
			TargetType:         getTargetType(),
			TargetURL:          deployTarget(),
		}
		if d.IsCloud() {
			var err error
			d.Zone, err = vespa.ZoneFromString(zoneArg)
			if err != nil {
				printErrHint(err, "Zones have the format <env>.<region>.")
				return
			}
			d.Application, err = vespa.ApplicationFromString(getApplication())
			if err != nil {
				printErrHint(err, "Applications have the format <tenant>.<application-name>.<instance-name>")
				return
			}
			if !d.ApplicationPackage.HasCertificate() {
				printErrHint(fmt.Errorf("Missing certificate in application package"), "Applications in Vespa Cloud require a certificate", "Try 'vespa cert'")
				return
			}
			configDir := configDir("")
			if configDir == "" {
				return
			}
			d.APIKey = readAPIKey(configDir, d.Application.Tenant)
			if d.APIKey == nil {
				printErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
				return
			}
		}
		if err := vespa.Deploy(d); err == nil {
			printSuccess("Deployed ", color.Cyan(pkg.Path))
			if d.IsCloud() {
				log.Print("See ", color.Cyan(fmt.Sprintf("https://console.vespa.oath.cloud/tenant/%s/application/%s/dev/instance/%s", d.Application.Tenant, d.Application.Application, d.Application.Instance)), " for deployment status")
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
		sessionID, err := vespa.Prepare(vespa.Deployment{
			ApplicationPackage: pkg,
			TargetType:         getTargetType(),
			TargetURL:          deployTarget(),
		})
		if err == nil {
			if err := writeSessionID(configDir, sessionID); err != nil {
				printErr(err, "Could not write session ID")
				return
			}
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
		if configDir == "" {
			return
		}
		sessionID, err := readSessionID(configDir)
		if err != nil {
			printErr(err, "Could not read session ID")
			return
		}
		err = vespa.Activate(sessionID, vespa.Deployment{
			ApplicationPackage: pkg,
			TargetType:         getTargetType(),
			TargetURL:          deployTarget(),
		})
		if err == nil {
			printSuccess("Activated ", color.Cyan(pkg.Path), " with session ", sessionID)
		} else {
			printErr(nil, err.Error())
		}
	},
}

func writeSessionID(appConfigDir string, sessionID int64) error {
	if err := os.MkdirAll(appConfigDir, 0755); err != nil {
		return err
	}
	return os.WriteFile(sessionIDFile(appConfigDir), []byte(fmt.Sprintf("%d\n", sessionID)), 0600)
}

func readSessionID(appConfigDir string) (int64, error) {
	b, err := os.ReadFile(sessionIDFile(appConfigDir))
	if err != nil {
		return 0, err
	}
	return strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
}

func sessionIDFile(appConfigDir string) string { return filepath.Join(appConfigDir, "session_id") }

func readAPIKey(configDir, tenant string) []byte {
	apiKeyPath := filepath.Join(configDir, tenant+".api-key.pem")
	key, err := os.ReadFile(apiKeyPath)
	if err != nil {
		printErr(err, "Could not read API key from ", color.Cyan(apiKeyPath))
		return nil
	}
	return key
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	return "."
}
