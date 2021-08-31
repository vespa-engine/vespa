// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"os"
	"path/filepath"

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
	Use:   "deploy",
	Short: "Deploy (prepare and activate) an application package",
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		d := vespa.Deployment{
			ApplicationSource: applicationSource(args),
			TargetType:        getTargetType(),
			TargetURL:         deployTarget(),
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
			configDir := configDir(d.Application.String())
			if configDir == "" {
				return
			}
			d.APIKey = loadApiKey(configDir, getApplication())
			if d.APIKey == nil {
				printErrHint(err, "Deployment to cloud requires an API key. Try 'vespa api-key'")
				return
			}
		}
		resolvedSrc, err := vespa.Deploy(d)
		if err == nil {
			printSuccess("Deployed ", color.Cyan(resolvedSrc))
		} else {
			printErr(nil, err.Error())
		}
	},
}

var prepareCmd = &cobra.Command{
	Use:   "prepare",
	Short: "Prepare an application package for activation",
	Args:  cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		resolvedSrc, err := vespa.Prepare(vespa.Deployment{ApplicationSource: applicationSource(args)})
		if err == nil {
			printSuccess("Prepared ", color.Cyan(resolvedSrc))
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
		resolvedSrc, err := vespa.Activate(vespa.Deployment{ApplicationSource: applicationSource(args)})
		if err == nil {
			printSuccess("Activated ", color.Cyan(resolvedSrc))
		} else {
			printErr(nil, err.Error())
		}
	},
}

func loadApiKey(configDir, application string) []byte {
	apiKeyPath := filepath.Join(configDir, "api-key.pem")
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
