// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy command
// Author: bratseth

package cmd

import (
	"log"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/vespa"
)

const (
	zoneFlag        = "zone"
	applicationFlag = "application"
)

var (
	zoneArg        string
	applicationArg string
)

func init() {
	rootCmd.AddCommand(deployCmd)
	rootCmd.AddCommand(prepareCmd)
	rootCmd.AddCommand(activateCmd)
	addTargetFlag(deployCmd)
	addTargetFlag(prepareCmd)
	addTargetFlag(activateCmd)

	deployCmd.PersistentFlags().StringVarP(&zoneArg, zoneFlag, "z", "dev.aws-us-east-1c", "The zone to use for deployment")
	deployCmd.PersistentFlags().StringVarP(&applicationArg, applicationFlag, "a", "", "The application name to use for deployment")
}

var deployCmd = &cobra.Command{
	Use:   "deploy",
	Short: "Deploys (prepares and activates) an application package",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		d := vespa.Deployment{
			ApplicationSource: applicationSource(args),
			TargetType:        targetArg,
			TargetURL:         deployTarget(),
		}
		if d.IsCloud() {
			var err error
			d.Zone, err = vespa.ZoneFromString(zoneArg)
			if err != nil {
				log.Fatal(err)
			}
			d.Application, err = vespa.ApplicationFromString(applicationArg)
			if err != nil {
				log.Fatal(err)
			}

			d.KeyPair, err = loadApplicationKeyPair(applicationArg)
			if err != nil {
				log.Fatal(err)
			}
		}
		resolvedSrc, err := vespa.Deploy(d)
		if err == nil {
			log.Printf("Deployed %s successfully", color.Cyan(resolvedSrc))
		} else {
			log.Print(err)
		}
	},
}

var prepareCmd = &cobra.Command{
	Use:   "prepare",
	Short: "Prepares an application package for activation",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		resolvedSrc, err := vespa.Prepare(vespa.Deployment{ApplicationSource: applicationSource(args)})
		if err == nil {
			log.Printf("Prepared %s successfully", color.Cyan(resolvedSrc))
		} else {
			log.Print(color.Red(err))
		}
	},
}

var activateCmd = &cobra.Command{
	Use:   "activate",
	Short: "Activates (deploys) the previously prepared application package",
	Long:  `TODO`,
	Run: func(cmd *cobra.Command, args []string) {
		resolvedSrc, err := vespa.Activate(vespa.Deployment{ApplicationSource: applicationSource(args)})
		if err == nil {
			log.Printf("Activated %s successfully", color.Cyan(resolvedSrc))
		} else {
			log.Print(color.Red(err))
		}
	},
}

func loadApplicationKeyPair(application string) (vespa.PemKeyPair, error) {
	configDir, err := configDir(application)
	if err != nil {
		return vespa.PemKeyPair{}, err
	}
	certificateFile := filepath.Join(configDir, "data-plane-public-cert.pem")
	privateKeyFile := filepath.Join(configDir, "data-plane-private-key.pem")
	return vespa.LoadKeyPair(privateKeyFile, certificateFile)
}

func applicationSource(args []string) string {
	if len(args) > 0 {
		return args[0]
	}
	wd, err := os.Getwd()
	if err != nil {
		log.Fatalf("Could not determine working directory: %s", err)
	}
	return wd
}
